package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ndrcb5210max.ansimlink.ui.home.HomeScreen
import io.github.ndrcb5210max.ansimlink.ui.navigation.AnsimTab
import io.github.ndrcb5210max.ansimlink.ui.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 하단 탭이 같이 보이는 3단계 그림. 1단계 스크린샷과 같은 기본 화면 크기로 찍는다.
 *
 * 홈 화면은 '재난문자 감지 — 켜짐' 상태를, 설정 화면은 디버그 항목까지 보여 준다.
 * (1단계 스크린샷은 감지가 꺼진 홈 화면과 디버그 항목 없는 설정 화면을 찍는다.)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
class AlertTabScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `홈 - 감지가 켜진 모습`() {
        composeTestRule.captureWithTabs(AnsimTab.Home, "03/08-home-detection-on.png") {
            HomeScreen(detecting = true)
        }
    }

    @Test
    fun `설정 - 디버그 항목까지`() {
        composeTestRule.captureWithTabs(AnsimTab.Settings, "03/09-settings-debug.png") {
            SettingsScreen(showDebugEntries = true)
        }
    }
}
