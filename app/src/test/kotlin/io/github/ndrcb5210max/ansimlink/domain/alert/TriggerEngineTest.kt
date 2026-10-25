package io.github.ndrcb5210max.ansimlink.domain.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 발동 규칙 엔진 테스트.
 *
 * 분류 결과는 [AlertClassifier] 를 거치지 않고 직접 만든다. 여기서 확인할 것은
 * '규칙 표와 최근 기록을 보고 발동할지 정하는 부분'뿐이기 때문이다.
 */
class TriggerEngineTest {

    private val now = 1_761_000_000_000L

    private fun classification(
        severity: AlertSeverity = AlertSeverity.Emergency,
        type: DisasterType = DisasterType.Earthquake,
        fingerprint: String = "aaaa",
    ) = AlertClassification(
        severity = severity,
        type = type,
        category = null,
        confidence = AlertConfidence.High,
        bundleSummary = false,
        bodyPreview = "본문",
        bodyFingerprint = fingerprint,
        channelId = null,
    )

    private fun decide(
        classification: AlertClassification,
        rules: TriggerRules = TriggerRules.default,
        previous: List<PreviousTrigger> = emptyList(),
        nowMillis: Long = now,
    ) = TriggerEngine.decide(classification, rules, previous, nowMillis)

    // --- 기본값 ---

    @Test
    fun `기본값에서 위급재난문자는 모든 유형으로 발동한다`() {
        DisasterType.selectableEntries.forEach { type ->
            val decision = decide(classification(AlertSeverity.Extreme, type))
            assertTrue(type.label + " 에서 발동하지 않았다", decision.triggered)
        }
    }

    @Test
    fun `기본값에서 긴급재난문자는 모든 유형으로 발동한다`() {
        DisasterType.selectableEntries.forEach { type ->
            assertTrue(decide(classification(AlertSeverity.Emergency, type)).triggered)
        }
    }

    @Test
    fun `기본값에서 안전안내문자는 발동하지 않는다`() {
        val decision = decide(classification(AlertSeverity.PublicSafety, DisasterType.HeatWave))
        assertFalse(decision.triggered)
        assertEquals(TriggerReason.SeverityDisabled, decision.reason)
    }

    @Test
    fun `기본값에서 실종경보문자는 발동하지 않는다`() {
        val decision = decide(classification(AlertSeverity.AmberAlert, DisasterType.Other))
        assertFalse(decision.triggered)
        assertEquals(TriggerReason.SeverityDisabled, decision.reason)
    }

    @Test
    fun `종류 미상으로는 발동하지 않는다`() {
        val decision = decide(classification(AlertSeverity.Unknown, DisasterType.Unknown))
        assertFalse(decision.triggered)
        assertEquals(TriggerReason.SeverityUnknown, decision.reason)
    }

    @Test
    fun `종류 미상은 규칙을 켜 두어도 발동하지 않는다`() {
        // 사용자가 '종류 미상'을 켤 방법은 없다. 그래도 표에 값이 들어가 있어도
        // 발동하지 않는다는 것을 못박아 둔다.
        val rules = TriggerRules(
            bySeverity = mapOf(
                AlertSeverity.Unknown to SeverityRule(enabled = true, types = TriggerRules.allTypes),
            ),
        )
        val decision = decide(classification(AlertSeverity.Unknown, DisasterType.Unknown), rules)
        assertEquals(TriggerReason.SeverityUnknown, decision.reason)
    }

    // --- 사용자가 고친 규칙 ---

    @Test
    fun `안전안내문자를 켜면 발동한다`() {
        val rules = TriggerRules.default
            .withSeverityEnabled(AlertSeverity.PublicSafety, true)
        assertTrue(
            decide(classification(AlertSeverity.PublicSafety, DisasterType.HeatWave), rules)
                .triggered,
        )
    }

    @Test
    fun `유형 칩을 끄면 그 유형만 발동하지 않는다`() {
        val rules = TriggerRules.default
            .withType(AlertSeverity.Emergency, DisasterType.HeatWave, on = false)

        val heatWave = decide(classification(AlertSeverity.Emergency, DisasterType.HeatWave), rules)
        assertEquals(TriggerReason.TypeDisabled, heatWave.reason)

        val earthquake =
            decide(classification(AlertSeverity.Emergency, DisasterType.Earthquake), rules)
        assertTrue(earthquake.triggered)
    }

    @Test
    fun `스위치를 끄면 고른 유형이 남아 있어도 발동하지 않는다`() {
        val rules = TriggerRules.default
            .withSeverityEnabled(AlertSeverity.Emergency, false)
        val decision = decide(classification(AlertSeverity.Emergency, DisasterType.Earthquake), rules)
        assertEquals(TriggerReason.SeverityDisabled, decision.reason)
        // 유형은 그대로 남아 있다. 다시 켜면 그대로 쓰인다.
        assertEquals(TriggerRules.allTypes, rules.rule(AlertSeverity.Emergency).types)
    }

    @Test
    fun `표에 없는 종류는 발동하지 않는다`() {
        val rules = TriggerRules(bySeverity = emptyMap())
        val decision = decide(classification(AlertSeverity.Extreme, DisasterType.Earthquake), rules)
        assertEquals(TriggerReason.SeverityDisabled, decision.reason)
    }

    @Test
    fun `유형 미상은 기타 칸의 설정을 따른다`() {
        val onlyOtherOff = TriggerRules.default
            .withType(AlertSeverity.Emergency, DisasterType.Other, on = false)
        assertEquals(
            TriggerReason.TypeDisabled,
            decide(classification(AlertSeverity.Emergency, DisasterType.Unknown), onlyOtherOff)
                .reason,
        )
        assertTrue(
            "기타가 켜져 있으면 유형 미상도 발동한다",
            decide(classification(AlertSeverity.Emergency, DisasterType.Unknown)).triggered,
        )
    }

