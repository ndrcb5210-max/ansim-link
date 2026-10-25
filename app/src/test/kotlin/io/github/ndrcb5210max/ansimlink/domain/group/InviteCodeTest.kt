package io.github.ndrcb5210max.ansimlink.domain.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InviteCodeTest {

    private val issuedAt = 1_761_000_000_000L

    @Test
    fun `코드는 여섯 자리다`() {
        repeat(200) { seed ->
            assertEquals(6, InviteCode.generate(Random(seed)).length)
        }
    }

    @Test
    fun `헷갈리는 글자는 쓰지 않는다`() {
        // 0 과 O, 1 과 I 와 L 은 받아 적을 때 섞인다.
        val forbidden = setOf('0', 'O', '1', 'I', 'L')
        repeat(500) { seed ->
            val code = InviteCode.generate(Random(seed))
            assertTrue(
                "$code 에 헷갈리는 글자가 들어 있다",
                code.none { it in forbidden },
            )
        }
    }

    @Test
    fun `만들어진 코드는 형식 검사를 통과한다`() {
        repeat(200) { seed ->
            assertTrue(InviteCode.isWellFormed(InviteCode.generate(Random(seed))))
        }
    }

    @Test
    fun `같은 씨앗이면 같은 코드가 나온다`() {
        assertEquals(InviteCode.generate(Random(42)), InviteCode.generate(Random(42)))
    }

    @Test
    fun `소문자와 공백과 하이픈을 정리한다`() {
        assertEquals("KH3M7P", InviteCode.normalize(" kh3m-7p "))
        assertEquals("KH3M7P", InviteCode.normalize("KH3M7P"))
        assertEquals("KH3M7P", InviteCode.normalize("k h 3 m 7 p"))
    }

    @Test
    fun `형식이 틀린 코드는 걸러 낸다`() {
        assertFalse("다섯 자리", InviteCode.isWellFormed("KH3M7"))
        assertFalse("일곱 자리", InviteCode.isWellFormed("KH3M7PQ"))
        assertFalse("빈 값", InviteCode.isWellFormed(""))
        assertFalse("쓰지 않는 글자 O", InviteCode.isWellFormed("KH3M7O"))
        assertFalse("쓰지 않는 글자 0", InviteCode.isWellFormed("KH3M70"))
        assertFalse("소문자", InviteCode.isWellFormed("kh3m7p"))
    }

    @Test
    fun `만료 시각은 만든 때로부터 24시간 뒤다`() {
        assertEquals(
            issuedAt + 24 * 60 * 60 * 1000,
            InviteCode.expiresAtMillis(issuedAt),
        )
    }

    @Test
    fun `24시간 안에는 만료되지 않는다`() {
        val expiresAt = InviteCode.expiresAtMillis(issuedAt)

        assertFalse("만든 직후", InviteCode.isExpired(expiresAt, issuedAt))
        assertFalse("23시간 59분 뒤", InviteCode.isExpired(expiresAt, issuedAt + 86_340_000))
    }

    @Test
    fun `만료 시각과 같은 순간은 아직 쓸 수 있다`() {
        // 경계값은 사용자에게 유리한 쪽으로 둔다.
        val expiresAt = InviteCode.expiresAtMillis(issuedAt)
        assertFalse(InviteCode.isExpired(expiresAt, expiresAt))
    }

    @Test
    fun `24시간이 지나면 만료된다`() {
        val expiresAt = InviteCode.expiresAtMillis(issuedAt)
        assertTrue(InviteCode.isExpired(expiresAt, expiresAt + 1))
        assertTrue(InviteCode.isExpired(expiresAt, issuedAt + 25 * 60 * 60 * 1000))
    }

    @Test
    fun `남은 시간을 분으로 알려 준다`() {
        val expiresAt = InviteCode.expiresAtMillis(issuedAt)

        assertEquals(24 * 60L, InviteCode.remainingMinutes(expiresAt, issuedAt))
        assertEquals(60L, InviteCode.remainingMinutes(expiresAt, expiresAt - 3_600_000))
    }

    @Test
    fun `이미 만료됐으면 남은 시간은 0분이다`() {
        val expiresAt = InviteCode.expiresAtMillis(issuedAt)
        assertEquals(0L, InviteCode.remainingMinutes(expiresAt, expiresAt + 10_000))
    }
}
