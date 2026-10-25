package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ndrcb5210max.ansimlink.data.account.DeletionPlan
import io.github.ndrcb5210max.ansimlink.ui.settings.AccountScreen
import io.github.ndrcb5210max.ansimlink.ui.settings.AccountUiState
import io.github.ndrcb5210max.ansimlink.ui.settings.DeleteAccountScreen
import io.github.ndrcb5210max.ansimlink.ui.settings.DeleteAccountUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 계정·계정 삭제 화면 스크린샷 → docs/screenshots/02/
 *
 * 계정 삭제 화면은 경고·구독 안내·본인 확인·그룹 처리 선택이 모두 들어가서 제일 길다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h1400dp-xhdpi")
class AccountScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `설정 - 계정`() {
        composeTestRule.capture("02/08-account.png") {
            AccountScreen(
                state = AccountUiState(user = sampleUser),
                onResendVerification = {},
                onSignOut = {},
                onDeleteAccount = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `설정 - 계정 삭제`() {
        composeTestRule.capture("02/09-delete-account.png") {
            DeleteAccountScreen(
                state = DeleteAccountUiState(
                    user = sampleUser.copy(emailVerified = true),
                    plan = DeletionPlan(
                        ownedGroupsWithOthers = listOf(sampleGroup),
                        ownedGroupsAlone = emptyList(),
                        joinedGroups = emptyList(),
                    ),
                ),
                onPasswordChange = {},
                onReauthenticate = {},
                onChoiceChange = { _, _ -> },
                onOpenSubscriptions = {},
                onDelete = {},
                onBack = {},
            )
        }
    }
}
