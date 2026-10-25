package io.github.ndrcb5210max.ansimlink.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 거리 문자열과 `geo:` URI.
 *
 * 둘 다 순수 함수라 Robolectric 없이 돌린다. 특히 `geo:` URI 는 **로캘에 따라 깨질 수
 * 있는 자리**라서 테스트를 달아 두었다(소수점이 쉼표가 되면 지도 앱이 좌표를 못 읽는다).
 */
class ShareFormattingTest {

    // ---- 거리 ----

    @Test
    fun `1km 미만은 10m 단위로 끊는다`() {
        // 위치 정확도 자체가 수십 미터라, 1m 단위로 적으면 거짓 정밀도가 된다.
        assertEquals("320m", ShareFormatting.distanceText(321.4))
        assertEquals("320m", ShareFormatting.distanceText(318.0))
        assertEquals("50m", ShareFormatting.distanceText(50.0))
        assertEquals("0m", ShareFormatting.distanceText(3.0))
    }

    @Test
    fun `1km 이상은 소수 한 자리다`() {
        assertEquals("1.2km", ShareFormatting.distanceText(1_234.0))
        assertEquals("1.0km", ShareFormatting.distanceText(1_000.0))
        assertEquals("9.9km", ShareFormatting.distanceText(9_870.0))
    }

    @Test
    fun `10km 이상은 정수다`() {
        // 그 거리에서 100m 차이는 뜻이 없다.
        assertEquals("12km", ShareFormatting.distanceText(12_300.0))
        assertEquals("10km", ShareFormatting.distanceText(10_000.0))
        assertEquals("431km", ShareFormatting.distanceText(430_900.0))
    }

    @Test
    fun `1km 경계에서 단위가 바뀐다`() {
        assertEquals("1000m", ShareFormatting.distanceText(999.9))
        assertEquals("1.0km", ShareFormatting.distanceText(1_000.0))
    }

    @Test
    fun `거리 문자열에 소수점 쉼표가 들어가지 않는다`() {
        // String.format 을 쓰면 로캘에 따라 '1,2km' 가 된다. 직접 만드는 이유다.
        val text = ShareFormatting.distanceText(1_234.0)

        assertTrue("쉼표가 없어야 한다: " + text, !text.contains(','))
    }

    // ---- geo: URI ----

    @Test
    fun `geo URI 에 좌표가 두 번 들어간다`() {
        // q= 가 없으면 어떤 지도 앱은 화면만 옮기고 핀을 찍지 않는다.
        val uri = ShareFormatting.geoUri(37.5665, 126.978, "지은")

        assertEquals("geo:37.5665,126.978?q=37.5665,126.978(지은)", uri)
    }

    @Test
    fun `geo URI 의 소수점은 항상 점이다`() {
        // 쉼표 소수점이 들어가면 좌표가 네 개로 보여 지도 앱이 파싱에 실패한다.
        val uri = ShareFormatting.geoUri(37.5665, 126.978, "지은")

        assertTrue("소수점은 점이어야 한다: " + uri, uri.contains("37.5665"))
        assertEquals(2, uri.count { it == ',' })
    }

    @Test
    fun `음수 좌표도 그대로 넣는다`() {
        // 남반구·서반구. 한국만 쓰는 앱이지만 좌표 처리에 부호를 빼먹지 않았는지 본다.
        val uri = ShareFormatting.geoUri(-33.8688, -151.2093, "x")

        assertTrue(uri.startsWith("geo:-33.8688,-151.2093?"))
    }

    @Test
    fun `핀 이름에서 URI 를 깨는 글자를 뺀다`() {
        // 사용자 이름이 들어오는 자리다. 괄호와 앰퍼샌드가 들어가면 URI 가 깨진다.
        val uri = ShareFormatting.geoUri(1.0, 2.0, "지은(엄마)&hi?#")

        assertEquals("geo:1.0,2.0?q=1.0,2.0(지은엄마hi)", uri)
    }

    @Test
    fun `핀 이름이 비면 기본 이름을 쓴다`() {
        // 이름이 빈 구성원이 있을 수 있다. 빈 괄호는 지도 앱에서 어색하게 보인다.
        assertEquals("geo:1.0,2.0?q=1.0,2.0(위치)", ShareFormatting.geoUri(1.0, 2.0, ""))
        assertEquals("geo:1.0,2.0?q=1.0,2.0(위치)", ShareFormatting.geoUri(1.0, 2.0, "   "))
    }
}
