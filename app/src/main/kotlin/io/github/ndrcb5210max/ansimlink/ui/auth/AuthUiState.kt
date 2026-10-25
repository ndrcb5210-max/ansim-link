package io.github.ndrcb5210max.ansimlink.ui.auth

import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.domain.auth.EmailPasswordRules
import io.github.ndrcb5210max.ansimlink.domain.auth.EmailProblem
import io.github.ndrcb5210max.ansimlink.domain.auth.PasswordProblem
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsentState

enum class AuthMode { SignIn, SignUp }

/** 로그인·가입 화면이 그릴 내용 전부. 화면은 이 값만 보고 그린다. */
data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val displayName: String = "",
    val consents: SignUpConsentState = SignUpConsentState(),
    val busy: Boolean = false,
    val error: AppError? = null,
    /** 안내 문구(인증 메일 보냄, 재설정 메일 보냄 등). */
    val notice: AuthNotice? = null,
    /** Web client ID 가 있을 때만 Google 버튼을 보여 준다. */
    val googleSignInAvailable: Boolean = false,
    /** Firebase 설정이 없으면 입력은 보여 주되 전송은 막는다. */
    val serverReady: Boolean = true,
    /** 입력을 한 번이라도 건드렸을 때만 빨간 글씨를 보여 준다. */
    val showValidation: Boolean = false,
) {
    val emailProblem: EmailProblem?
        get() = EmailPasswordRules.checkEmail(email)

    val passwordProblem: PasswordProblem?
        get() = if (mode == AuthMode.SignUp) {
            EmailPasswordRules.checkPassword(password)
        } else {
            PasswordProblem.Empty.takeIf { password.isEmpty() }
        }

    val passwordConfirmationProblem: PasswordProblem?
        get() = if (mode == AuthMode.SignUp) {
            EmailPasswordRules.checkPasswordConfirmation(password, passwordConfirmation)
        } else {
            null
        }

    /** 가입 버튼을 켤 수 있는지. 필수 동의가 하나라도 빠지면 꺼진다. */
    val canSubmit: Boolean
        get() = !busy && serverReady && when (mode) {
            AuthMode.SignIn -> EmailPasswordRules.canSignIn(email, password)
            AuthMode.SignUp -> emailProblem == null &&
                passwordProblem == null &&
                passwordConfirmationProblem == null &&
                displayName.isNotBlank() &&
                consents.canSubmit
        }

    /**
     * Google 로그인 버튼을 누를 수 있는지.
     *
     * 누르기 전에는 처음 들어오는 사람인지 알 수 없다. 새 계정이면 동의 없이 계정이 만들어져 버리므로
     * 로그인 화면에서도 동의를 먼저 받는다.
     */
    val canUseGoogle: Boolean
        get() = !busy && serverReady && googleSignInAvailable && consents.canSubmit

    /** 동의 묶음을 보여 줘야 하는지. Google 로그인도 동의가 필요하므로 로그인 화면에서도 보여 준다. */
    val showConsents: Boolean
        get() = mode == AuthMode.SignUp || googleSignInAvailable
}

enum class AuthNotice { VerificationEmailSent, PasswordResetSent }
