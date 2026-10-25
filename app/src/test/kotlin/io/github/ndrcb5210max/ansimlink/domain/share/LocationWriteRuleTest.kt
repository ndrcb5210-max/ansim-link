package io.github.ndrcb5210max.ansimlink.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Firestore 쓰기 판정(60초 간격 · 50m 이상).
 *
 * 좌표는 서울시청 근처를 기준점으로 쓰되 값 자체에 뜻은 없다. 위도 0.001도가 약 111m,
 * 경도 0.001도가 서울 위도(37.57도)에서 약 88m 다. 그 숫자로 50m 경계를 만든다.
 */
class LocationWriteRuleTest {

    private val baseLatitude = 37.5665
    private val baseLongitude = 126.9780

    private fun point(
        latitude: Double = baseLatitude,
        longitude: Double = baseLongitude,
        atMillis: Long,
        accuracyMeters: Float? = 12f,
    ) = LocationPoint(latitude, longitude, accuracyMeters, atMillis)

    // ---- 간격 ----

    @Test
    fun `세션의 첫 위치는 무조건 쓴다`() {
        val verdict = LocationWriteRule.decide(
            lastWritten = null,
            candidate = point(atMillis = START),
        )

        assertEquals(LocationWriteVerdict.First, verdict)
        assertTrue(verdict.shouldWrite)
    }

    @Test
    fun `60초가 지나지 않으면 쓰지 않는다`() {
        val last = point(atMillis = START)
        // 아무리 멀리 움직였어도 간격이 안 됐으면 쓰지 않는다.
        val candidate = point(latitude = baseLatitude + 0.05, atMillis = START + 59_000)

        val verdict = LocationWriteRule.decide(last, candidate)

        assertEquals(LocationWriteVerdict.TooSoon, verdict)
        assertFalse(verdict.shouldWrite)
    }

    @Test
    fun `시계가 거꾸로 가도 쓰지 않는다`() {
        // 시간대 변경이나 시간 보정으로 뒤 위치의 시각이 더 이를 수 있다.
        val last = point(atMillis = START)
        val candidate = point(latitude = baseLatitude + 0.05, atMillis = START - 10_000)

        assertEquals(LocationWriteVerdict.TooSoon, LocationWriteRule.decide(last, candidate))
    }

    // ---- 50m 판정 ----

    @Test
    fun `60초가 지났고 50m 이상 움직이면 쓴다`() {
        val last = point(atMillis = START)
        // 위도 +0.0006도 ≈ 67m
        val candidate = point(latitude = baseLatitude + 0.0006, atMillis = START + 60_000)

        val verdict = LocationWriteRule.decide(last, candidate)

        assertEquals(LocationWriteVerdict.Moved, verdict)
        assertTrue(verdict.shouldWrite)
    }

    @Test
    fun `60초가 지났어도 50m 안쪽이면 쓰지 않는다`() {
        val last = point(atMillis = START)
        // 위도 +0.0002도 ≈ 22m
        val candidate = point(latitude = baseLatitude + 0.0002, atMillis = START + 60_000)

        val verdict = LocationWriteRule.decide(last, candidate)

        assertEquals(LocationWriteVerdict.TooClose, verdict)
        assertFalse(verdict.shouldWrite)
    }

    @Test
    fun `정확히 50m 움직이면 쓴다`() {
        val last = point(atMillis = START)
        // 위도 1도 = 약 111,195m 이므로 50m 는 약 0.00044964도다.
        val fiftyMetersInDegrees = 50.0 / (Math.PI * 6_371_008.8 / 180.0)
        val candidate = point(
            latitude = baseLatitude + fiftyMetersInDegrees,
            atMillis = START + 60_000,
        )

        val distance = LocationWriteRule.distanceMeters(last, candidate)
        assertTrue("거리가 50m 근처여야 한다: " + distance, distance >= 50.0)
        assertEquals(LocationWriteVerdict.Moved, LocationWriteRule.decide(last, candidate))
    }

