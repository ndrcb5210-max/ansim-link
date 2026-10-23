package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ndrcb5210max.ansimlink.ui.navigation.AnsimTab
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 세 화면의 스크린샷을 찍어 docs/screenshots/01/ 에 저장한다(블로그용).
 *
 * 저장 위치는 app/build.gradle.kts 의 `roborazzi { outputDir }` 에서 정하고,
 * 기록 여부는 gradle.properties 의 `roborazzi.test.record=true` 로 켜 둔다.
 * 그래서 `./gradlew testDebugUnitTest` 만 돌려도 PNG가 갱신된다.
 *
 * 화면 크기를 고정해야 결과가 기기·환경에 따라 달라지지 않으므로 qualifiers 를 직접 적는다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen() {
        captureTab(AnsimTab.Home, "01-home.png")
    }

    @Test
    fun groupScreen() {
        captureTab(AnsimTab.Group, "02-group.png")
    }

    @Test
    fun settingsScreen() {
        captureTab(AnsimTab.Settings, "03-settings.png")
    }

    private fun captureTab(tab: AnsimTab, fileName: String) {
        composeTestRule.setContent {
            AnsimLinkTheme(darkTheme = false) {
                AnsimLinkApp(startTab = tab)
            }
        }
        composeTestRule.onRoot().captureRoboImage(fileName)
    }
}
