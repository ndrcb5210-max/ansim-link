package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ndrcb5210max.ansimlink.ui.group.GroupActions
import io.github.ndrcb5210max.ansimlink.ui.group.GroupScreen
import io.github.ndrcb5210max.ansimlink.ui.group.GroupUiState
import io.github.ndrcb5210max.ansimlink.ui.home.HomeScreen
import io.github.ndrcb5210max.ansimlink.ui.navigation.AnsimTab
import io.github.ndrcb5210max.ansimlink.ui.settings.SettingsScreen
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 하단 탭 세 화면의 스크린샷을 docs/screenshots/01/ 에 저장한다(블로그용).
 *
 * 저장 위치의 기준 폴더는 app/build.gradle.kts 의 `roborazzi { outputDir }` 이고,
 * 기록 여부는 gradle.properties 의 `roborazzi.test.record=true` 로 켜 둔다.
 * 그래서 `./gradlew testDebugUnitTest` 만 돌려도 PNG 가 갱신된다.
 *
 * 2단계에서 그룹·설정 화면 내용이 바뀌었으므로 01 폴더의 PNG 도 같이 갱신된다.
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
        captureTab(AnsimTab.Home, "01/01-home.png") { HomeScreen() }
    }

    @Test
    fun groupScreen() {
        captureTab(AnsimTab.Group, "01/02-group.png") {
            GroupScreen(
                state = GroupUiState(loading = false, myUid = "me"),
                actions = GroupActions(),
            )
        }
    }

    @Test
    fun settingsScreen() {
        captureTab(AnsimTab.Settings, "01/03-settings.png") { SettingsScreen() }
    }

    /** 하단 탭이 달린 상태로 화면 하나를 찍는다. 실제 앱과 같은 틀을 쓴다. */
    private fun captureTab(tab: AnsimTab, fileName: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            AnsimLinkTheme(darkTheme = false) {
                AnsimTabScaffold(selectedTab = tab, onSelectTab = {}) { innerPadding ->
                    Box(Modifier.padding(innerPadding)) { content() }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(fileName)
    }
}
