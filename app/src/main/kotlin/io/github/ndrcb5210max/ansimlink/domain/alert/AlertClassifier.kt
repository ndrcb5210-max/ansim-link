package io.github.ndrcb5210max.ansimlink.domain.alert

/**
 * 분류기에 넣는 알림 한 건. 안드로이드 타입을 쓰지 않아 순수 Kotlin으로 테스트할 수 있다.
 *
 * @param packageName 알림을 띄운 앱. 허용 목록([AlertPackages]) 검사는 분류기 바깥에서 먼저 한다.
 * @param title 알림 제목. 문자 종류를 여기서 정한다.
 * @param body 알림 본문. 재난 유형을 여기서 정한다. 묶음 알림이면 비어 있다.
 * @param channelId 알림 채널 ID. 지금은 기록만 하고 판단에 쓰지 않는다([AlertClassification.channelId]).
 */
data class AlertNotification(
    val packageName: String,
    val title: String?,
    val body: String?,
    val channelId: String? = null,
)

/**
 * 분류 결과를 얼마나 믿을 수 있는지.
 *
 * 발동 여부를 바꾸지는 않는다. 발동 기록 화면에 그대로 보여 주어,
 * 감지가 어긋났을 때 사용자가 '제목을 못 읽은 것인지, 유형을 못 가린 것인지' 알 수 있게 한다.
 */
enum class AlertConfidence(val key: String) {
    /** 제목이 알려진 재난문자 제목과 맞고, 본문에서 재난 유형까지 가렸다. */
    High("high"),

    /** 제목은 맞지만 본문에서 유형을 가리지 못했다(본문이 없거나 내용이 가려졌다). */
    Medium("medium"),

    /** 제목이 맞지 않는다. 묶음 알림이거나 재난문자가 아닌 알림이다. */
    Low("low"),
    ;

    companion object {
        fun fromKey(key: String): AlertConfidence? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 알림 한 건을 가린 결과.
 *
 * @param severity 문자 종류.
 * @param type 설정 화면용 12개 묶음 중 하나(또는 유형 미상).
 * @param category 재난구분 35종 중 맞은 것. 기록용이고 아무것도 맞지 않으면 null.
 * @param confidence 신뢰도.
 * @param bundleSummary '새 경보', '읽지 않은 경보 3개' 처럼 본문 없는 묶음 알림인지.
 * @param bodyPreview 본문 앞 60자. 저장하는 것은 이만큼뿐이다.
 * @param bodyFingerprint 같은 문자인지 가릴 때 쓰는 본문 해시.
 * @param channelId 알림 채널 ID(기록용).
 */
data class AlertClassification(
    val severity: AlertSeverity,
    val type: DisasterType,
    val category: DisasterCategory?,
    val confidence: AlertConfidence,
    val bundleSummary: Boolean,
    val bodyPreview: String,
    val bodyFingerprint: String,
    val channelId: String?,
)

/**
 * 재난문자 알림을 '문자 종류 × 재난 유형'으로 가린다.
 *
 * 순수 함수다. 시계도, 저장소도, 안드로이드도 건드리지 않는다.
 * 허용 목록 검사([AlertPackages])와 발동 판단([TriggerEngine])은 따로 둔다.
 */
object AlertClassifier {

    // 본문 없이 '읽지 않은 경보가 있다'만 알려 주는 묶음 알림의 제목.
    // 재난문자 앱은 읽지 않은 경보가 여러 건이면 개별 알림을 접고 이런 알림 하나만 띄운다.
    // 이때는 종류도 유형도 알 수 없다.
    // 'Emergency alerts' 처럼 종류 이름이 들어간 제목은 넣지 않는다.
    // AlertSeverity.fromTitle 이 'emergencyalert' 로 먼저 맞혀 버려서 묶음 알림인지 아닌지가
    // 어긋나기 때문이다.
    private val bundleTitles = listOf(
        "새경보",
        "읽지않은경보",
        "newalerts",
        "unreadalerts",
    )

    fun classify(notification: AlertNotification): AlertClassification {
        val squeezedTitle = AlertText.squeeze(notification.title)
        val squeezedBody = AlertText.squeeze(notification.body)

        val severity = AlertSeverity.fromTitle(notification.title)
        val bundleSummary = squeezedBody.isEmpty() && isBundleTitle(squeezedTitle)

        val category = if (squeezedBody.isEmpty()) null else DisasterCategory.best(squeezedBody)
        val type = when {
            // 본문이 없다(묶음 알림이거나 Android 15 에서 내용이 가려진 경우) → 유형 미상.
            squeezedBody.isEmpty() -> DisasterType.Unknown
            // 본문은 있는데 아는 낱말이 하나도 없다 → '기타'. 실종경보문자가 여기로 온다.
            category == null -> DisasterType.Other
            else -> category.group
        }

        val confidence = when {
            severity == AlertSeverity.Unknown -> AlertConfidence.Low
            type == DisasterType.Unknown -> AlertConfidence.Medium
            else -> AlertConfidence.High
        }

        return AlertClassification(
            severity = severity,
            type = type,
            category = category,
            confidence = confidence,
            bundleSummary = bundleSummary,
            bodyPreview = AlertText.preview(notification.body),
            bodyFingerprint = AlertText.fingerprint(notification.body),
            channelId = notification.channelId,
        )
    }

    private fun isBundleTitle(squeezedTitle: String): Boolean {
        val lowercase = squeezedTitle.lowercase()
        return bundleTitles.any { it in squeezedTitle || it in lowercase }
    }
}
