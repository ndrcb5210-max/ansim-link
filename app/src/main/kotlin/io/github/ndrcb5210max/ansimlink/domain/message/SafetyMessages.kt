package io.github.ndrcb5210max.ansimlink.domain.message

import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.group.Plan

/**
 * 안심 메시지 모음.
 *
 * 무료는 공통 메시지 하나, 프리미엄은 재난 유형별 메시지까지 쓸 수 있다
 * (CLAUDE.md 3장 결제). 저장은 [io.github.ndrcb5210max.ansimlink.data.message.SafetyMessageStore].
 *
 * @param common 공통 메시지. 비어 있으면 [SafetyMessages.DEFAULT] 를 쓴다.
 * @param byType 재난 유형별 메시지. 적지 않은 유형은 여기 없다(빈 문자열도 '안 적었다'로 본다).
 */
data class SafetyMessageSet(
    val common: String = SafetyMessages.DEFAULT,
    val byType: Map<DisasterType, String> = emptyMap(),
)

/**
 * 발동했을 때 어떤 문구를 보낼지 정한다.
 *
 * 순수 함수다. 저장소도 시계도 요금제 검증도 하지 않는다. 요금제는 인자로 받는다
 * (구독 상태는 서버가 검증한 값을 읽어 와야 한다 — CLAUDE.md 3장).
 */
object SafetyMessages {

    /** 기본 문구. 지시문에 적힌 그대로다. */
    const val DEFAULT = "재난문자 받았어. 나는 괜찮아. 앱에서 내 위치를 확인해 줘."

    /**
     * 메시지 글자 수 상한.
     *
     * FCM 알림으로 보여 주는 한 줄이고, Firestore 문서 하나에 들어간다.
     * 길게 쓸 이유가 없고 알림에서 잘려 보이면 쓸모가 없다.
     */
    const val MAX_LENGTH = 200

    /**
     * 이 발동에 쓸 문구를 고른다.
     *
     * 고르는 순서
     * 1. 프리미엄이고 이 유형에 적어 둔 문구가 있으면 그것.
     * 2. 공통 메시지.
     * 3. 공통 메시지도 비어 있으면 [DEFAULT].
     *
     * '유형 미상'([DisasterType.Unknown])은 발동 규칙과 같은 방식으로 '기타' 칸의 문구를
     * 본다([DisasterType.ruleType]). 규칙과 메시지가 서로 다른 칸을 보면 사용자가
     * '기타를 켜서 발동했는데 왜 공통 문구가 갔나'를 이해할 수 없다.
     *
     * 무료 요금제에서는 [SafetyMessageSet.byType] 에 값이 남아 있어도 쓰지 않는다.
     * 구독을 해지해도 저장해 둔 유형별 문구는 지우지 않으므로(다시 구독하면 되살아난다),
     * 여기서 요금제를 꼭 봐야 한다.
     */
    fun select(
        messages: SafetyMessageSet,
        type: DisasterType,
        plan: Plan,
    ): String {
        if (plan == Plan.Premium) {
            val perType = messages.byType[type.ruleType]?.trim()
            if (!perType.isNullOrEmpty()) return perType.take(MAX_LENGTH)
        }
        val common = messages.common.trim()
        return (if (common.isEmpty()) DEFAULT else common).take(MAX_LENGTH)
    }

    /**
     * 사용자가 적은 문구를 저장할 모양으로 다듬는다.
     *
     * 빈 문자열이면 '안 적었다'로 두고(저장소에서 지운다) 기본값이 쓰이게 한다.
     * 줄바꿈은 한 칸으로 줄인다. 알림은 어차피 한 줄로 보여 주고, 줄바꿈이 섞이면
     * 알림과 앱 화면에서 다르게 보인다.
     */
    fun normalize(raw: String): String =
        raw.replace(whitespaceRun, " ").trim().take(MAX_LENGTH)

    private val whitespaceRun = Regex("\\s+")
}
