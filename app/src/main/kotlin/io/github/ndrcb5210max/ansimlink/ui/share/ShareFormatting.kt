package io.github.ndrcb5210max.ansimlink.ui.share

import kotlin.math.roundToInt

/**
 * 거리를 사람이 읽는 글로 바꾼다. 순수 함수라 단위 테스트가 쉽다.
 *
 * 문자열 리소스를 쓰지 않는 이유: 숫자 단위("m"/"km")만 붙이는 일이고, 화면이 아니라
 * ViewModel 에서 미리 만들어 둬야 스크린샷이 흔들리지 않는다. 문장이 들어가는 문구는
 * strings.xml 에 있다(`family_distance` 가 "내 위치에서 %1$s" 로 감싼다).
 */
object ShareFormatting {

    /** 이 거리부터 km 로 보여 준다. */
    private const val KILOMETER_THRESHOLD_METERS = 1_000.0

    /**
     * 거리 문자열.
     *
     * - 1km 미만: 10m 단위로 끊어 "320m". 재난 상황에서 1m 단위 정확도는 뜻이 없고,
     *   위치 정확도 자체가 수십 미터라 더 정밀하게 적으면 거짓 정밀도가 된다.
     * - 1km 이상: 소수 한 자리 "1.2km".
     * - 10km 이상: 정수 "12km". 그 거리에서 100m 차이는 뜻이 없다.
     */
    fun distanceText(meters: Double): String {
        if (meters < KILOMETER_THRESHOLD_METERS) {
            val rounded = (meters / 10).roundToInt() * 10
            return rounded.toString() + "m"
        }
        val kilometers = meters / KILOMETER_THRESHOLD_METERS
        return if (kilometers >= 10) {
            kilometers.roundToInt().toString() + "km"
        } else {
            // 소수 한 자리. String.format 의 로캘 의존을 피하려고 직접 만든다
            // (로캘에 따라 소수점이 쉼표가 되면 "1,2km" 가 되어 어색하다).
            val tenths = (kilometers * 10).roundToInt()
            (tenths / 10).toString() + "." + (tenths % 10).toString() + "km"
        }
    }

    /**
     * 외부 지도 앱에 넘길 `geo:` URI.
     *
     * 지도 SDK 와 API 키를 쓰지 않는 방법이다(CLAUDE.md 3장). `q=` 에 좌표를 한 번 더
     * 넣는 이유: 그러지 않으면 어떤 지도 앱은 그 좌표로 화면만 옮기고 핀을 찍지 않는다.
     *
     * 좌표는 로캘과 무관하게 점(.)을 쓰는 형식이어야 한다. `geo:` 는 URI 문법이고
     * 쉼표 소수점은 파싱되지 않으므로 [Double.toString] 을 쓴다(항상 점을 쓴다).
     */
    fun geoUri(latitude: Double, longitude: Double, label: String): String {
        val coordinates = latitude.toString() + "," + longitude.toString()
        return "geo:" + coordinates + "?q=" + coordinates + "(" + encodeLabel(label) + ")"
    }

    /**
     * 핀 이름에 쓸 수 없는 글자를 뺀다.
     *
     * 괄호와 앰퍼샌드가 들어가면 URI 가 깨진다. 사용자 이름이 들어오는 자리라서 거른다.
     */
    private fun encodeLabel(label: String): String =
        label.filterNot { it in "()&?#" }.ifBlank { "위치" }
}
