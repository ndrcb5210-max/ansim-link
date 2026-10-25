package io.github.ndrcb5210max.ansimlink.domain.share

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 위치 한 점. 안드로이드 `Location` 을 쓰지 않아 순수 Kotlin으로 테스트할 수 있다. */
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    /** 수평 정확도(m). 모르면 null. */
    val accuracyMeters: Float?,
    val atMillis: Long,
)

/** Firestore 에 쓸지 말지, 왜 그런지. 쓰기 건수를 줄이는 이유를 기록으로 남기려고 둔다. */
enum class LocationWriteVerdict(val key: String) {
    /** 세션의 첫 위치. 이건 거리·간격과 상관없이 쓴다. */
    First("first"),

    /** 50m 이상 움직였다. */
    Moved("moved"),

    /** 움직이지 않았지만 '아직 공유 중'을 알리려고 쓴다(하트비트). */
    Heartbeat("heartbeat"),

    /** 간격이 아직 안 됐다. */
    TooSoon("too_soon"),

    /** 간격은 됐지만 50m 안쪽이고 하트비트 시각도 아니다. */
    TooClose("too_close"),
    ;

    val shouldWrite: Boolean
        get() = this == First || this == Moved || this == Heartbeat
}

/**
 * Firestore 에 위치를 쓸지 정하는 규칙.
 *
 * 왜 조이는가: Firestore 무료 요금제는 하루 쓰기 2만 건이다. 구성원 3명이 60분 동안
 * 1초마다 쓰면 한 세션에 10,800건이라 이틀도 못 간다. 그래서 CLAUDE.md 가 정한 대로
 * **60초 간격 · 50m 이상 움직였을 때만** 쓴다.
 *
 * 그런데 '50m 이상 움직였을 때만' 만으로는 모자란다. 집에 가만히 있는 사람은 세션 내내
 * 첫 위치 한 건만 남아서, 보는 쪽에서 '이 사람 위치가 10분째 그대로인데 앱이 죽은 건가,
 * 정말 가만히 있는 건가'를 구분할 수 없다. 재난 상황에서 이 구분은 중요하다.
 * 그래서 [HEARTBEAT_MILLIS] 마다 한 번은 움직이지 않아도 쓴다.
 * 5분이면 60분 세션에서 최대 12건이라 쓰기 예산에 거의 영향이 없다.
 *
 * 전부 순수 함수다. 시계도 안드로이드도 쓰지 않는다.
 */
object LocationWriteRule {

    /** 위치 요청 간격. CLAUDE.md: 60초마다. */
    const val INTERVAL_MILLIS: Long = 60_000L

    /** 이만큼 움직이면 쓴다. CLAUDE.md: 50m 이상. */
    const val MIN_DISTANCE_METERS: Double = 50.0

    /** 움직이지 않아도 이만큼 지나면 한 번 쓴다('아직 공유 중'). */
    const val HEARTBEAT_MILLIS: Long = 5 * 60_000L

    /**
     * 정확도가 이보다 나쁜 위치는 버린다.
     *
     * 실내나 지하에서 Wi-Fi·셀 기반 위치가 수백 미터 오차로 오는 경우가 있다. 그 값을 쓰면
     * 가만히 있는 사람이 지도 위에서 500m 씩 튀어 다니고, '50m 이상 움직였다'로 잡혀
     * 쓰기 건수까지 늘어난다.
     */
    const val MAX_ACCURACY_METERS: Float = 500f

    /**
     * [candidate] 를 Firestore 에 쓸지 정한다.
     *
     * @param lastWritten 이 세션에서 **마지막으로 쓴** 위치. 아직 없으면 null.
     */
    fun decide(lastWritten: LocationPoint?, candidate: LocationPoint): LocationWriteVerdict {
        if (lastWritten == null) return LocationWriteVerdict.First

        val elapsed = candidate.atMillis - lastWritten.atMillis
        // 시계가 거꾸로 간 경우(시간대 변경·시간 보정)도 '아직 안 됐다'로 본다.
        if (elapsed < INTERVAL_MILLIS) return LocationWriteVerdict.TooSoon

        if (distanceMeters(lastWritten, candidate) >= MIN_DISTANCE_METERS) {
            return LocationWriteVerdict.Moved
        }
        if (elapsed >= HEARTBEAT_MILLIS) return LocationWriteVerdict.Heartbeat
        return LocationWriteVerdict.TooClose
    }

    /** 정확도가 너무 나쁜 위치인지. 이 검사는 [decide] 보다 먼저 한다. */
    fun isTooInaccurate(point: LocationPoint): Boolean {
        val accuracy = point.accuracyMeters ?: return false
        return accuracy > MAX_ACCURACY_METERS
    }

    /**
     * 두 점 사이 거리(m). 하버사인 공식.
     *
     * 안드로이드 `Location.distanceTo` 를 쓰지 않은 이유: 이 규칙을 순수 Kotlin으로 두어
     * Robolectric 없이 단위 테스트하려는 것이다. 하버사인은 지구를 완전한 구로 보므로
     * 실제와 0.5% 안쪽으로 다를 수 있는데, 50m 판정에는 충분하다
     * (50m 에서 0.5% 는 25cm 다).
     */
    fun distanceMeters(from: LocationPoint, to: LocationPoint): Double =
        distanceMeters(from.latitude, from.longitude, to.latitude, to.longitude)

    fun distanceMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Double {
        val latitudeDelta = Math.toRadians(toLatitude - fromLatitude)
        val longitudeDelta = Math.toRadians(toLongitude - fromLongitude)
        val fromLatitudeRadians = Math.toRadians(fromLatitude)
        val toLatitudeRadians = Math.toRadians(toLatitude)

        val sinHalfLatitude = sin(latitudeDelta / 2)
        val sinHalfLongitude = sin(longitudeDelta / 2)
        val haversine = sinHalfLatitude * sinHalfLatitude +
            cos(fromLatitudeRadians) * cos(toLatitudeRadians) *
            sinHalfLongitude * sinHalfLongitude

        // asin 의 입력이 1 을 아주 조금 넘어 NaN 이 되는 것을 막는다(부동소수점 오차).
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(haversine)))
    }

    /** 지구 평균 반지름(m). WGS84 평균값. */
    private const val EARTH_RADIUS_METERS = 6_371_008.8
}
