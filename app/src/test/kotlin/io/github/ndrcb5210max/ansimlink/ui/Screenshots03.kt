package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertConfidence
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerReason
import io.github.ndrcb5210max.ansimlink.ui.alert.AlertLogItem
import io.github.ndrcb5210max.ansimlink.ui.alert.AlertLogUiState
import io.github.ndrcb5210max.ansimlink.ui.alert.ObservedPackageItem
import io.github.ndrcb5210max.ansimlink.ui.navigation.AnsimTab
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

// 3단계 스크린샷 테스트가 같이 쓰는 것들.
//
// 시각은 문자열로 미리 넣는다. 화면이 시계를 모르므로(ViewModel 이 만들어 준다)
// 그림이 기기 시간대나 찍는 시각에 따라 달라지지 않는다.

/** 하단 탭이 달린 상태로 화면 하나를 찍는다. 기본 화면 크기에서만 쓴다. */
internal fun ComposeContentTestRule.captureWithTabs(
    tab: AnsimTab,
    fileName: String,
    content: @Composable () -> Unit,
) {
    setContent {
        AnsimLinkTheme(darkTheme = false) {
            AnsimTabScaffold(selectedTab = tab, onSelectTab = {}) { innerPadding ->
                Box(Modifier.padding(innerPadding)) { content() }
            }
        }
    }
    onRoot().captureRoboImage(fileName)
}

/**
 * 발동 기록 보기.
 *
 * 발동한 것, 종류가 꺼져 있어 발동하지 않은 것, 같은 내용이라 건너뛴 것,
 * 묶음 알림이라 종류를 알 수 없는 것을 한 화면에 담았다.
 * 본문은 모두 실제 문장 형태를 참고해 지어낸 예시다.
 */
internal val sampleAlertLog = AlertLogUiState(
    loading = false,
    items = listOf(
        AlertLogItem(
            id = 4,
            timeText = "10월 25일 05:12",
            severity = AlertSeverity.Emergency,
            type = DisasterType.Earthquake,
            categoryName = "지진",
            confidence = AlertConfidence.High,
            bodyPreview = "[기상청] 10-25 05:12 경북 ○○시 남남서쪽 19km 지역 규모 4.2 지진발생/낙하…",
            triggered = true,
            reason = TriggerReason.Triggered,
        ),
        AlertLogItem(
            id = 3,
            timeText = "10월 25일 05:12",
            severity = AlertSeverity.Emergency,
            type = DisasterType.Earthquake,
            categoryName = "지진",
            confidence = AlertConfidence.High,
            bodyPreview = "[Korea Meteorological Administration] M4.2 earthquake near ○○-si…",
            triggered = false,
            reason = TriggerReason.DuplicateSameKind,
        ),
        AlertLogItem(
            id = 2,
            timeText = "10월 24일 14:03",
            severity = AlertSeverity.PublicSafety,
            type = DisasterType.HeatWave,
            categoryName = "폭염",
            confidence = AlertConfidence.High,
            bodyPreview = "[○○시] 오늘 폭염경보 발효. 낮 시간 야외활동을 줄이고 물을 자주 드세요",
            triggered = false,
            reason = TriggerReason.SeverityDisabled,
        ),
        AlertLogItem(
            id = 1,
            timeText = "10월 24일 09:41",
            severity = AlertSeverity.Unknown,
            type = DisasterType.Unknown,
            categoryName = null,
            confidence = AlertConfidence.Low,
            bodyPreview = "",
            triggered = false,
            reason = TriggerReason.SeverityUnknown,
        ),
    ),
)

/**
 * 디버그 전용 '받은 알림 패키지 기록' 보기.
 *
 * 삼성 기기의 재난문자 앱 패키지 이름을 아직 모르므로, 이 화면으로 찾는다.
 * 아래 패키지 이름은 실기기에서 흔히 보이는 이름을 적은 예시다.
 */
internal val sampleObservedPackages = listOf(
    ObservedPackageItem(
        packageName = "com.samsung.android.messaging",
        count = 12,
        lastSeenText = "10월 25일 09:41",
        known = false,
    ),
    ObservedPackageItem(
        packageName = "com.kakao.talk",
        count = 7,
        lastSeenText = "10월 25일 08:20",
        known = false,
    ),
    ObservedPackageItem(
        packageName = "com.android.cellbroadcastreceiver",
        count = 2,
        lastSeenText = "10월 25일 05:12",
        known = true,
    ),
)
