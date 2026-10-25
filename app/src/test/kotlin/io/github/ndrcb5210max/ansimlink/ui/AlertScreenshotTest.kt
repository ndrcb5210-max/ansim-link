package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerRules
import io.github.ndrcb5210max.ansimlink.service.TestAlertPublisher
import io.github.ndrcb5210max.ansimlink.ui.alert.AlertLogScreen
import io.github.ndrcb5210max.ansimlink.ui.alert.AlertLogUiState
import io.github.ndrcb5210max.ansimlink.ui.alert.DetectionSetupScreen
import io.github.ndrcb5210max.ansimlink.ui.alert.DetectionUiState
import io.github.ndrcb5210max.ansimlink.ui.alert.ObservedPackagesScreen
import io.github.ndrcb5210max.ansimlink.ui.alert.ObservedPackagesUiState
import io.github.ndrcb5210max.ansimlink.ui.alert.TestAlertScreen
import io.github.ndrcb5210max.ansimlink.ui.alert.TriggerRulesScreen
import io.github.ndrcb5210max.ansimlink.ui.alert.TriggerRulesUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 3단계의 긴 화면 스크린샷 → docs/screenshots/03/
 *
 * 하단 탭 없이 화면 본문만 찍는다. 기본 높이가 아닌 화면에서 Scaffold 를 같이 그리면
 * 하단 탭이 위쪽에 한 번 더 그려지기 때문이다(2단계 '막힌 점' 5번).
 * 하단 탭이 같이 보이는 그림은 [AlertTabScreenshotTest] 가 기본 크기로 찍는다.
 *
 * 화면 높이는 클래스마다 `@Config` 로 고정한다. 테스트 안에서 바꾸면 결과가 깨지므로
 * 내용 길이가 비슷한 화면끼리 클래스를 나눈다(짧은 화면은 [AlertLogScreenshotTest]).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h1500dp-xhdpi")
class AlertScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `감지 설정 안내 - 권한을 켜기 전`() {
        composeTestRule.capture("03/01-detection-setup.png") {
            DetectionSetupScreen(
                state = DetectionUiState(),
                onOpenNotificationAccessSettings = {},
                onOpenAppDetails = {},
                onRequestNotificationPermission = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `감지 설정 안내 - 켜진 뒤`() {
        composeTestRule.capture("03/02-detection-setup-on.png") {
            DetectionSetupScreen(
                state = DetectionUiState(
                    accessGranted = true,
                    listenerConnected = true,
                    notificationsEnabled = true,
                ),
                onOpenNotificationAccessSettings = {},
                onOpenAppDetails = {},
                onRequestNotificationPermission = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `발동 규칙`() {
        // 스위치가 켜진 카드와 꺼진 카드, 칩이 켜진 상태와 꺼진 상태를 한 그림에 담으려고
        // 안전안내문자를 켜고 유형 세 개를 빼 두었다.
        composeTestRule.capture("03/03-trigger-rules.png") {
            TriggerRulesScreen(
                state = TriggerRulesUiState(
                    rules = TriggerRules.default
                        .withSeverityEnabled(AlertSeverity.PublicSafety, true)
                        .withType(AlertSeverity.PublicSafety, DisasterType.HeatWave, on = false)
                        .withType(AlertSeverity.PublicSafety, DisasterType.ColdWave, on = false)
                        .withType(AlertSeverity.PublicSafety, DisasterType.Other, on = false),
                ),
                onSeverityEnabled = { _, _ -> },
                onTypeEnabled = { _, _, _ -> },
                onSelectAllTypes = { _, _ -> },
                onReset = {},
                onBack = {},
            )
        }
    }

}

/** 3단계의 짧은 화면 스크린샷. 위 클래스와 높이만 다르다. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h1150dp-xhdpi")
class AlertLogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `발동 기록`() {
        composeTestRule.capture("03/04-alert-log.png") {
            AlertLogScreen(state = sampleAlertLog, onClear = {}, onBack = {})
        }
    }

    @Test
    fun `발동 기록 - 비어 있을 때`() {
        composeTestRule.capture("03/05-alert-log-empty.png") {
            AlertLogScreen(
                state = AlertLogUiState(loading = false),
                onClear = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `테스트 재난문자 보내기 - 디버그 전용`() {
        composeTestRule.capture("03/06-debug-test-alert.png") {
            TestAlertScreen(
                samples = TestAlertPublisher.samples,
                resultMessage = null,
                onSend = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `받은 알림 패키지 기록 - 디버그 전용`() {
        composeTestRule.capture("03/07-debug-observed-packages.png") {
            ObservedPackagesScreen(
                state = ObservedPackagesUiState(loading = false, items = sampleObservedPackages),
                onClear = {},
                onBack = {},
            )
        }
    }
}
