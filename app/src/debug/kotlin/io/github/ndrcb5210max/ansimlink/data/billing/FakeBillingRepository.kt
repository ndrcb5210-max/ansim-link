package io.github.ndrcb5210max.ansimlink.data.billing

import android.app.Activity
import io.github.ndrcb5210max.ansimlink.domain.billing.BillingPeriod
import io.github.ndrcb5210max.ansimlink.domain.billing.PhaseRecurrence
import io.github.ndrcb5210max.ansimlink.domain.billing.PricingPhase
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionCatalog
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionOffer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 디버그 빌드 전용 가짜 결제 저장소. **릴리스 빌드에는 이 파일이 없다**
 * (`app/src/debug/` 소스셋이다 — `BillingRepositoryFactory.kt` 주석 참고).
 *
 * ## 무엇을 시험할 수 있나
 * Play Console 계정 없이 **결제 화면의 흐름**과 **한도 적용**을 볼 수 있다.
 * 가격 표시, 무료 체험 문구, 버튼이 꺼지는 경우, 결제 취소, 가격 미수신 등
 * [FakeBillingScenario] 로 고른다.
 *
 * ## 무엇은 시험할 수 없나
 * **프리미엄이 열리지 않는다.** 프리미엄을 여는 근거는 서버가 쓴
 * `users/{uid}.entitlement` 하나이고(CLAUDE.md 3장), 이 가짜 저장소는 그 문서를 쓰지
 * 못한다 — 보안 규칙이 클라이언트 쓰기를 막는다. 그러므로 '가짜 결제 → 프리미엄 열림'
 * 까지 보려면 에뮬레이터에서 Admin 권한으로 그 문서를 직접 넣거나
 * (`docs/devlog/05-billing.md` 의 절차) 함수를 배포해야 한다.
 *
 * 이 제약은 일부러 남겼다. 가짜 결제가 프리미엄을 열 수 있으면 그 경로가 곧 우회
 * 경로이고, 디버그 빌드에만 있다는 것으로는 안심할 수 없다(디버그 APK 도 돌아다닌다).
 */
class FakeBillingRepository(
    private val scenario: FakeBillingScenario = FakeBillingScenario.MonthlyWithTrial,
) : BillingRepository {

    private val _state = MutableStateFlow(BillingState())
    override val state: Flow<BillingState> = _state.asStateFlow()

    private val purchases = mutableListOf<SubscriptionPurchase>()

    override suspend fun refresh() {
        _state.update { it.copy(connecting = true, lastResult = null) }
        // 진짜 Play 도 즉시 답하지 않는다. 화면이 '불러오는 중'을 제대로 그리는지 보려고
        // 일부러 조금 늦춘다.
        delay(FAKE_DELAY_MILLIS)
        _state.value = BillingState(
            connecting = false,
            offers = scenario.offers,
            unavailable = scenario.unavailable,
        )
    }

    override suspend fun launchPurchase(
        activity: Activity,
        offerToken: String,
        obfuscatedAccountId: String?,
    ): Boolean {
        if (offerToken.isBlank()) return false
        _state.update { it.copy(purchaseInProgress = true, lastResult = null) }
        delay(FAKE_DELAY_MILLIS)

        val outcome = scenario.purchaseOutcome
        if (outcome is PurchaseOutcome.Purchased) purchases += outcome.purchase
        _state.update { it.copy(purchaseInProgress = false, lastResult = outcome) }
        return true
    }

    override suspend fun queryPurchases(): List<SubscriptionPurchase> = purchases.toList()

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        val index = purchases.indexOfFirst { it.purchaseToken == purchaseToken }
        if (index < 0) return false
        purchases[index] = purchases[index].copy(acknowledged = true)
        return true
    }

    override fun close() = Unit

    private companion object {
        const val FAKE_DELAY_MILLIS = 400L
    }
}

/**
 * 가짜 결제가 흉내 낼 상황.
 *
 * 설정 > 디버그 > '가짜 결제' 화면에서 고른다. 고른 값은 다음 앱 시작 때 적용된다
 * (저장소는 [io.github.ndrcb5210max.ansimlink.AppContainer] 가 한 번만 만든다).
 */
