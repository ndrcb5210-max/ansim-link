package io.github.ndrcb5210max.ansimlink.domain.share

import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 공유 시간 계산과 자동 종료. 시계를 인자로 받으므로 시간을 마음대로 돌릴 수 있다. */
class ShareWindowTest {

    private val minute = ShareWindow.MILLIS_PER_MINUTE

    // ---- 끝 시각과 남은 시간 ----

    @Test
    fun `기본 60분이면 끝 시각은 시작 60분 뒤다`() {
        val window = ShareWindow(startedAtMillis = START, minutes = 60)

        assertEquals(START + 60 * minute, window.endsAtMillis)
    }

    @Test
    fun `남은 시간은 지금 시각을 뺀 값이다`() {
        val window = ShareWindow(START, minutes = 60)

        assertEquals(60 * minute, window.remainingMillis(START))
        assertEquals(45 * minute, window.remainingMillis(START + 15 * minute))
    }

    @Test
    fun `끝 시각이 지나면 남은 시간은 음수가 아니라 0이다`() {
        val window = ShareWindow(START, minutes = 60)

        assertEquals(0L, window.remainingMillis(START + 90 * minute))
    }

    @Test
    fun `남은 분은 올림한다`() {
        val window = ShareWindow(START, minutes = 60)

        // 59분 1초 지났으면 58.98초가 남았다 → '1분 남음'으로 보여 준다.
        assertEquals(1, window.remainingMinutes(START + 59 * minute + 1_000))
        // 딱 59분 지났으면 1분 남았다.
        assertEquals(1, window.remainingMinutes(START + 59 * minute))
        assertEquals(0, window.remainingMinutes(START + 60 * minute))
    }

    // ---- 자동 종료 ----

    @Test
    fun `끝 시각 직전에는 끝나지 않았다`() {
        val window = ShareWindow(START, minutes = 60)

        assertFalse(window.isExpired(START + 60 * minute - 1))
    }

    @Test
    fun `끝 시각에 닿으면 끝난 것으로 본다`() {
        val window = ShareWindow(START, minutes = 60)

        assertTrue(window.isExpired(START + 60 * minute))
        assertTrue(window.isExpired(START + 61 * minute))
    }

    // ---- 요금제 상한 ----

    @Test
    fun `무료는 60분을 넘겨 고를 수 없다`() {
        assertEquals(60, ShareDuration.effectiveMinutes(180, Plan.Free))
        assertEquals(60, ShareDuration.effectiveMinutes(90, Plan.Free))
        assertEquals(30, ShareDuration.effectiveMinutes(30, Plan.Free))
    }

    @Test
    fun `프리미엄은 180분까지 쓴다`() {
        assertEquals(180, ShareDuration.effectiveMinutes(180, Plan.Premium))
        assertEquals(120, ShareDuration.effectiveMinutes(120, Plan.Premium))
    }

    @Test
    fun `어떤 요금제에서도 180분을 넘기지 않는다`() {
        assertEquals(180, ShareDuration.effectiveMinutes(600, Plan.Premium))
    }

    @Test
    fun `30분보다 짧게는 공유하지 않는다`() {
        assertEquals(30, ShareDuration.effectiveMinutes(5, Plan.Free))
        assertEquals(30, ShareDuration.effectiveMinutes(0, Plan.Free))
        assertEquals(30, ShareDuration.effectiveMinutes(-10, Plan.Free))
    }

    @Test
    fun `구독이 끝나 상한이 내려가면 저장된 180분도 60분으로 줄어든다`() {
        // 프리미엄일 때 180분을 저장해 둔 사람이 구독을 해지한 경우.
        // 앱은 '막는' 방향으로만 움직이므로 저장된 값이 그대로 쓰이지 않는다.
        val saved = 180

        assertEquals(60, ShareDuration.effectiveMinutes(saved, Plan.Free))
    }

    @Test
    fun `고를 수 있는 값은 30분부터 180분까지 30분 눈금이다`() {
        assertEquals(listOf(30, 60, 90, 120, 150, 180), ShareDuration.selectableMinutes)
    }

    @Test
    fun `무료 요금제에서 90분 이상은 고를 수 없는 값으로 보인다`() {
        assertTrue(ShareDuration.isAllowed(60, Plan.Free))
        assertFalse(ShareDuration.isAllowed(90, Plan.Free))
        assertTrue(ShareDuration.isAllowed(180, Plan.Premium))
    }

    @Test
    fun `start 는 요금제 상한을 거친 창을 만든다`() {
        val window = ShareWindow.start(nowMillis = START, requestedMinutes = 180, plan = Plan.Free)

        assertEquals(60, window.minutes)
        assertEquals(START + 60 * minute, window.endsAtMillis)
    }

    private companion object {
        /** 2026-10-25 18:05:00 KST 쯤. 값 자체에 뜻은 없고 고정값이면 된다. */
        const val START = 1_792_000_000_000L
    }
}