    // --- 중복 ---

    @Test
    fun `같은 본문은 10분 안에 한 번만 발동한다`() {
        val previous = listOf(
            PreviousTrigger("aaaa", AlertSeverity.Emergency, DisasterType.Earthquake, now - 60_000),
        )
        val decision = decide(classification(fingerprint = "aaaa"), previous = previous)
        assertEquals(TriggerReason.DuplicateBody, decision.reason)
    }

    @Test
    fun `10분이 지나면 같은 본문도 다시 발동한다`() {
        val previous = listOf(
            PreviousTrigger(
                "aaaa",
                AlertSeverity.Emergency,
                DisasterType.Earthquake,
                now - TriggerEngine.DEDUPE_WINDOW_MILLIS,
            ),
        )
        assertTrue(decide(classification(fingerprint = "aaaa"), previous = previous).triggered)
    }

    @Test
    fun `10분 경계 바로 앞은 아직 중복이다`() {
        val previous = listOf(
            PreviousTrigger(
                "aaaa",
                AlertSeverity.Emergency,
                DisasterType.Earthquake,
                now - TriggerEngine.DEDUPE_WINDOW_MILLIS + 1,
            ),
        )
        assertEquals(
            TriggerReason.DuplicateBody,
            decide(classification(fingerprint = "aaaa"), previous = previous).reason,
        )
    }

    @Test
    fun `언어별 사본은 본문이 달라도 같은 종류 유형이면 한 번만 발동한다`() {
        val previous = listOf(
            PreviousTrigger("korean", AlertSeverity.Emergency, DisasterType.Earthquake, now - 1_000),
        )
        val english = classification(fingerprint = "english")
        assertEquals(
            TriggerReason.DuplicateSameKind,
            decide(english, previous = previous).reason,
        )
    }

    @Test
    fun `종류가 올라가면 다시 발동한다`() {
        // 안전안내 → 긴급으로 올라가는 경우. 같은 유형이어도 다른 문자로 본다.
        val previous = listOf(
            PreviousTrigger(
                "aaaa",
                AlertSeverity.PublicSafety,
                DisasterType.Earthquake,
                now - 1_000,
            ),
        )
        assertTrue(decide(classification(fingerprint = "bbbb"), previous = previous).triggered)
    }

    @Test
    fun `유형이 다르면 다시 발동한다`() {
        val previous = listOf(
            PreviousTrigger("aaaa", AlertSeverity.Emergency, DisasterType.Earthquake, now - 1_000),
        )
        val typhoon = classification(type = DisasterType.Typhoon, fingerprint = "bbbb")
        assertTrue(decide(typhoon, previous = previous).triggered)
    }

    @Test
    fun `본문 없는 알림의 지문은 중복 판단에 쓰지 않는다`() {
        // 본문이 없는 알림은 지문이 모두 'empty' 라서 지문만 보면 전부 중복이 된다.
        // 종류·유형 검사로 넘어가야 한다.
        val previous = listOf(
            PreviousTrigger(
                AlertText.EMPTY_FINGERPRINT,
                AlertSeverity.Emergency,
                DisasterType.Typhoon,
                now - 1_000,
            ),
        )
        val noBody = classification(
            type = DisasterType.Unknown,
            fingerprint = AlertText.EMPTY_FINGERPRINT,
        )
        assertTrue("유형이 달라 발동해야 한다", decide(noBody, previous = previous).triggered)
    }

    @Test
    fun `10분보다 오래된 기록은 중복 판단에서 빠진다`() {
        val previous = listOf(
            PreviousTrigger(
                "aaaa",
                AlertSeverity.Emergency,
                DisasterType.Earthquake,
                now - 2 * TriggerEngine.DEDUPE_WINDOW_MILLIS,
            ),
        )
        assertTrue(decide(classification(fingerprint = "aaaa"), previous = previous).triggered)
    }

    @Test
    fun `규칙 검사가 중복 검사보다 먼저다`() {
        // 꺼 둔 종류라면 중복인지 따질 필요가 없고, 기록에도 '설정이 꺼져 있다'로 남아야 한다.
        val previous = listOf(
            PreviousTrigger(
                "aaaa",
                AlertSeverity.PublicSafety,
                DisasterType.HeatWave,
                now - 1_000,
            ),
        )
        val decision = decide(
            classification(AlertSeverity.PublicSafety, DisasterType.HeatWave, "aaaa"),
            previous = previous,
        )
        assertEquals(TriggerReason.SeverityDisabled, decision.reason)
    }

    // --- 규칙 표 다루기 ---

    @Test
    fun `중복으로 보는 시간 폭은 10분이다`() {
        assertEquals(10 * 60 * 1000L, TriggerEngine.DEDUPE_WINDOW_MILLIS)
    }

    @Test
    fun `유형을 켜고 끄는 것은 그 종류에만 영향을 준다`() {
        val rules = TriggerRules.default
            .withType(AlertSeverity.Emergency, DisasterType.Typhoon, on = false)
        assertFalse(DisasterType.Typhoon in rules.rule(AlertSeverity.Emergency).types)
        assertTrue(DisasterType.Typhoon in rules.rule(AlertSeverity.Extreme).types)
    }

    @Test
    fun `모든 종류의 기본 유형 묶음은 12개다`() {
        AlertSeverity.configurableEntries.forEach { severity ->
            assertEquals(12, TriggerRules.default.rule(severity).types.size)
        }
    }

    @Test
    fun `유형 미상은 설정 화면에 보여 주지 않는다`() {
        assertFalse(DisasterType.Unknown.selectable)
        assertFalse(DisasterType.Unknown in TriggerRules.allTypes)
    }
}
