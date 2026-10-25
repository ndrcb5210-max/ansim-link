package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ndrcb5210max.ansimlink.ui.auth.AuthActions
import io.github.ndrcb5210max.ansimlink.ui.auth.AuthMode
import io.github.ndrcb5210max.ansimlink.ui.auth.AuthScreen
import io.github.ndrcb5210max.ansimlink.ui.auth.AuthUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 로그인·가입 화면 스크린샷 → docs/screenshots/02/
 *
 * 가입 화면은 입력칸과 동의 네 줄이 모두 들어가야 해서 화면을 길게 잡았다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h1320dp-xhdpi")
class AuthScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `가입 - 동의 전에는 가입 버튼이 꺼져 있다`() {
        composeTestRule.capture("02/01-sign-up.png") {
            AuthScreen(
                state = AuthUiState(mode = AuthMode.SignUp, googleSignInAvailable = true),
                actions = AuthActions(),
            )
        }
    }

    @Test
    fun `가입 - 모두 동의하면 버튼이 켜진다`() {
        composeTestRule.capture("02/02-sign-up-consented.png") {
            AuthScreen(
                state = AuthUiState(
                    mode = AuthMode.SignUp,
                    email = "hong@example.com",
                    displayName = "민수",
                    password = "abcd1234",
                    passwordConfirmation = "abcd1234",
                    consents = allSignUpConsents,
                    googleSignInAvailable = true,
                ),
                actions = AuthActions(),
            )
        }
    }

    @Test
    fun `로그인`() {
        composeTestRule.capture("02/03-sign-in.png") {
            AuthScreen(
                state = AuthUiState(
                    mode = AuthMode.SignIn,
                    email = "hong@example.com",
                    googleSignInAvailable = true,
                    consents = allSignUpConsents,
                ),
                actions = AuthActions(),
            )
        }
    }

    @Test
    fun `서버 설정이 없으면 안내를 보여 준다`() {
        composeTestRule.capture("02/04-server-not-configured.png") {
            AuthScreen(
                state = AuthUiState(mode = AuthMode.SignIn, serverReady = false),
                actions = AuthActions(),
            )
        }
    }
}
