package io.github.ndrcb5210max.ansimlink.ui.billing

import io.github.ndrcb5210max.ansimlink.data.billing.BillingUnavailable
import io.github.ndrcb5210max.ansimlink.domain.billing.BillingPeriod
import io.github.ndrcb5210max.ansimlink.domain.billing.Entitlement
import io.github.ndrcb5210max.ansimlink.domain.billing.EntitlementState
import io.github.ndrcb5210max.ansimlink.domain.billing.OfferPresentation
import io.github.ndrcb5210max.ansimlink.domain.billing.OfferStep
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionCatalog
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 결제 버튼을 누를 수 있는 조건.
 *
 * 여기서 가장 중요한 것은 **가격을 못 받았을 때 버튼이 꺼지는 것**이다. 금액을 모르는
 * 채로 결제 흐름을 띄우면 사용자가 얼마를 내는지 모르고 누르게 된다.
 */
class PaywallUiStateTest {

    private val monthly = OfferStep(
        formattedPrice = "₩2,500",
        free = false,
        period = BillingPeriod.parse("P1M"),
        cycles = 0,
        repeating = true,
    )

    private val offer = OfferPresentation(
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        basePlanId = "monthly",
        offerId = null,
        offerToken = "token-base",
        steps = listOf(monthly),
        renewal = monthly,
    )

    private val ready = PaywallUiState(loading = false, offer = offer)

    @Test
    fun `가격을 받았고 무료 요금제면 결제할 수 있다`() {
        assertTrue(ready.canSubscribe)
        assertFalse(ready.priceMissing)
    }

    @Test
    fun `혜택이 없으면 결제 버튼을 끈다`() {
        val noOffer = PaywallUiState(loading = false, offer = null)

        assertFalse(noOffer.canSubscribe)
        assertTrue(noOffer.priceMissing)
    }

    @Test
    fun `가격을 못 받은 이유가 있으면 결제 버튼을 끈다`() {
        // 혜택 목록이 어쩌다 남아 있어도 이유가 있으면 꺼야 한다.
        BillingUnavailable.entries.forEach { reason ->
            val blocked = ready.copy(unavailable = reason)

            assertFalse("이유: $reason", blocked.canSubscribe)
            assertTrue("이유: $reason", blocked.priceMissing)
        }
    }

    @Test
    fun `불러오는 중에는 가격 미수신으로 보지 않는다`() {
        // 연결·조회가 끝나기 전에 '금액을 받을 수 없습니다'를 띄우면 안 된다.
        val loading = PaywallUiState(loading = true, offer = null)

        assertFalse(loading.priceMissing)
        assertFalse(loading.canSubscribe)
    }

    @Test
    fun `이미 프리미엄이면 결제 버튼을 끈다`() {
        // 눌러도 ITEM_ALREADY_OWNED 가 나고, 그 전에 '또 결제되나' 하고 불안하게 만든다.
        val premium = ready.copy(plan = Plan.Premium)

        assertTrue(premium.premium)
        assertFalse(premium.canSubscribe)
    }

    @Test
    fun `결제 흐름이 떠 있는 동안에는 다시 누를 수 없다`() {
        assertFalse(ready.copy(purchaseInProgress = true).canSubscribe)
    }

    @Test
    fun `검증을 기다리는 동안에는 다시 누를 수 없다`() {
        // 두 번 누르면 구매가 두 건 생길 수 있다.
        assertFalse(ready.copy(verifying = true).canSubscribe)
    }

    @Test
    fun `서버 설정이 없으면 결제할 수 없다`() {
        // 가짜 google-services.json 으로는 검증 함수를 부를 수 없다. 결제만 되고
        // 프리미엄은 안 열리는 상태를 만들지 않는다.
        val noServer = ready.copy(serverReady = false)

        assertFalse(noServer.canSubscribe)
    }

    @Test
    fun `네트워크 오류는 다시 시도할 수 있다`() {
        assertTrue(ready.copy(unavailable = BillingUnavailable.NetworkError).retryable)
        assertTrue(ready.copy(unavailable = BillingUnavailable.Unknown).retryable)
    }

    @Test
    fun `상품이 없는 것은 다시 눌러도 달라지지 않는다`() {
        // Play Console 쪽 문제다. [다시 시도] 를 보여 주면 헛되게 누르게 한다.
        assertFalse(ready.copy(unavailable = BillingUnavailable.ProductNotFound).retryable)
        assertFalse(ready.copy(unavailable = BillingUnavailable.PlayStoreUnavailable).retryable)
        assertFalse(
            ready.copy(unavailable = BillingUnavailable.SubscriptionsNotSupported).retryable,
        )
        assertFalse(ready.copy(unavailable = BillingUnavailable.ServerNotConfigured).retryable)
    }

    @Test
    fun `무료 체험이 있으면 전환 안내에 쓸 단계를 찾는다`() {
        val trial = OfferStep(
            formattedPrice = "₩0",
            free = true,
            period = BillingPeriod.parse("P7D"),
            cycles = 1,
            repeating = false,
        )
        val withTrial = ready.copy(
            offer = offer.copy(steps = listOf(trial, monthly)),
        )

        assertNotNull(withTrial.offer!!.freeTrial)
        assertEquals(1, withTrial.offer!!.beforeRenewal.size)
    }

    @Test
    fun `무료 체험이 없으면 전환 안내를 띄우지 않는다`() {
        assertNull(ready.offer?.freeTrial)
        assertTrue(ready.offer?.beforeRenewal?.isEmpty() == true)
    }

    @Test
    fun `취소와 진행 중 안내는 오류 색을 쓰지 않는다`() {
        // 아무 문제가 없는데 빨간 글씨로 적으면 겁을 준다.
        assertFalse(PaywallNotice.PurchaseCancelled.isError)
        assertFalse(PaywallNotice.PurchasePending.isError)
        assertFalse(PaywallNotice.VerificationPending.isError)
        assertFalse(PaywallNotice.PremiumActivated.isError)
        assertFalse(PaywallNotice.PurchaseRestored.isError)

        assertTrue(PaywallNotice.PurchaseRejected.isError)
        assertTrue(PaywallNotice.PurchaseFailed.isError)
    }

    @Test
    fun `구독 상태가 화면 상태로 그대로 넘어온다`() {
        val grace = PaywallUiState(
            loading = false,
            plan = Plan.Premium,
            entitlement = Entitlement(
                state = EntitlementState.InGracePeriod,
                productId = SubscriptionCatalog.PREMIUM_MONTHLY,
                expiryTimeMillis = 1L,
                autoRenewing = true,
            ),
        )

        assertTrue(grace.entitlement.needsPaymentFix)
        assertTrue(grace.premium)
    }
}
