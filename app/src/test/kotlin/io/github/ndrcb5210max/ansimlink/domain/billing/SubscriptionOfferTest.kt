package io.github.ndrcb5210max.ansimlink.domain.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 혜택 고르기와 가격 표시.
 *
 * 결제 화면에 적는 금액·주기·무료 체험 전환 시점이 여기서 나온다. 틀리면 바로
 * '표시 가격과 청구 금액이 다르다'가 되므로 경우마다 못박아 둔다.
 */
class SubscriptionOfferTest {

    private fun phase(
        price: String,
        micros: Long,
        period: String,
        cycles: Int = 0,
        recurrence: PhaseRecurrence = PhaseRecurrence.Infinite,
    ) = PricingPhase(
        formattedPrice = price,
        priceAmountMicros = micros,
        billingPeriod = BillingPeriod.parse(period),
        billingCycleCount = cycles,
        recurrence = recurrence,
    )

    private val basePhase = phase("₩2,500", 2_500_000_000L, "P1M")

    private val baseOffer = SubscriptionOffer(
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        basePlanId = "monthly",
        offerId = null,
        offerToken = "token-base",
        phases = listOf(basePhase),
    )

    private val trialOffer = SubscriptionOffer(
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        basePlanId = "monthly",
        offerId = "free-trial",
        offerToken = "token-trial",
        phases = listOf(
            phase("₩0", 0L, "P7D", cycles = 1, recurrence = PhaseRecurrence.Finite),
            basePhase,
        ),
    )

    @Test
    fun `혜택이 없으면 고를 것도 없다`() {
        assertNull(SubscriptionOffers.choose(emptyList()))
        assertNull(SubscriptionOffers.presentBest(emptyList()))
    }

    @Test
    fun `무료 체험이 붙은 혜택을 고른다`() {
        val chosen = SubscriptionOffers.choose(listOf(baseOffer, trialOffer))

        assertEquals("token-trial", chosen?.offerToken)
    }

    @Test
    fun `목록 순서가 달라도 같은 것을 고른다`() {
        assertEquals(
            "token-trial",
            SubscriptionOffers.choose(listOf(trialOffer, baseOffer))?.offerToken,
        )
    }

    @Test
    fun `무료 체험이 없으면 기준 요금제를 고른다`() {
        assertEquals("token-base", SubscriptionOffers.choose(listOf(baseOffer))?.offerToken)
    }

    @Test
    fun `첫 단계 가격이 같으면 단계가 많은 쪽을 고른다`() {
        val trialThenDiscount = trialOffer.copy(
            offerToken = "token-trial-discount",
            phases = listOf(
                phase("₩0", 0L, "P7D", cycles = 1, recurrence = PhaseRecurrence.Finite),
                phase("₩1,000", 1_000_000_000L, "P1M", cycles = 2, PhaseRecurrence.Finite),
                basePhase,
            ),
        )

        val chosen = SubscriptionOffers.choose(listOf(trialOffer, trialThenDiscount))

        assertEquals("token-trial-discount", chosen?.offerToken)
    }

    @Test
    fun `단계가 없는 혜택은 쓰지 않는다`() {
        val broken = baseOffer.copy(offerToken = "token-broken", phases = emptyList())

        assertFalse(broken.usable)
        assertEquals("token-base", SubscriptionOffers.choose(listOf(broken, baseOffer))?.offerToken)
    }

    @Test
    fun `토큰이 빈 혜택은 쓰지 않는다`() {
        // Billing 8 부터 빈 offerToken 을 결제에 넘기면 예외가 난다. 미리 걸러 낸다.
        val broken = baseOffer.copy(offerToken = " ")

        assertFalse(broken.usable)
        assertNull(SubscriptionOffers.choose(listOf(broken)))
    }

    @Test
    fun `기준 요금제만 있으면 되풀이 단계 하나를 보여 준다`() {
        val presentation = SubscriptionOffers.present(baseOffer)

        assertNotNull(presentation)
        assertEquals(1, presentation!!.steps.size)
        assertEquals("₩2,500", presentation.renewal?.formattedPrice)
        assertEquals(BillingPeriodUnit.Month, presentation.renewal?.period?.unit)
        assertEquals(1, presentation.renewal?.period?.count)
        assertTrue(presentation.renewal?.repeating == true)
        assertNull(presentation.freeTrial)
        assertTrue(presentation.beforeRenewal.isEmpty())
    }

    @Test
    fun `무료 체험은 전환 시점과 금액을 같이 알려 준다`() {
        val presentation = SubscriptionOffers.present(trialOffer)!!

        // 7일 무료 → 그 뒤부터 매월 2,500원. 화면이 이 두 줄을 그대로 적는다.
        val trial = presentation.freeTrial!!
        assertTrue(trial.free)
        assertEquals(BillingPeriodUnit.Day, trial.period.unit)
        assertEquals(7, trial.period.count)
        assertEquals(1, trial.cycles)

        assertEquals("₩2,500", presentation.renewal?.formattedPrice)
        assertEquals(1, presentation.beforeRenewal.size)
    }

    @Test
    fun `되풀이 단계가 없으면 자동 갱신 문구를 적지 않는다`() {
        // 선불 요금제(한 번만 결제). 이 앱에는 아직 없지만 Play 에서 만들 수 있다.
        val prepaid = baseOffer.copy(
            offerToken = "token-prepaid",
            phases = listOf(
                phase("₩2,500", 2_500_000_000L, "P1M", recurrence = PhaseRecurrence.NonRecurring),
            ),
        )

        val presentation = SubscriptionOffers.present(prepaid)!!

        assertNull(presentation.renewal)
        assertEquals(1, presentation.steps.size)
    }

    @Test
    fun `고른 혜택의 토큰을 그대로 결제에 넘긴다`() {
        // 화면에 적은 가격과 결제되는 가격이 달라지면 안 된다.
        val presentation = SubscriptionOffers.presentBest(listOf(baseOffer, trialOffer))!!

        assertEquals("token-trial", presentation.offerToken)
        assertEquals("free-trial", presentation.offerId)
    }

    @Test
    fun `결제 주기 문자열을 읽는다`() {
        assertEquals(BillingPeriodUnit.Month to 1, BillingPeriod.parse("P1M").pair())
        assertEquals(BillingPeriodUnit.Month to 3, BillingPeriod.parse("P3M").pair())
        assertEquals(BillingPeriodUnit.Year to 1, BillingPeriod.parse("P1Y").pair())
        assertEquals(BillingPeriodUnit.Week to 2, BillingPeriod.parse("P2W").pair())
        assertEquals(BillingPeriodUnit.Day to 14, BillingPeriod.parse("P14D").pair())
    }

    @Test
    fun `모르는 주기 형식은 원래 문자열을 그대로 둔다`() {
        // 억지로 해석해서 틀린 주기를 적는 것이 가장 나쁘다. 화면이 raw 를 그대로 보여 준다.
        val weird = BillingPeriod.parse("P1M2W")

        assertFalse(weird.known)
        assertEquals("P1M2W", weird.raw)
        assertNull(weird.unit)

        assertFalse(BillingPeriod.parse("PT1H").known)
        assertFalse(BillingPeriod.parse("").known)
        assertFalse(BillingPeriod.parse(null).known)
        assertFalse(BillingPeriod.parse("P0M").known)
    }

    private fun BillingPeriod.pair() = unit to count
}