    @Test
    fun `경도로 움직여도 거리를 제대로 센다`() {
        // 위도 37.57도에서 경도 0.001도는 약 88m 다. 위도에 따라 달라지는 것을 확인한다.
        val from = point(atMillis = START)
        val to = point(longitude = baseLongitude + 0.001, atMillis = START + 60_000)

        val distance = LocationWriteRule.distanceMeters(from, to)

        assertTrue("약 88m 여야 한다: " + distance, distance in 80.0..95.0)
    }

    @Test
    fun `같은 자리는 거리가 0이다`() {
        val point = point(atMillis = START)

        // 부동소수점 오차로 NaN 이 되지 않아야 한다.
        assertEquals(0.0, LocationWriteRule.distanceMeters(point, point), 0.001)
    }

    // ---- 하트비트 ----

    @Test
    fun `가만히 있어도 5분마다 한 번은 쓴다`() {
        val last = point(atMillis = START)
        val candidate = point(atMillis = START + 5 * 60_000)

        val verdict = LocationWriteRule.decide(last, candidate)

        assertEquals(LocationWriteVerdict.Heartbeat, verdict)
        assertTrue(verdict.shouldWrite)
    }

    @Test
    fun `4분째에는 하트비트를 쓰지 않는다`() {
        val last = point(atMillis = START)
        val candidate = point(atMillis = START + 4 * 60_000)

        assertEquals(LocationWriteVerdict.TooClose, LocationWriteRule.decide(last, candidate))
    }

    @Test
    fun `60분 동안 가만히 있으면 쓰기는 13건을 넘지 않는다`() {
        // 첫 위치 1건 + 5분마다 하트비트 12건 = 13건.
        // 60초마다 위치가 들어오는 상황을 그대로 돌려 센다.
        var lastWritten: LocationPoint? = null
        var writes = 0

        for (tick in 0..60) {
            val candidate = point(atMillis = START + tick * 60_000L)
            val verdict = LocationWriteRule.decide(lastWritten, candidate)
            if (verdict.shouldWrite) {
                writes++
                lastWritten = candidate
            }
        }

        assertEquals(13, writes)
    }

    @Test
    fun `60분 동안 계속 움직이면 쓰기는 61건이다`() {
        // 60초마다 100m 씩 움직이는 경우(걷는 속도보다 조금 느리다).
        // 한 세션 최대 쓰기 건수를 눈으로 확인해 두려는 테스트다.
        // 구성원 3명이면 183건이라 무료 요금제 하루 2만 건 안에서 100세션쯤 된다.
        var lastWritten: LocationPoint? = null
        var writes = 0

        for (tick in 0..60) {
            val candidate = point(
                latitude = baseLatitude + tick * 0.0009,
                atMillis = START + tick * 60_000L,
            )
            val verdict = LocationWriteRule.decide(lastWritten, candidate)
            if (verdict.shouldWrite) {
                writes++
                lastWritten = candidate
            }
        }

        assertEquals(61, writes)
    }

    // ---- 정확도 ----

    @Test
    fun `정확도가 500m 보다 나쁜 위치는 버린다`() {
        assertTrue(LocationWriteRule.isTooInaccurate(point(atMillis = START, accuracyMeters = 900f)))
        assertFalse(LocationWriteRule.isTooInaccurate(point(atMillis = START, accuracyMeters = 500f)))
        assertFalse(LocationWriteRule.isTooInaccurate(point(atMillis = START, accuracyMeters = 20f)))
    }

    @Test
    fun `정확도를 모르는 위치는 버리지 않는다`() {
        // 정확도가 없다고 쓸모없는 위치는 아니다. 기록에 '정확도 모름'으로 남는다.
        assertFalse(LocationWriteRule.isTooInaccurate(point(atMillis = START, accuracyMeters = null)))
    }

    private companion object {
        const val START = 1_792_000_000_000L
    }
}
