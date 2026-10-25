package io.github.ndrcb5210max.ansimlink.ui.billing

import io.github.ndrcb5210max.ansimlink.data.billing.BillingUnavailable
import io.github.ndrcb5210max.ansimlink.domain.billing.Entitlement
import io.github.ndrcb5210max.ansimlink.domain.billing.OfferPresentation
import io.github.ndrcb5210max.ansimlink.domain.group.Plan

/**
 * 결제 화면(paywall)과 설정 > 구독 화면의 상태.
 *
 * 2~4단계와 같은 방식이다. 화면은 상태와 콜백만 받고 시계·Play·Firestore 를 모른다.
 * 갱신일 같은 시각 문자열도 ViewModel 이 미리 만들어 넘기므로 스크린샷이 찍는 시각에
 * 따라 달라지지 않는다.
 *
 * @param plan 지금 요금제. 서버가 검증한 값이다.
 * @param offer Play 에서 받은 가격·주기·무료 체험. **null 이면 결제 버튼을 끈다.**
 * @param unavailable 가격을 못 받은 이유. 화면이 이것을 사람 말로 적는다.
 * @param manageUrl Google Play 구독 관리(해지) 링크.
 * @param renewalDateText "2026년 11월 24일" 처럼 미리 만든 문자열. 구독이 없으면 null.
 * @param verifiedAtText 서버가 마지막으로 확인한 시각. 구독이 없으면 null.
 * @param verifying 구매 토큰 검증을 기다리는 중. 결제 직후 잠깐 true 다.
 * @param notice 화면 위에 한 번 띄울 안내. 사용자가 읽으면 비운다.
 */
data class PaywallUiState(
    val loading: Boolean = true,
    val serverReady: Boolean = true,
    val plan: Plan = Plan.Free,
    val entitlement: Entitlement = Entitlement.none,
    val offer: OfferPresentation? = null,
    val unavailable: BillingUnavailable? = null,
    val purchaseInProgress: Boolean = false,
    val verifying: Boolean = false,
    val manageUrl: String = "",
    val renewalDateText: String? = null,
    val verifiedAtText: String? = null,
    val notice: PaywallNotice? = null,
) {

    val premium: Boolean get() = plan == Plan.Premium

    /**
     * 결제 버튼을 누를 수 있는지.
     *
     * 가격을 못 받으면 **반드시 꺼야 한다.** 금액을 모르는 채로 결제 흐름을 띄우면
     * 사용자가 얼마를 내는지 모르고 누르게 된다.
     */
    val canSubscribe: Boolean
        get() = offer != null &&
            unavailable == null &&
            !premium &&
            !purchaseInProgress &&
            !verifying &&
            serverReady

    /** 가격을 못 받아 버튼이 꺼진 상태인지. 화면이 이유와 [다시 시도]를 보여 준다. */
    val priceMissing: Boolean get() = !loading && (offer == null || unavailable != null)

    /** 다시 시도해 볼 만한 실패인지. 상품이 없는 것은 다시 눌러도 달라지지 않는다. */
    val retryable: Boolean
        get() = unavailable == BillingUnavailable.NetworkError ||
            unavailable == BillingUnavailable.Unknown
}

/**
 * 한 번 띄우는 안내.
 *
 * 성공·실패를 따로 두는 이유: 색과 말투가 달라야 한다. 특히 사용자가 결제를 **취소한**
 * 경우를 실패와 같이 두면 빨간 글씨로 겁을 주게 된다.
 */
enum class PaywallNotice {
    /** 검증까지 끝나 프리미엄이 열렸다. */
    PremiumActivated,

    /** 결제는 됐지만 서버 확인이 아직이다. 잠시 뒤 다시 확인하라고 적는다. */
    VerificationPending,

    /** 결제가 진행 중이다(계좌이체 등). 끝나면 자동으로 열린다. */
    PurchasePending,

    /** 사용자가 결제를 취소했다. 오류가 아니다. */
    PurchaseCancelled,

    /** Play 가 이 구매를 인정하지 않았다. */
    PurchaseRejected,

    /** 결제·검증이 실패했다. 다시 시도할 수 있다. */
    PurchaseFailed,

    /** 이미 구독 중인 것을 찾아 되살렸다. */
    PurchaseRestored,
}
