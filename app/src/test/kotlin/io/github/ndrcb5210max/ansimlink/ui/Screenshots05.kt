package io.github.ndrcb5210max.ansimlink.ui

import io.github.ndrcb5210max.ansimlink.data.billing.BillingUnavailable
import io.github.ndrcb5210max.ansimlink.domain.billing.BillingPeriod
import io.github.ndrcb5210max.ansimlink.domain.billing.Entitlement
import io.github.ndrcb5210max.ansimlink.domain.billing.EntitlementState
import io.github.ndrcb5210max.ansimlink.domain.billing.OfferPresentation
import io.github.ndrcb5210max.ansimlink.domain.billing.OfferStep
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionCatalog
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.ui.billing.PaywallNotice
import io.github.ndrcb5210max.ansimlink.ui.billing.PaywallUiState
import io.github.ndrcb5210max.ansimlink.ui.share.ShareSettingsUiState
import io.github.ndrcb5210max.ansimlink.domain.share.AutoStartStep

// 5단계 스크린샷 테스트가 같이 쓰는 것들.
//
// 2~4단계와 같은 방식이다. 날짜도 문자열로 미리 넣는다(화면이 시계를 모른다 —
// BillingViewModel 이 만들어 준다). 그래서 그림이 찍는 시각이나 시간대에 따라
// 달라지지 않는다.
//
// 금액은 Play 가 한국에서 주는 형식(`₩2,500`)을 흉내 낸 **예시**다. 실제 금액은
// Play Console 에서 정하고, 앱은 Play 가 준 formattedPrice 를 그대로 보여 준다.

private const val MONTHLY_PRICE = "₩2,500"

private val monthlyStep = OfferStep(
    formattedPrice = MONTHLY_PRICE,
    free = false,
    period = BillingPeriod.parse("P1M"),
    cycles = 0,
    repeating = true,
)

private val trialStep = OfferStep(
    formattedPrice = "₩0",
    free = true,
    period = BillingPeriod.parse("P7D"),
    cycles = 1,
    repeating = false,
)

/** 월 구독만. 기준 요금제 하나다. */
private val monthlyOffer = OfferPresentation(
    productId = SubscriptionCatalog.PREMIUM_MONTHLY,
    basePlanId = "monthly",
    offerId = null,
    offerToken = "token-base",
    steps = listOf(monthlyStep),
    renewal = monthlyStep,
)

/** 7일 무료 체험 + 월 구독. */
private val trialOffer = OfferPresentation(
    productId = SubscriptionCatalog.PREMIUM_MONTHLY,
    basePlanId = "monthly",
    offerId = "free-trial",
    offerToken = "token-trial",
    steps = listOf(trialStep, monthlyStep),
    renewal = monthlyStep,
)

/**
 * 결제 화면 — 무료 체험이 있는 경우.
 *
 * 이 그림을 기본으로 고른 이유: 구독 정책이 요구하는 것이 한 화면에 다 들어간다.
 * 가격, 결제 주기, 자동 갱신, **무료 체험 전환 시점과 금액**, 해지 방법.
 */
internal val paywallWithTrial = PaywallUiState(
    loading = false,
    plan = Plan.Free,
    offer = trialOffer,
    manageUrl = "https://play.google.com/store/account/subscriptions",
)

/** 결제 화면 — 무료 체험 없이 월 구독만. */
internal val paywallMonthlyOnly = PaywallUiState(
    loading = false,
    plan = Plan.Free,
    offer = monthlyOffer,
    manageUrl = "https://play.google.com/store/account/subscriptions",
)

/**
 * 결제 화면 — **가격을 못 받은 경우.**
 *
 * 이 단계에서 가장 보여 주고 싶은 그림이다. 금액을 모르는 채로 결제 흐름을 띄우지
 * 않으려고 버튼을 끄고, 끄는 것으로 끝내지 않고 이유와 할 수 있는 일을 적는다.
 */
internal val paywallPriceUnavailable = PaywallUiState(
    loading = false,
    plan = Plan.Free,
    offer = null,
    unavailable = BillingUnavailable.ProductNotFound,
    manageUrl = "https://play.google.com/store/account/subscriptions",
)

/** 결제 화면 — 네트워크 오류. [다시 시도]가 보인다. */
internal val paywallNetworkError = PaywallUiState(
    loading = false,
    plan = Plan.Free,
    offer = null,
    unavailable = BillingUnavailable.NetworkError,
    manageUrl = "https://play.google.com/store/account/subscriptions",
)

