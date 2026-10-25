package io.github.ndrcb5210max.ansimlink.domain.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareStateTest {

    @Test
    fun `동의했고 일시중지가 아니면 동의함이다`() {
        assertEquals(ShareState.Consented, ShareState.of(locationConsent = true, paused = false))
    }

    @Test
    fun `동의했지만 일시중지면 일시중지다`() {
        assertEquals(ShareState.Paused, ShareState.of(locationConsent = true, paused = true))
    }

    @Test
    fun `동의가 없으면 일시중지 값과 상관없이 철회다`() {
        assertEquals(ShareState.Withdrawn, ShareState.of(locationConsent = false, paused = false))
        assertEquals(ShareState.Withdrawn, ShareState.of(locationConsent = false, paused = true))
    }

    @Test
    fun `동의한 상태에서만 위치를 공유한다`() {
        // CLAUDE.md 원칙 1: 동의하지 않은 사람의 위치는 어떤 경우에도 수집·전송하지 않는다.
        assertTrue(ShareState.Consented.sharesLocation)
        assertFalse(ShareState.Paused.sharesLocation)
        assertFalse(ShareState.Withdrawn.sharesLocation)
    }
}
