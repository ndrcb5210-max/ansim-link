package io.github.ndrcb5210max.ansimlink.ui.auth

import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsent
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsentState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가입 버튼이 언제 켜지는지.
 *
 * 화면 자체는 이 상태만 보고 버튼을 켜고 끄므로, 여기서 검사하면 화면 동작도 같이 보장된다.
 */
class AuthUiStateTest {

    private val allConsents = SignUpConsentState(accepted = SignUpConsent.entries.toSet())

    private fun signUp(
        consents: SignUpConsentState = allConsents,
        email: String = "hong@example.com",
        password: String = "abcd1234",
        confirmation: String = "abcd1234",
        displayName: String = "민수",
    ) = AuthUiState(
        mode = AuthMode.SignUp,
        email = email,
        password = password,
        passwordConfirmation = confirmation,
        displayName = displayName,
        consents = consents,
    )

    @Test
    fun `모두 채우고 모두 동의하면 가입할 수 있다`() {
        assertTrue(signUp().canSubmit)
    }

    @Test
    fun `필수 동의가 하나라도 빠지면 가입 버튼이 꺼진다`() {
        SignUpConsent.entries.forEach { skipped ->
            val state = signUp(
                consents = SignUpConsentState(SignUpConsent.entries.toSet() - skipped),
            )
            assertFalse("$skipped 없이 가입이 가능하다", state.canSubmit)
        }
    }

    @Test
    fun `동의를 하나도 하지 않으면 가입 버튼이 꺼진다`() {
        assertFalse(signUp(consents = SignUpConsentState()).canSubmit)
    }

    @Test
    fun `이름이 비면 가입 버튼이 꺼진다`() {
        assertFalse(signUp(displayName = "").canSubmit)
        assertFalse(signUp(displayName = "   ").canSubmit)
    }

    @Test
    fun `비밀번호 확인이 다르면 가입 버튼이 꺼진다`() {
        assertFalse(signUp(confirmation = "abcd12345").canSubmit)
    }

    @Test
    fun `이메일 모양이 틀리면 가입 버튼이 꺼진다`() {
        assertFalse(signUp(email = "hong").canSubmit)
    }

    @Test
    fun `서버 설정이 없으면 가입 버튼이 꺼진다`() {
        assertFalse(signUp().copy(serverReady = false).canSubmit)
    }

    @Test
    fun `처리 중에는 가입 버튼이 꺼진다`() {
        assertFalse(signUp().copy(busy = true).canSubmit)
    }

    @Test
    fun `로그인은 동의 없이도 할 수 있다`() {
        val state = AuthUiState(
            mode = AuthMode.SignIn,
            email = "hong@example.com",
            password = "old",
        )
        assertTrue(state.canSubmit)
    }

    @Test
    fun `로그인도 비밀번호가 비면 버튼이 꺼진다`() {
        val state = AuthUiState(mode = AuthMode.SignIn, email = "hong@example.com")
        assertFalse(state.canSubmit)
    }

    @Test
    fun `Google 로그인도 동의를 모두 받아야 누를 수 있다`() {
        // 누르기 전에는 새 계정인지 알 수 없다. 동의 없이 계정이 만들어지면 안 된다.
        val withoutConsents = AuthUiState(
            mode = AuthMode.SignIn,
            googleSignInAvailable = true,
        )
        assertFalse(withoutConsents.canUseGoogle)

        val withConsents = withoutConsents.copy(consents = allConsents)
        assertTrue(withConsents.canUseGoogle)
    }

    @Test
    fun `Web client ID 가 없으면 Google 버튼을 쓸 수 없다`() {
        val state = AuthUiState(
            mode = AuthMode.SignIn,
            googleSignInAvailable = false,
            consents = allConsents,
        )
        assertFalse(state.canUseGoogle)
    }

    @Test
    fun `Google 버튼이 있으면 로그인 화면에서도 동의 묶음을 보여 준다`() {
        assertTrue(AuthUiState(mode = AuthMode.SignIn, googleSignInAvailable = true).showConsents)
        assertFalse(AuthUiState(mode = AuthMode.SignIn, googleSignInAvailable = false).showConsents)
        assertTrue(AuthUiState(mode = AuthMode.SignUp).showConsents)
    }
}