/** 설정 > 구독 — 무료 요금제. */
internal val subscriptionFree = PaywallUiState(
    loading = false,
    plan = Plan.Free,
    entitlement = Entitlement.none,
    manageUrl = "https://play.google.com/store/account/subscriptions",
)

/** 설정 > 구독 — 정상적으로 결제되고 있는 상태. */
internal val subscriptionActive = PaywallUiState(
    loading = false,
    plan = Plan.Premium,
    entitlement = Entitlement(
        state = EntitlementState.Active,
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        // 화면에 보이는 날짜는 아래 renewalDateText 다. 이 값은 화면이 쓰지 않는다.
        expiryTimeMillis = 1L,
        autoRenewing = true,
        verifiedAtMillis = 1L,
    ),
    renewalDateText = "2026년 11월 24일",
    verifiedAtText = "2026년 10월 25일",
    manageUrl = "https://play.google.com/store/account/subscriptions" +
        "?sku=premium_monthly&package=io.github.ndrcb5210max.ansimlink",
)

/**
 * 설정 > 구독 — 해지해 두었고 기간이 남은 상태.
 *
 * 이 그림이 따로 필요한 이유: '다음 결제일'과 '해지 예약됨 · 언제까지'는 전혀 다른
 * 정보인데 같은 문구를 쓰면 해지한 사람이 또 결제되는 줄 안다.
 */
internal val subscriptionCancelled = PaywallUiState(
    loading = false,
    plan = Plan.Premium,
    entitlement = Entitlement(
        state = EntitlementState.Canceled,
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        expiryTimeMillis = 1L,
        autoRenewing = false,
        verifiedAtMillis = 1L,
    ),
    renewalDateText = "2026년 11월 24일",
    verifiedAtText = "2026년 10월 25일",
    manageUrl = "https://play.google.com/store/account/subscriptions" +
        "?sku=premium_monthly&package=io.github.ndrcb5210max.ansimlink",
)

/**
 * 설정 > 구독 — 결제가 막힌 상태(유예 기간).
 *
 * 프리미엄은 아직 켜져 있고 결제 수단을 고칠 시간이 있다. 보류(on hold)와 문구를
 * 나눈 이유는 '지금 쓸 수 있나'가 다르기 때문이다.
 */
internal val subscriptionGracePeriod = PaywallUiState(
    loading = false,
    plan = Plan.Premium,
    entitlement = Entitlement(
        state = EntitlementState.InGracePeriod,
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        expiryTimeMillis = 1L,
        autoRenewing = true,
        verifiedAtMillis = 1L,
    ),
    renewalDateText = "2026년 11월 1일",
    verifiedAtText = "2026년 10월 25일",
    manageUrl = "https://play.google.com/store/account/subscriptions" +
        "?sku=premium_monthly&package=io.github.ndrcb5210max.ansimlink",
)

/** 설정 > 구독 — 결제가 보류되어 프리미엄이 꺼진 상태. */
internal val subscriptionOnHold = PaywallUiState(
    loading = false,
    plan = Plan.Free,
    entitlement = Entitlement(
        state = EntitlementState.OnHold,
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        expiryTimeMillis = 1L,
        autoRenewing = true,
        verifiedAtMillis = 1L,
    ),
    renewalDateText = "2026년 10월 20일",
    // 보류 상태의 구독은 autoRenewing 이 true 로 남아 있는데 기간은 과거다.
    // 그때 '다음 결제일'이라고 적으면 지난 날짜가 미래 일처럼 보인다.
    renewalDatePast = true,
    verifiedAtText = "2026년 10월 25일",
    manageUrl = "https://play.google.com/store/account/subscriptions" +
        "?sku=premium_monthly&package=io.github.ndrcb5210max.ansimlink",
)

/** 결제 화면 — 결제는 됐는데 서버 확인이 늦은 상태. 열어 주지 않고 안내만 한다. */
internal val paywallVerificationPending = PaywallUiState(
    loading = false,
    plan = Plan.Free,
    offer = monthlyOffer,
    notice = PaywallNotice.VerificationPending,
    manageUrl = "https://play.google.com/store/account/subscriptions",
)

/** 공유 시간 설정 — 프리미엄이라 180분까지 고를 수 있는 상태. */
internal val shareSettingsPremium = ShareSettingsUiState(
    shareMinutes = 180,
    maxAllowedMinutes = 180,
    premium = true,
    autoStartEnabled = true,
    autoStartStep = AutoStartStep.Ready,
    locationPermissionGranted = true,
    backgroundLocationGranted = true,
    notificationsEnabled = true,
)
