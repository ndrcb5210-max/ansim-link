package io.github.ndrcb5210max.ansimlink.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailPasswordRulesTest {

    @Test
    fun `쓸 만한 이메일은 통과한다`() {
        listOf(
            "hong@example.com",
            "hong.gil.dong@mail.example.co.kr",
            "a+tag@example.io",
        ).forEach { email ->
            assertNull("$email 이 막혔다", EmailPasswordRules.checkEmail(email))
        }
    }

    @Test
    fun `앞뒤 공백은 지우고 본다`() {
        assertNull(EmailPasswordRules.checkEmail("  hong@example.com  "))
    }

    @Test
    fun `빈 이메일과 모양이 틀린 이메일은 막는다`() {
        assertEquals(EmailProblem.Empty, EmailPasswordRules.checkEmail(""))
        assertEquals(EmailProblem.Empty, EmailPasswordRules.checkEmail("   "))

        listOf("hong", "hong@", "@example.com", "hong@example", "hong example@x.com")
            .forEach { email ->
                assertEquals(
                    "$email 이 통과했다",
                    EmailProblem.Malformed,
                    EmailPasswordRules.checkEmail(email),
                )
            }
    }

    @Test
    fun `비밀번호는 여덟 자 이상이어야 한다`() {
        assertEquals(8, EmailPasswordRules.MIN_PASSWORD_LENGTH)
        assertEquals(PasswordProblem.Empty, EmailPasswordRules.checkPassword(""))
        assertEquals(PasswordProblem.TooShort, EmailPasswordRules.checkPassword("1234567"))
        assertNull(EmailPasswordRules.checkPassword("12345678"))
    }

    @Test
    fun `비밀번호 확인이 다르면 막는다`() {
        assertEquals(
            PasswordProblem.Mismatch,
            EmailPasswordRules.checkPasswordConfirmation("abcd1234", "abcd12345"),
        )
        assertNull(EmailPasswordRules.checkPasswordConfirmation("abcd1234", "abcd1234"))
    }

    @Test
    fun `비밀번호 자체가 짧으면 확인보다 그 문제를 먼저 알려 준다`() {
        assertEquals(
            PasswordProblem.TooShort,
            EmailPasswordRules.checkPasswordConfirmation("short", "short"),
        )
    }

    @Test
    fun `로그인은 이메일 모양과 비어 있지 않은 비밀번호만 본다`() {
        // 로그인은 기존 계정이므로 길이 규칙을 새로 들이대지 않는다.
        assertTrue(EmailPasswordRules.canSignIn("hong@example.com", "old"))
        assertFalse(EmailPasswordRules.canSignIn("hong@example.com", ""))
        assertFalse(EmailPasswordRules.canSignIn("hong", "whatever"))
    }
}
