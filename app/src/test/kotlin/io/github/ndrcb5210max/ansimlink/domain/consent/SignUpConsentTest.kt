package io.github.ndrcb5210max.ansimlink.domain.consent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 가입 동의 규칙.
 *
 * 제일 중요한 것: 필수 항목이 하나라도 빠지면 가입 버튼이 꺼져야 한다(CLAUDE.md 원칙 8).
 */
class SignUpConsentTest {

    @Test
    fun `아무것도 체크하지 않으면 가입할 수 없다`() {
        assertFalse(SignUpConsentState().canSubmit)
    }

    @Test
    fun `네 항목을 모두 체크해야 가입할 수 있다`() {
        val state = SignUpConsentState(accepted = SignUpConsent.entries.toSet())
        assertTrue(state.canSubmit)
        assertTrue(state.allAccepted)
        assertTrue(state.missing.isEmpty())
    }

    @Test
    fun `항목이 하나라도 빠지면 가입 버튼이 꺼진다`() {
        SignUpConsent.entries.forEach { skipped ->
            val state = SignUpConsentState(
                accepted = SignUpConsent.entries.toSet() - skipped,
            )
            assertFalse("$skipped 가 빠졌는데도 가입이 가능하다", state.canSubmit)
            assertEquals(listOf(skipped), state.missing)
        }
    }

    @Test
    fun `만 14세 확인이 빠지면 가입할 수 없다`() {
        val state = SignUpConsentState(
            accepted = setOf(
                SignUpConsent.TermsOfService,
                SignUpConsent.PrivacyPolicy,
                SignUpConsent.LocationTerms,
            ),
        )
        assertFalse(state.canSubmit)
        assertEquals(listOf(SignUpConsent.AgeOver14), state.missing)
    }

    @Test
    fun `토글은 켜고 끈다`() {
        val once = SignUpConsentState().toggle(SignUpConsent.AgeOver14)
        assertTrue(once.isAccepted(SignUpConsent.AgeOver14))

        val twice = once.toggle(SignUpConsent.AgeOver14)
        assertFalse(twice.isAccepted(SignUpConsent.AgeOver14))
    }

    @Test
    fun `전체 동의는 모두 켜고 모두 끈다`() {
        val all = SignUpConsentState().setAll(true)
        assertTrue(all.canSubmit)

        val none = all.setAll(false)
        assertFalse(none.canSubmit)
        assertEquals(SignUpConsent.entries.size, none.missing.size)
    }

    @Test
    fun `전체 동의 체크 상태는 항목이 모두 켜졌을 때만 켜진다`() {
        val almost = SignUpConsentState(
            accepted = SignUpConsent.entries.toSet() - SignUpConsent.LocationTerms,
        )
        assertFalse(almost.allAccepted)
    }

    @Test
    fun `동의 기록에는 항목 키와 약관 버전과 시각이 남는다`() {
        val now = 1_761_000_000_000L
        val records = SignUpConsentState().setAll(true).toRecords(now)

        assertEquals(SignUpConsent.entries.size, records.size)
        SignUpConsent.entries.forEach { item ->
            val record = requireNotNull(records[item.key]) { "${item.key} 기록이 없다" }
            assertTrue(record.accepted)
            assertEquals(item.version, record.version)
            assertEquals(now, record.acceptedAtMillis)
        }
    }

    @Test
    fun `체크하지 않은 항목은 기록에 남기지 않는다`() {
        val records = SignUpConsentState(accepted = setOf(SignUpConsent.AgeOver14))
            .toRecords(0L)
        assertEquals(setOf(SignUpConsent.AgeOver14.key), records.keys)
    }

    @Test
    fun `동의 항목 키는 서로 다르다`() {
        // Firestore 필드 이름이라 겹치면 기록이 덮어써진다.
        val keys = SignUpConsent.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `약관이 있는 항목은 문서가 연결되어 있다`() {
        assertEquals(LegalDocument.TermsOfService, SignUpConsent.TermsOfService.document)
        assertEquals(LegalDocument.PrivacyPolicy, SignUpConsent.PrivacyPolicy.document)
        assertEquals(LegalDocument.LocationTerms, SignUpConsent.LocationTerms.document)
        // 만 14세 확인은 약관이 아니라 확인 항목이다.
        assertEquals(null, SignUpConsent.AgeOver14.document)
    }

    @Test
    fun `약관 문서는 경로 키로 되찾을 수 있다`() {
        LegalDocument.entries.forEach { document ->
            assertEquals(document, LegalDocument.fromRouteKey(document.routeKey))
        }
        assertEquals(null, LegalDocument.fromRouteKey("없는키"))
    }
}
