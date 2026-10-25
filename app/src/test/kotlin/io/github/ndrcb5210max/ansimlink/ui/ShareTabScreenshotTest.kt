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
 * 하단 탭이 같이 보이는 4단계 그림. 1단계 스크린샷과 같은 기본 화면 크기로 찍는다.
 *
 * 홈 화면 세 가지를 찍는 이유: 이 카드가 **앱 안 배너**다(CLAUDE.md 원칙 2).
 * 공유 중일 때 상시 알림과 같은 내용을 보여 주는지, 못 시작할 때 이유를 버튼 위에
 * 적는지가 이 단계의 핵심이라 그림으로 남긴다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
class ShareTabScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `홈 - 공유 중`() {
        composeTestRule.captureWithTabs(AnsimTab.Home, "04/09-home-sharing.png") {
            HomeScreen(detecting = true, share = sharingNow)
        }
    }

    @Test
    fun `홈 - 공유 시작 전`() {
        composeTestRule.captureWithTabs(AnsimTab.Home, "04/10-home-ready.png") {
            HomeScreen(detecting = true, share = readyToShare)
        }
    }

    @Test
    fun `홈 - 일시중지라 시작할 수 없을 때`() {
        composeTestRule.captureWithTabs(AnsimTab.Home, "04/11-home-blocked.png") {
            HomeScreen(detecting = true, share = blockedByPause)
        }
    }

    @Test
    fun `설정 - 4단계 항목까지`() {
        composeTestRule.captureWithTabs(AnsimTab.Settings, "04/12-settings.png") {
            SettingsScreen()
        }
    }
}
