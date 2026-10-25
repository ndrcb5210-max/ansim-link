package io.github.ndrcb5210max.ansimlink.ui.alert

import io.github.ndrcb5210max.ansimlink.domain.alert.AlertConfidence
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerReason
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerRules

/**
 * 감지 설정 화면의 상태.
 *
 * @param accessGranted 알림 접근 권한이 켜져 있는지(시스템 설정).
 * @param listenerConnected 시스템이 우리 리스너를 실제로 묶었는지. 권한을 켠 직후에는
 *   아직 false 일 수 있다.
 * @param notificationsEnabled 알림을 띄울 수 있는지(Android 13+ 런타임 권한).
 * @param settingsNotFound 설정 화면을 열지 못했는지(기기에 그 화면이 없을 때).
 */
data class DetectionUiState(
    val accessGranted: Boolean = false,
    val listenerConnected: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val settingsNotFound: Boolean = false,
) {
    /** 홈 화면과 설정 화면에 '켜짐'으로 보여 줄지. */
    val detecting: Boolean get() = accessGranted && listenerConnected
}

/** 발동 규칙 화면의 상태. */
data class TriggerRulesUiState(
    val rules: TriggerRules = TriggerRules.default,
)

/**
 * 발동 기록 한 줄.
 *
 * [timeText] 를 미리 만들어 둔 이유: 화면은 시계를 모르는 쪽이 좋다. 그러면 스크린샷
 * 테스트에서 시각을 고정할 수 있어 기기·시간대에 따라 그림이 달라지지 않는다.
 */
data class AlertLogItem(
    val id: Long,
    val timeText: String,
    val severity: AlertSeverity,
    val type: DisasterType,
    val categoryName: String?,
    val confidence: AlertConfidence,
    val bodyPreview: String,
    val triggered: Boolean,
    val reason: TriggerReason,
)

data class AlertLogUiState(
    val loading: Boolean = true,
    val items: List<AlertLogItem> = emptyList(),
)

/** 디버그 전용 '받은 알림 패키지 기록' 한 줄. */
data class ObservedPackageItem(
    val packageName: String,
    val count: Long,
    val lastSeenText: String,
    val known: Boolean,
)

data class ObservedPackagesUiState(
    val loading: Boolean = true,
    val items: List<ObservedPackageItem> = emptyList(),
)
