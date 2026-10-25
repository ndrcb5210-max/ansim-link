package io.github.ndrcb5210max.ansimlink.domain.message

import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 안심 메시지 선택. 무료는 공통 하나, 프리미엄은 재난 유형별까지 쓴다. */
class SafetyMessagesTest {

    @Test
    fun `아무것도 적지 않으면 기본 문구를 보낸다`() {
        val selected = SafetyMessages.select(
            messages = SafetyMessageSet(),
            type = DisasterType.Earthquake,
            plan = Plan.Free,
        )

        assertEquals(SafetyMessages.DEFAULT, selected)
    }

    @Test
    fun `공통 메시지를 적으면 그것을 보낸다`() {
        val selected = SafetyMessages.select(
            messages = SafetyMessageSet(common = "집에 있어. 걱정 마."),
            type = DisasterType.HeavyRain,
            plan = Plan.Free,
        )

        assertEquals("집에 있어. 걱정 마.", selected)
    }

    @Test
    fun `공통 메시지가 비어 있으면 기본 문구로 돌아간다`() {
        // 사용자가 입력란을 다 지운 경우. 아무 메시지도 안 가는 것보다 기본 문구가 낫다.
        assertEquals(
            SafetyMessages.DEFAULT,
            SafetyMessages.select(SafetyMessageSet(common = ""), DisasterType.Other, Plan.Free),
        )
        assertEquals(
            SafetyMessages.DEFAULT,
            SafetyMessages.select(SafetyMessageSet(common = "   "), DisasterType.Other, Plan.Free),
        )
    }

    // ---- 요금제 ----

    @Test
    fun `무료는 유형별 메시지를 쓰지 않는다`() {
        val messages = SafetyMessageSet(
            common = "공통 문구",
            byType = mapOf(DisasterType.Earthquake to "지진이야. 책상 아래 있어."),
        )

        val selected = SafetyMessages.select(messages, DisasterType.Earthquake, Plan.Free)

        assertEquals("공통 문구", selected)
    }

    @Test
    fun `프리미엄은 유형별 메시지를 쓴다`() {
        val messages = SafetyMessageSet(
            common = "공통 문구",
            byType = mapOf(DisasterType.Earthquake to "지진이야. 책상 아래 있어."),
        )

        val selected = SafetyMessages.select(messages, DisasterType.Earthquake, Plan.Premium)

        assertEquals("지진이야. 책상 아래 있어.", selected)
    }

    @Test
    fun `프리미엄이어도 그 유형을 적지 않았으면 공통 문구를 쓴다`() {
        val messages = SafetyMessageSet(
            common = "공통 문구",
            byType = mapOf(DisasterType.Earthquake to "지진 문구"),
        )

        val selected = SafetyMessages.select(messages, DisasterType.Typhoon, Plan.Premium)

        assertEquals("공통 문구", selected)
    }

    @Test
    fun `유형별 메시지가 빈 문자열이면 안 적은 것으로 본다`() {
        val messages = SafetyMessageSet(
            common = "공통 문구",
            byType = mapOf(DisasterType.Earthquake to "   "),
        )

        assertEquals(
            "공통 문구",
            SafetyMessages.select(messages, DisasterType.Earthquake, Plan.Premium),
        )
    }

    @Test
    fun `구독을 해지하면 저장해 둔 유형별 문구가 쓰이지 않는다`() {
        // 유형별 문구는 지우지 않는다(다시 구독하면 되살아난다). 그래서 요금제를 꼭 봐야 한다.
        val messages = SafetyMessageSet(
            common = "공통 문구",
            byType = mapOf(DisasterType.Earthquake to "지진 문구"),
        )

        assertEquals(
            "지진 문구",
            SafetyMessages.select(messages, DisasterType.Earthquake, Plan.Premium),
        )
        assertEquals(
            "공통 문구",
            SafetyMessages.select(messages, DisasterType.Earthquake, Plan.Free),
        )
    }

    // ---- 유형 미상 ----

    @Test
    fun `유형 미상은 기타 칸의 문구를 쓴다`() {
        // 발동 규칙도 '기타' 칸을 보므로(DisasterType.ruleType) 서로 어긋나지 않게 맞춘다.
        val messages = SafetyMessageSet(
            common = "공통 문구",
            byType = mapOf(DisasterType.Other to "무슨 문자인지는 모르겠는데 나는 괜찮아."),
        )

        val selected = SafetyMessages.select(messages, DisasterType.Unknown, Plan.Premium)

        assertEquals("무슨 문자인지는 모르겠는데 나는 괜찮아.", selected)
    }

    // ---- 다듬기 ----

    @Test
    fun `줄바꿈과 이어진 공백은 한 칸으로 줄인다`() {
        assertEquals(
            "나는 괜찮아. 앱 봐 줘.",
            SafetyMessages.normalize("나는  괜찮아.\n\n앱 봐 줘."),
        )
    }

    @Test
    fun `앞뒤 공백을 지운다`() {
        assertEquals("괜찮아", SafetyMessages.normalize("  괜찮아  "))
    }

    @Test
    fun `200자를 넘으면 자른다`() {
        val long = "가".repeat(300)

        val normalized = SafetyMessages.normalize(long)

        assertEquals(SafetyMessages.MAX_LENGTH, normalized.length)
    }

    @Test
    fun `저장된 값이 200자를 넘어도 보낼 때 자른다`() {
        val messages = SafetyMessageSet(common = "나".repeat(500))

        val selected = SafetyMessages.select(messages, DisasterType.Other, Plan.Free)

        assertEquals(SafetyMessages.MAX_LENGTH, selected.length)
    }

    @Test
    fun `기본 문구는 상한보다 짧다`() {
        assertTrue(SafetyMessages.DEFAULT.length <= SafetyMessages.MAX_LENGTH)
    }
}
