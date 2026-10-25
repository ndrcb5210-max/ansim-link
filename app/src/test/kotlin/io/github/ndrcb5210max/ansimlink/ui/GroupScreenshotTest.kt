package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ndrcb5210max.ansimlink.data.model.InviteLookup
import io.github.ndrcb5210max.ansimlink.ui.group.CreateGroupScreen
import io.github.ndrcb5210max.ansimlink.ui.group.CreateGroupUiState
import io.github.ndrcb5210max.ansimlink.ui.group.GroupActions
import io.github.ndrcb5210max.ansimlink.ui.group.GroupScreen
import io.github.ndrcb5210max.ansimlink.ui.group.GroupUiState
import io.github.ndrcb5210max.ansimlink.ui.group.JoinGroupScreen
import io.github.ndrcb5210max.ansimlink.ui.group.JoinGroupUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 그룹 화면 스크린샷 → docs/screenshots/02/ */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h1150dp-xhdpi")
class GroupScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `그룹 목록 - 구성원과 공유 상태가 서로 보인다`() {
        // 하단 탭은 1단계 스크린샷(01/02-group.png)에서 보여 준다. 여기서는 화면만 찍는다.
        // 기본 높이가 아닌 화면에서 Scaffold 를 같이 그리면 하단 탭이 위쪽에 한 번 더
        // 그려지는 문제가 있어서 긴 화면은 화면 본문만 찍는다.
        composeTestRule.capture("02/05-group-members.png") {
            GroupScreen(
                state = GroupUiState(loading = false, myUid = "me", groups = listOf(sampleGroup)),
                actions = GroupActions(),
            )
        }
    }

    @Test
    fun `그룹 만들기`() {
        composeTestRule.capture("02/06-create-group.png") {
            CreateGroupScreen(
                state = CreateGroupUiState(name = "우리 가족"),
                onNameChange = {},
                onSubmit = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `참여 - 위치 제공 동의 화면`() {
        composeTestRule.capture("02/07-join-consent.png") {
            JoinGroupScreen(
                state = JoinGroupUiState(
                    code = "KH3M7P",
                    invite = InviteLookup(
                        code = "KH3M7P",
                        groupId = "g1",
                        groupName = "우리 가족",
                        memberNames = listOf("민수", "지은"),
                        expiresAtMillis = 0,
                    ),
                ),
                onCodeChange = {},
                onLookup = {},
                onConsentChange = {},
                onJoin = {},
                onBack = {},
            )
        }
    }
}