enum class FakeBillingScenario(
    val label: String,
    val offers: List<SubscriptionOffer>,
    val unavailable: BillingUnavailable?,
    val purchaseOutcome: PurchaseOutcome,
) {

    /** 7일 무료 체험 + 월 2,500원. 전환 시점·금액 문구를 확인하는 데 쓴다. */
    MonthlyWithTrial(
        label = "월 구독 + 7일 무료 체험",
        offers = listOf(FakeOffers.trial, FakeOffers.base),
        unavailable = null,
        purchaseOutcome = PurchaseOutcome.Purchased(FakeOffers.purchase),
    ),

    /** 무료 체험 없이 월 2,500원. */
    MonthlyOnly(
        label = "월 구독만(무료 체험 없음)",
        offers = listOf(FakeOffers.base),
        unavailable = null,
        purchaseOutcome = PurchaseOutcome.Purchased(FakeOffers.purchase),
    ),

    /** 가격을 못 받은 경우. 결제 버튼이 꺼지고 이유가 보여야 한다. */
    PriceUnavailable(
        label = "가격을 받지 못함(상품 없음)",
        offers = emptyList(),
        unavailable = BillingUnavailable.ProductNotFound,
        purchaseOutcome = PurchaseOutcome.Failed(BillingUnavailable.ProductNotFound, null),
    ),

    /** 네트워크 오류. [다시 시도] 가 보여야 한다. */
    NetworkError(
        label = "네트워크 오류",
        offers = emptyList(),
        unavailable = BillingUnavailable.NetworkError,
        purchaseOutcome = PurchaseOutcome.Failed(BillingUnavailable.NetworkError, null),
    ),

    /** 사용자가 결제 화면을 닫은 경우. 빨간 글씨가 아니어야 한다. */
    UserCancels(
        label = "사용자가 결제를 취소",
        offers = listOf(FakeOffers.trial, FakeOffers.base),
        unavailable = null,
        purchaseOutcome = PurchaseOutcome.Cancelled,
    ),

    /** 결제가 아직 끝나지 않은 구매(계좌이체 등). */
    PendingPurchase(
        label = "결제 대기 중(계좌이체 등)",
        offers = listOf(FakeOffers.base),
        unavailable = null,
        purchaseOutcome = PurchaseOutcome.Pending,
    ),

    /** 이미 구독 중. 구매 복원으로 흘러야 한다. */
    AlreadyOwned(
        label = "이미 구독 중",
        offers = listOf(FakeOffers.base),
        unavailable = null,
        purchaseOutcome = PurchaseOutcome.AlreadyOwned,
    ),
    ;
}

/** 가짜 상품 데이터. 금액 모양은 Play 가 한국에서 주는 형식(`₩2,500`)에 맞췄다. */
private object FakeOffers {

    private val monthly = PricingPhase(
        formattedPrice = "₩2,500",
        priceAmountMicros = 2_500_000_000L,
        billingPeriod = BillingPeriod.parse("P1M"),
        billingCycleCount = 0,
        recurrence = PhaseRecurrence.Infinite,
    )

    val base = SubscriptionOffer(
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        basePlanId = "monthly",
        offerId = null,
        offerToken = "fake-offer-base",
        phases = listOf(monthly),
    )

    val trial = SubscriptionOffer(
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        basePlanId = "monthly",
        offerId = "free-trial",
        offerToken = "fake-offer-trial",
        phases = listOf(
            PricingPhase(
                formattedPrice = "₩0",
                priceAmountMicros = 0L,
                billingPeriod = BillingPeriod.parse("P7D"),
                billingCycleCount = 1,
                recurrence = PhaseRecurrence.Finite,
            ),
            monthly,
        ),
    )

    val purchase = SubscriptionPurchase(
        purchaseToken = "fake-purchase-token",
        productIds = listOf(SubscriptionCatalog.PREMIUM_MONTHLY),
        acknowledged = false,
        pending = false,
        orderId = "FAKE.0000-0000-0000-00000",
    )
}
