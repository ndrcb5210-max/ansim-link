package io.github.ndrcb5210max.ansimlink.domain.billing

/**
 * 가격 단계가 되풀이되는 방식. Play 의 `ProductDetails.RecurrenceMode` 를 옮긴 것이다.
 *
 * 숫자 상수를 그대로 쓰지 않고 enum 으로 옮긴 이유: 이 파일 아래의 판정들을 Billing
 * 라이브러리 없이 단위 테스트하려는 것이다. 숫자 ↔ enum 변환은 데이터 계층
 * ([io.github.ndrcb5210max.ansimlink.data.billing.PlayBillingRepository]) 한 곳에서만 한다.
 */
enum class PhaseRecurrence {
    /** 끝없이 되풀이. 이것이 '매월 얼마'에 해당하는 단계다. */
    Infinite,

    /** 정해진 횟수만 되풀이(소개 가격·무료 체험). */
    Finite,

    /** 한 번만(선불 요금제). */
    NonRecurring,
}

/**
 * 가격 단계 한 개. `ProductDetails.PricingPhase` 를 옮긴 것이다.
 *
 * @param formattedPrice Play 가 통화 기호까지 붙여 준 문자열(예: `₩2,500`).
 *   **앱이 직접 만들지 않는다.** 통화·자리수·기호 위치는 나라마다 다르고,
 *   표시 가격과 실제 청구 금액이 어긋나면 그대로 정책 위반이다.
 * @param priceAmountMicros 100만분의 1 단위 금액. 0 이면 무료 단계다.
 * @param billingCycleCount 이 단계가 몇 주기 가는지. 끝없이 되풀이·한 번만 인 단계는 0.
 */
data class PricingPhase(
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val billingPeriod: BillingPeriod,
    val billingCycleCount: Int,
    val recurrence: PhaseRecurrence,
) {
    val free: Boolean get() = priceAmountMicros == 0L
}

/**
 * 구독 상품의 혜택(offer) 하나. `ProductDetails.SubscriptionOfferDetails` 를 옮긴 것이다.
 *
 * `queryProductDetailsAsync` 는 **그 사용자가 받을 수 있는** 혜택만 돌려준다. 즉 무료
 * 체험을 이미 써 버린 사람에게는 체험이 붙은 혜택이 오지 않는다. 그래서 앱이 '체험을
 * 쓴 적 있나'를 따로 판단하지 않는다 — 판단할 방법도 없다.
 *
 * @param offerToken 결제 흐름에 넘기는 값. 빈 문자열을 넘기면 Billing 8 부터 예외가 난다.
 */
data class SubscriptionOffer(
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val phases: List<PricingPhase>,
) {
    /** 단계가 하나도 없으면 가격을 보여 줄 수 없다. */
    val usable: Boolean get() = offerToken.isNotBlank() && phases.isNotEmpty()
}

/**
 * 결제 화면에 줄 단위로 적을 가격 단계.
 *
 * 화면이 이것을 위에서 아래로 그대로 적는다. 마지막 줄([repeating] 이 true 인 줄)이
 * 계속 빠져나가는 금액이다.
 *
 * @param cycles 몇 주기 가는지. [repeating] 이면 뜻이 없다(0).
 */
data class OfferStep(
    val formattedPrice: String,
    val free: Boolean,
    val period: BillingPeriod,
    val cycles: Int,
    val repeating: Boolean,
)

/**
 * 결제 화면이 쓰는 혜택 설명.
 *
 * @param steps 가격 단계 전부. 무료 체험이 있으면 첫 줄이 무료 단계다.
 * @param renewal 계속 빠져나가는 단계. 없으면(선불 요금제만 있는 경우) null.
 */
data class OfferPresentation(
    val productId: String,
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val steps: List<OfferStep>,
    val renewal: OfferStep?,
) {
    /** 무료 체험 단계. 없으면 null. */
    val freeTrial: OfferStep? get() = steps.firstOrNull { it.free && !it.repeating }

    /** 무료 체험·소개 가격이 끝나고 정가가 처음 청구되기 전 단계들. */
    val beforeRenewal: List<OfferStep> get() = steps.filterNot { it.repeating }
}

/** 혜택을 고르고 화면에 적을 모양으로 바꾼다. 순수 함수다. */
object SubscriptionOffers {

    /**
     * 받을 수 있는 혜택 중에서 **사용자에게 가장 유리한** 것을 고른다.
     *
     * 고르는 순서
     * 1. 첫 단계가 싼 것(무료 체험 → 소개 가격 → 정가).
     * 2. 같으면 유리한 단계가 더 많은 것(체험 + 할인 > 체험만).
     * 3. 그래도 같으면 Play 가 준 순서를 지킨다.
     *
     * 왜 '가장 유리한'인가: Play 가 이미 받을 수 있는 것만 보내 주므로, 그중 비싼 것을
     * 골라 보여 줄 이유가 없다. 보여 준 가격과 실제로 결제되는 가격이 같아야 하니
     * **화면에 적은 혜택의 [SubscriptionOffer.offerToken] 을 그대로 결제에 넘긴다**
     * ([OfferPresentation.offerToken]).
     *
     * 기준 요금제(basePlanId)가 여러 개인 상품은 지금 없다. 생기면 '어느 기준 요금제의
     * 혜택인가'를 먼저 갈라야 한다 — 지금은 상품 하나에 기준 요금제 하나다.
     */
    fun choose(offers: List<SubscriptionOffer>): SubscriptionOffer? =
        offers.filter { it.usable }
            .minWithOrNull(
                compareBy<SubscriptionOffer> { it.phases.first().priceAmountMicros }
                    .thenByDescending { it.phases.size },
            )

    /**
     * 혜택을 화면에 적을 모양으로 바꾼다.
     *
     * 되풀이되는 단계는 [PhaseRecurrence.Infinite] 로 판단한다. 그 단계가 '매월 얼마'다.
     * 선불 요금제(한 번만 결제)에는 그 단계가 없어서 [OfferPresentation.renewal] 이
     * null 이 되고, 화면은 '자동 갱신' 문구를 적지 않는다.
     */
    fun present(offer: SubscriptionOffer?): OfferPresentation? {
        if (offer == null || !offer.usable) return null
        val steps = offer.phases.map { phase ->
            OfferStep(
                formattedPrice = phase.formattedPrice,
                free = phase.free,
                period = phase.billingPeriod,
                cycles = if (phase.recurrence == PhaseRecurrence.Finite) {
                    phase.billingCycleCount
                } else {
                    0
                },
                repeating = phase.recurrence == PhaseRecurrence.Infinite,
            )
        }
        return OfferPresentation(
            productId = offer.productId,
            basePlanId = offer.basePlanId,
            offerId = offer.offerId,
            offerToken = offer.offerToken,
            steps = steps,
            renewal = steps.lastOrNull { it.repeating },
        )
    }

    /** [choose] 와 [present] 를 한 번에. 화면 쪽에서 쓰는 입구다. */
    fun presentBest(offers: List<SubscriptionOffer>): OfferPresentation? =
        present(choose(offers))
}
