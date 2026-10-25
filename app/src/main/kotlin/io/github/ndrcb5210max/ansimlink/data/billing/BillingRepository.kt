package io.github.ndrcb5210max.ansimlink.data.billing

import android.app.Activity
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionOffer
import kotlinx.coroutines.flow.Flow

/**
 * Google Play 결제.
 *
 * 화면은 이 인터페이스만 본다. 덕분에 디버그 빌드에서 가짜 구현으로 화면 흐름과 한도
 * 적용을 시험할 수 있다(`app/src/debug/.../FakeBillingRepository.kt`).
 *
 * ## 이 인터페이스가 **하지 않는** 일
 * 프리미엄을 열지 않는다. 구매가 성공해도 돌려주는 것은 '구매 토큰'뿐이고, 그 토큰으로
 * 무엇을 할지는 [SubscriptionVerifier] 와 서버가 정한다. 기능을 여는 근거는 서버가 쓴
 * `users/{uid}.entitlement` 하나다(CLAUDE.md 3장).
 *
 * ## 확인(acknowledge)은 검증 뒤에
 * Play 는 구매를 3일 안에 확인(acknowledge)하지 않으면 **자동으로 환불하고 취소한다.**
 * 그래서 확인은 반드시 해야 하는데, 서버 검증 **뒤**에 한다([acknowledge]).
 * 확인을 먼저 하면 '검증이 실패했는데 결제는 확정된' 상태가 되어, 돈은 빠져나가고
 * 프리미엄은 안 열리면서 자동 환불도 안 되는 가장 나쁜 조합이 된다.
 */
interface BillingRepository {

    /** 연결·상품 조회 상태. 화면이 이것을 보고 버튼을 켜고 끈다. */
    val state: Flow<BillingState>

    /**
     * 연결하고 상품을 조회한다. 결제 화면을 열 때 부른다.
     *
     * 여러 번 불러도 괜찮다(이미 연결돼 있으면 상품만 다시 조회한다).
     */
    suspend fun refresh()

    /**
     * 결제 흐름을 띄운다.
     *
     * @param activity Play 의 결제 화면을 띄울 Activity. 결제 흐름은 Activity 가 필요하다.
     * @param offerToken 화면에 가격을 보여 준 그 혜택의 토큰. 다른 것을 넘기면
     *   보여 준 가격과 실제 결제 금액이 달라진다.
     * @param obfuscatedAccountId Play 에 넘기는 '우리 쪽 사용자 식별값'. uid 를 그대로
     *   넘기지 않고 해시해서 넘긴다(아래 [BillingRepository] 구현 주석 참고).
     * @return 흐름을 띄웠는지. 띄우지 못한 이유는 [state] 에 담긴다.
     */
    suspend fun launchPurchase(
        activity: Activity,
        offerToken: String,
        obfuscatedAccountId: String?,
    ): Boolean

    /**
     * 아직 처리하지 못한 구매를 다시 가져온다.
     *
     * 왜 필요한가: 결제가 끝난 직후 앱이 죽거나 네트워크가 끊기면 검증·확인을 못 한
     * 구매가 남는다. 그대로 두면 3일 뒤 Play 가 환불·취소한다. 그래서 결제 화면을 열
     * 때마다 한 번 훑고, 확인되지 않은 구매가 있으면 검증부터 다시 한다.
     */
    suspend fun queryPurchases(): List<SubscriptionPurchase>

    /**
     * 구매를 확인한다. **서버 검증이 성공한 뒤에만 부른다**(클래스 주석 참고).
     *
     * 이미 확인된 구매에 다시 불러도 괜찮다.
     */
    suspend fun acknowledge(purchaseToken: String): Boolean

    /** 결제 화면을 떠날 때 연결을 끊는다. */
    fun close()
}

/**
 * Play 에서 받은 구매 한 건.
 *
 * Billing 타입을 밖으로 내보내지 않는다. 가짜 구현을 만들 수 있어야 하고, 화면이
 * Billing 라이브러리를 import 하면 스크린샷 테스트에서 그 타입을 만들어야 한다.
 *
 * @param pending 결제가 아직 끝나지 않은 구매(계좌이체 등). 프리미엄을 열지 않는다.
 */
data class SubscriptionPurchase(
    val purchaseToken: String,
    val productIds: List<String>,
    val acknowledged: Boolean,
    val pending: Boolean,
    val orderId: String?,
)

/** 결제 화면이 보는 상태. */
data class BillingState(
    val connecting: Boolean = true,
    /** 받을 수 있는 혜택. 비어 있으면 가격을 보여 줄 수 없다. */
    val offers: List<SubscriptionOffer> = emptyList(),
    /** 가격을 못 받은 이유. 화면이 버튼을 끄고 이 이유를 적는다. */
    val unavailable: BillingUnavailable? = null,
    /** 결제 흐름이 떠 있는지. */
    val purchaseInProgress: Boolean = false,
    /** 마지막 결제 흐름의 결과. 화면이 안내를 띄우고 비운다. */
    val lastResult: PurchaseOutcome? = null,
) {
    val priceReady: Boolean get() = offers.isNotEmpty() && unavailable == null
}

/**
 * 가격을 못 받은 이유.
 *
 * 왜 이유를 나누는가: '구독할 수 없습니다'만 보여 주면 사용자가 할 수 있는 일이 없다.
 * [NetworkError] 면 다시 시도하면 되고, [PlayStoreUnavailable] 이면 Play 스토어를 고쳐야
 * 하고, [ProductNotFound] 는 우리 쪽(Play Console) 문제다.
 */
enum class BillingUnavailable {
    /** 네트워크 때문. 다시 시도하면 된다. */
    NetworkError,

    /** 이 기기에서 Play 결제를 쓸 수 없다(Play 스토어가 없거나 막혀 있다). */
    PlayStoreUnavailable,

    /** 구독 기능 자체를 지원하지 않는 기기·버전. */
    SubscriptionsNotSupported,

    /**
     * 상품을 찾지 못했다.
     *
     * 거의 항상 우리 쪽 문제다. Play Console 에 상품이 없거나 비활성이거나, 앱이
     * Play 에서 설치된 것이 아니거나(사이드로드), 서명·패키지가 Console 과 다르다.
     */
    ProductNotFound,

    /** 서버 설정이 없다(가짜 google-services.json). 결제를 시험할 수 없다. */
    ServerNotConfigured,

    /** 그 밖의 오류. */
    Unknown,
}

/** 결제 흐름 한 번의 결과. */
sealed interface PurchaseOutcome {

    /** 구매가 끝났다. 아직 프리미엄이 열린 것은 아니다 — 서버 검증이 남았다. */
    data class Purchased(val purchase: SubscriptionPurchase) : PurchaseOutcome

    /** 결제가 아직 진행 중이다(계좌이체 등). 끝나면 다음에 앱을 열 때 잡힌다. */
    data object Pending : PurchaseOutcome

    /** 사용자가 결제 화면을 닫았다. 오류가 아니므로 빨간 글씨로 적지 않는다. */
    data object Cancelled : PurchaseOutcome

    /** 이미 구독 중이다. 구매 복원으로 처리한다. */
    data object AlreadyOwned : PurchaseOutcome

    /** 실패. [reason] 을 화면에 적는다. */
    data class Failed(val reason: BillingUnavailable, val debugMessage: String?) : PurchaseOutcome
}
