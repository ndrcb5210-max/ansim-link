package io.github.ndrcb5210max.ansimlink.domain.auth

/** 이메일 입력이 잘못된 이유. 화면에서 그대로 문구로 바꿔 보여 준다. */
enum class EmailProblem { Empty, Malformed }

/** 비밀번호 입력이 잘못된 이유. */
enum class PasswordProblem { Empty, TooShort, Mismatch }

/**
 * 이메일·비밀번호 입력 검사.
 *
 * 안드로이드 `Patterns.EMAIL_ADDRESS` 를 쓰면 단위 테스트에서 에뮬레이터가 필요하므로
 * 순수 Kotlin 으로 둔다. 여기서는 '명백히 잘못된 입력'만 거르고, 최종 판단은 서버(Firebase Auth)가 한다.
 */
object EmailPasswordRules {

    /** Firebase Auth 의 최소 길이는 6자이지만, 조금 더 안전한 쪽으로 8자를 쓴다. */
    const val MIN_PASSWORD_LENGTH = 8

    // 공백 없이 `무언가@무언가.무언가` 모양인지만 본다.
    private val EMAIL_SHAPE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun checkEmail(raw: String): EmailProblem? {
        val email = raw.trim()
        return when {
            email.isEmpty() -> EmailProblem.Empty
            !EMAIL_SHAPE.matches(email) -> EmailProblem.Malformed
            else -> null
        }
    }

    fun checkPassword(raw: String): PasswordProblem? = when {
        raw.isEmpty() -> PasswordProblem.Empty
        raw.length < MIN_PASSWORD_LENGTH -> PasswordProblem.TooShort
        else -> null
    }

    /** 가입 화면의 '비밀번호 확인'. 비밀번호 자체가 잘못됐으면 그 문제를 먼저 돌려준다. */
    fun checkPasswordConfirmation(password: String, confirmation: String): PasswordProblem? =
        checkPassword(password) ?: PasswordProblem.Mismatch.takeIf { password != confirmation }

    /** 로그인 버튼을 켤 수 있는지. 로그인은 동의 체크가 필요 없다. */
    fun canSignIn(email: String, password: String): Boolean =
        checkEmail(email) == null && password.isNotEmpty()
}
