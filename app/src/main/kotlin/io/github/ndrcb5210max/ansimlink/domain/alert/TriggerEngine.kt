package io.github.ndrcb5210max.ansimlink.domain.alert

/**
 * 발동하지 않은(또는 발동한) 이유. 발동 기록 화면에 그대로 보여 준다.
 *
 * 왜 이유를 남기는가: '재난문자를 받았는데 아무 일도 없었다'가 가장 답답한 상황이다.
 * 규칙에서 걸렸는지, 중복이라 건너뛴 것인지 사용자가 직접 볼 수 있어야 한다.
 */
enum class TriggerReason(val key: String) {
    /** 발동했다. */
    Triggered("triggered"),

    /** 문자 종류를 알 수 없다(묶음 알림 등). 무엇인지 모르는 알림으로는 발동하지 않는다. */
    SeverityUnknown("severity_unknown"),

    /** 이 문자 종류의 스위치가 꺼져 있다. */
    SeverityDisabled("severity_disabled"),

    /** 이 재난 유형의 칩이 꺼져 있다. */
    TypeDisabled("type_disabled"),

    /** 같은 본문으로 10분 안에 이미 발동했다. */
    DuplicateBody("duplicate_body"),

    /** 같은 종류·같은 유형으로 10분 안에 이미 발동했다(언어별 사본·재게시). */
    DuplicateSameKind("duplicate_same_kind"),
    ;

    val triggered: Boolean get() = this == Triggered

    companion object {
        fun fromKey(key: String): TriggerReason? = entries.firstOrNull { it.key == key }
    }
}

/** 발동 판단 결과. */
data class TriggerDecision(val reason: TriggerReason) {
    val triggered: Boolean get() = reason.triggered
}

/** 중복 판단에 쓰는 '이미 발동한 기록' 한 건. */
data class PreviousTrigger(
    val bodyFingerprint: String,
    val severity: AlertSeverity,
    val type: DisasterType,
    val triggeredAtMillis: Long,
)

/**
 * 규칙 표와 최근 발동 기록을 보고 '이 문자로 발동할지'를 정한다.
 *
 * 순수 함수다. 시계(`nowMillis`)와 최근 기록(`previous`)을 인자로 받으므로
 * 단위 테스트에서 시간을 마음대로 돌릴 수 있다.
 */
object TriggerEngine {

    /** 중복으로 볼 시간 폭. CLAUDE.md: 같은 문자가 여러 번 와도 10분 안에는 한 번만 발동한다. */
    const val DEDUPE_WINDOW_MILLIS: Long = 10 * 60 * 1000

    /**
     * @param previous 이미 **발동한** 기록만 넘긴다. 발동하지 않은 기록은 중복 판단을 막지 않는다.
     *   [DEDUPE_WINDOW_MILLIS] 보다 오래된 기록이 섞여 있어도 여기서 걸러 낸다.
     */
    fun decide(
        classification: AlertClassification,
        rules: TriggerRules,
        previous: List<PreviousTrigger>,
        nowMillis: Long,
    ): TriggerDecision {
        val severity = classification.severity
        if (severity == AlertSeverity.Unknown) {
            return TriggerDecision(TriggerReason.SeverityUnknown)
        }

        val rule = rules.rule(severity)
        if (!rule.enabled) {
            return TriggerDecision(TriggerReason.SeverityDisabled)
        }
        // '유형 미상'은 '기타' 칸의 설정을 따른다(DisasterType.ruleType).
        if (classification.type.ruleType !in rule.types) {
            return TriggerDecision(TriggerReason.TypeDisabled)
        }

        val recent = previous.filter { nowMillis - it.triggeredAtMillis < DEDUPE_WINDOW_MILLIS }

        // ① 같은 본문. 재난문자 앱이 같은 알림을 다시 게시하는 경우를 막는다.
        //    본문이 없는 알림은 지문이 모두 같으므로 이 검사에서 뺀다.
        if (classification.bodyFingerprint != AlertText.EMPTY_FINGERPRINT &&
            recent.any { it.bodyFingerprint == classification.bodyFingerprint }
        ) {
            return TriggerDecision(TriggerReason.DuplicateBody)
        }

        // ② 같은 종류·같은 유형. 같은 재난문자의 한국어판·영어판은 본문이 달라서 ①로는 안 걸린다.
        //    종류가 올라가면(안전안내 → 긴급) 다른 문자로 보고 다시 발동한다.
        //    대신 10분 안에 들어온 같은 종류·유형의 다른 문자(예: 여진)는 건너뛴다.
        //    지금 단계에서 발동은 '공유할까요?' 알림 한 번이므로 알림을 겹쳐 띄우지 않는 편이 낫다.
        if (recent.any { it.severity == severity && it.type == classification.type }) {
            return TriggerDecision(TriggerReason.DuplicateSameKind)
        }

        return TriggerDecision(TriggerReason.Triggered)
    }
}
