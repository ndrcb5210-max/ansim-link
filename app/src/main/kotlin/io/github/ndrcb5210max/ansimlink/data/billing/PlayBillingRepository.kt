package io.github.ndrcb5210max.ansimlink.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.ndrcb5210max.ansimlink.domain.billing.BillingPeriod
import io.github.ndrcb5210max.ansimlink.domain.billing.PhaseRecurrence
import io.github.ndrcb5210max.ansimlink.domain.billing.PricingPhase
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionCatalog
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionOffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Google Play Billing Library 9 를 쓰는 [BillingRepository].
 *
 * ## 콜백을 코루틴으로 바꾸는 방법
 * `billing-ktx` 를 쓰지 않고 [CompletableDeferred] 로 직접 감쌌다. 이유는 두 가지다.
 * 1. `billing-ktx` 의 kotlin_module 메타데이터가 2.3.0 인데 그것이 Kotlin 2.2 컴파일러의
 *    정확한 상한이다(4단계 play-services-location 과 같은 자리). 다음 버전에서 깨질
 *    의존을 하나 더 두고 싶지 않았다.
 * 2. ktx 가 해 주는 것은 콜백 → suspend 변환뿐이고, 정작 까다로운 **연결 수명**은
 *    그대로 남는다. 어차피 여기서 다뤄야 한다.
 *
 * ## 연결
 * `enableAutoServiceReconnection()` 을 켠다(Billing 8 에서 들어왔다). 그러면 서비스가
 * 끊겼을 때 라이브러리가 알아서 다시 붙으므로 `onBillingServiceDisconnected` 에서
 * 직접 `startConnection` 을 다시 부르는 코드(와 그 재시도 백오프)가 필요 없다.
 *
 * [connect] 를 [Mutex] 로 감싼 이유: 화면 두 개가 동시에 [refresh] 를 부르면
 * `startConnection` 이 두 번 불려 `DEVELOPER_ERROR` 가 난다.
 *
 * ## 프리미엄을 열지 않는다
 * [PurchasesUpdatedListener][com.android.billingclient.api.PurchasesUpdatedListener] 가
 * 구매를 받아도 여기서는 [BillingState.lastResult] 에 담기만 한다. 확인(acknowledge)도
 * 서버 검증이 끝난 뒤에 따로 불러야 한다([acknowledge]). 이 클래스는 요금제를 모른다.
 */
class PlayBillingRepository(
    context: Context,
    private val productIds: List<String> = listOf(SubscriptionCatalog.PREMIUM_MONTHLY),
) : BillingRepository {

    private val applicationContext = context.applicationContext

    private val _state = MutableStateFlow(BillingState())
    override val state: Flow<BillingState> = _state.asStateFlow()

    private val connectMutex = Mutex()

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(applicationContext)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            // 결제가 바로 끝나지 않는 수단(계좌이체 등)을 쓰는 사용자가 있다.
            // 이것을 켜지 않으면 그 구매가 아예 전달되지 않아 '결제했는데 아무 일도
            // 일어나지 않는' 상태가 된다. 선불 요금제도 같이 켠다.
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build(),
            )
            .enableAutoServiceReconnection()
            .build()
    }

    override suspend fun refresh() {
        _state.update { it.copy(connecting = true) }
        val connected = connect()
        if (connected != null) {
            _state.update { it.copy(connecting = false, offers = emptyList(), unavailable = connected) }
            return
        }
        queryOffers()
    }

    /**
     * 연결한다. 성공하면 null, 실패하면 이유를 돌려준다.
     *
     * 이미 연결돼 있으면 아무것도 하지 않는다. `startConnection` 은 연결된 클라이언트에
     * 다시 부르면 `DEVELOPER_ERROR` 를 돌려준다.
     */
    private suspend fun connect(): BillingUnavailable? = connectMutex.withLock {
        if (client.isReady) return@withLock null

        val deferred = CompletableDeferred<BillingResult>()
        runCatching {
            client.startConnection(
                object : com.android.billingclient.api.BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        // 여러 번 불릴 수 있다고 보고 complete 결과를 무시한다
                        // (이미 완료된 Deferred 에 complete 를 부르면 false 를 돌려준다).
                        deferred.complete(result)
                    }

                    override fun onBillingServiceDisconnected() {
                        // enableAutoServiceReconnection 이 다시 붙여 준다. 여기서는
                        // 연결을 기다리는 쪽이 영원히 매달리지 않게만 한다.
                        deferred.complete(
                            BillingResult.newBuilder()
                                .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                                .setDebugMessage("연결이 끊겼다")
                                .build(),
                        )
                    }
                },
            )
        }.onFailure { error ->
            Log.w(TAG, "결제 서비스에 연결하지 못했다", error)
            return@withLock BillingUnavailable.PlayStoreUnavailable
        }

        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { deferred.await() }
            ?: return@withLock BillingUnavailable.NetworkError

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "연결 실패: " + result.responseCode + " " + result.debugMessage)
            return@withLock result.toUnavailable()
        }

        // 구독을 지원하지 않는 기기·Play 버전이 있다. 지원하지 않으면 결제 화면에
        // 가격을 보여 줄 이유가 없다.
        val subscriptions = client.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        if (subscriptions.responseCode != BillingClient.BillingResponseCode.OK) {
            return@withLock BillingUnavailable.SubscriptionsNotSupported
        }

        null
    }

    /** 상품을 조회해 [BillingState.offers] 를 채운다. */
    private suspend fun queryOffers() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { id ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()

        val deferred = CompletableDeferred<Pair<BillingResult, List<ProductDetails>>>()
        client.queryProductDetailsAsync(params) { result, queryResult ->
            // Billing 8 부터 리스너의 둘째 인자가 QueryProductDetailsResult 다
            // (전에는 List<ProductDetails>). 받아 오지 못한 상품도 같이 알려 준다.
            val unfetched = queryResult.unfetchedProductList
            if (unfetched.isNotEmpty()) {
                Log.w(TAG, "받아 오지 못한 상품이 있다: " + unfetched.size + "개")
            }
            deferred.complete(result to queryResult.productDetailsList)
        }

        val (result, products) = withTimeoutOrNull(QUERY_TIMEOUT_MILLIS) { deferred.await() }
            ?: run {
                _state.update {
                    it.copy(connecting = false, unavailable = BillingUnavailable.NetworkError)
                }
                return
            }

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "상품 조회 실패: " + result.responseCode + " " + result.debugMessage)
            _state.update {
                it.copy(connecting = false, offers = emptyList(), unavailable = result.toUnavailable())
            }
            return
        }

        val offers = products.flatMap { it.toSubscriptionOffers() }
        _state.update {
            it.copy(
                connecting = false,
                offers = offers,
                // 응답은 성공인데 상품이 비어 있으면 Play Console 쪽 문제다.
                // 가장 흔한 원인은 상품 ID 오타, 비활성 상품, 사이드로드한 빌드다.
                unavailable = if (offers.isEmpty()) BillingUnavailable.ProductNotFound else null,
            )
        }
    }

    override suspend fun launchPurchase(
        activity: Activity,
        offerToken: String,
        obfuscatedAccountId: String?,
    ): Boolean {
        if (offerToken.isBlank()) {
            // Billing 8 부터 빈 offerToken 은 예외를 던진다. 미리 막는다.
            Log.w(TAG, "혜택 토큰이 비어 있어 결제를 띄우지 않는다")
            return false
        }
        val details = _state.value.offers
            .firstOrNull { it.offerToken == offerToken }
            ?.productId
            ?.let { productId -> productDetailsCache[productId] }
            ?: run {
                Log.w(TAG, "상품 정보가 없어 결제를 띄우지 않는다")
                return false
            }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .apply {
                // Play 에 넘기는 '우리 쪽 사용자 식별값'. 서버가 검증할 때 '이 구매가
                // 이 계정의 것인가'를 맞춰 볼 수 있다.
                //
                // uid 를 그대로 넘기지 않는다. 공식 문서가 '사용자를 특정할 수 있는
                // 값을 넣지 말라'고 못박고 있고, 이 값은 Play Console 주문 상세와
                // Developer API 응답에 그대로 보인다.
                if (!obfuscatedAccountId.isNullOrBlank()) {
                    setObfuscatedAccountId(obfuscatedAccountId)
                }
            }
            .build()

        _state.update { it.copy(purchaseInProgress = true, lastResult = null) }

        // launchBillingFlow 는 메인 스레드에서 불러야 한다(Activity 를 띄운다).
        val result = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, flowParams) }
        if (result.responseCode == BillingClient.BillingResponseCode.OK) return true

        Log.w(TAG, "결제 흐름을 띄우지 못했다: " + result.responseCode + " " + result.debugMessage)
        _state.update {
            it.copy(
                purchaseInProgress = false,
                lastResult = result.toOutcome(purchases = null),
            )
        }
        return false
    }

    override suspend fun queryPurchases(): List<SubscriptionPurchase> {
        if (connect() != null) return emptyList()

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val deferred = CompletableDeferred<Pair<BillingResult, List<Purchase>>>()
        client.queryPurchasesAsync(params) { result, purchases ->
            deferred.complete(result to purchases)
        }

        val (result, purchases) = withTimeoutOrNull(QUERY_TIMEOUT_MILLIS) { deferred.await() }
            ?: return emptyList()

        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "구매 조회 실패: " + result.responseCode + " " + result.debugMessage)
            return emptyList()
        }
        return purchases.map { it.toSubscriptionPurchase() }
    }

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        if (purchaseToken.isBlank()) return false
        if (connect() != null) return false

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        val deferred = CompletableDeferred<BillingResult>()
        client.acknowledgePurchase(params) { deferred.complete(it) }

        val result = withTimeoutOrNull(QUERY_TIMEOUT_MILLIS) { deferred.await() } ?: return false
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            // 실패하면 다음에 결제 화면을 열 때 queryPurchases 가 다시 잡아 준다.
            // 3일 안에 확인하지 못하면 Play 가 환불·취소한다.
            Log.w(TAG, "구매 확인 실패: " + result.responseCode + " " + result.debugMessage)
            return false
        }
        return true
    }

    override fun close() {
        runCatching { client.endConnection() }
    }

    // ---- 내부 ----

    /**
     * 결제에 넘길 [ProductDetails] 를 들고 있는다.
     *
     * 도메인 모델([SubscriptionOffer])에는 Billing 타입을 담지 않기로 했는데, 결제 흐름을
     * 띄울 때는 그 객체가 필요하다. 그래서 상품 ID → [ProductDetails] 를 여기에 따로 둔다.
     */
    private val productDetailsCache = mutableMapOf<String, ProductDetails>()

    private fun ProductDetails.toSubscriptionOffers(): List<SubscriptionOffer> {
        productDetailsCache[productId] = this
        return subscriptionOfferDetails.orEmpty().map { offer ->
            SubscriptionOffer(
                productId = productId,
                basePlanId = offer.basePlanId,
                offerId = offer.offerId,
                offerToken = offer.offerToken,
                phases = offer.pricingPhases.pricingPhaseList.map { phase ->
                    PricingPhase(
                        formattedPrice = phase.formattedPrice,
                        priceAmountMicros = phase.priceAmountMicros,
                        billingPeriod = BillingPeriod.parse(phase.billingPeriod),
                        billingCycleCount = phase.billingCycleCount,
                        recurrence = phase.recurrenceMode.toPhaseRecurrence(),
                    )
                },
            )
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        _state.update {
            it.copy(purchaseInProgress = false, lastResult = result.toOutcome(purchases))
        }
    }

    private companion object {
        const val TAG = "Billing"

        /**
         * 연결·조회를 기다리는 한계.
         *
         * 왜 시간 제한을 두는가: 콜백이 아예 오지 않는 경우가 있다(Play 스토어 프로세스가
         * 죽는 등). 그러면 결제 화면이 영원히 '불러오는 중'에 머문다. 끝나지 않는 것보다
         * '지금은 가격을 받을 수 없다'고 적고 다시 시도 버튼을 주는 쪽이 낫다.
         */
        const val CONNECT_TIMEOUT_MILLIS = 20_000L
        const val QUERY_TIMEOUT_MILLIS = 20_000L
    }
}

/** Play 의 되풀이 방식 상수를 도메인 enum 으로. 모르는 값은 한 번만으로 본다. */
private fun Int.toPhaseRecurrence(): PhaseRecurrence = when (this) {
    ProductDetails.RecurrenceMode.INFINITE_RECURRING -> PhaseRecurrence.Infinite
    ProductDetails.RecurrenceMode.FINITE_RECURRING -> PhaseRecurrence.Finite
    else -> PhaseRecurrence.NonRecurring
}

internal fun Purchase.toSubscriptionPurchase(): SubscriptionPurchase = SubscriptionPurchase(
    purchaseToken = purchaseToken,
    productIds = products,
    acknowledged = isAcknowledged,
    pending = purchaseState != Purchase.PurchaseState.PURCHASED,
    orderId = orderId,
)

/**
 * 응답 코드를 '사용자에게 할 수 있는 말'로 바꾼다.
 *
 * 코드를 그대로 화면에 적지 않는다. `BILLING_UNAVAILABLE(3)` 을 보여 줘도 사용자가
 * 할 수 있는 일이 없다.
 */
internal fun BillingResult.toUnavailable(): BillingUnavailable = when (responseCode) {
    BillingClient.BillingResponseCode.NETWORK_ERROR,
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
    -> BillingUnavailable.NetworkError

    // Billing 9 부터 '플레이 스토어가 막혀 있다'가 ERROR 대신 이 코드로 온다.
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
    -> BillingUnavailable.PlayStoreUnavailable

    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
    -> BillingUnavailable.SubscriptionsNotSupported

    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
    // DEVELOPER_ERROR 는 거의 항상 상품 ID·상품 설정 문제다.
    BillingClient.BillingResponseCode.DEVELOPER_ERROR,
    -> BillingUnavailable.ProductNotFound

    else -> BillingUnavailable.Unknown
}

/** 결제 흐름 한 번의 결과로 바꾼다. */
internal fun BillingResult.toOutcome(purchases: List<Purchase>?): PurchaseOutcome =
    when (responseCode) {
        BillingClient.BillingResponseCode.OK -> {
            val purchase = purchases.orEmpty().firstOrNull()
            when {
                purchase == null -> PurchaseOutcome.Failed(BillingUnavailable.Unknown, debugMessage)
                purchase.purchaseState == Purchase.PurchaseState.PENDING -> PurchaseOutcome.Pending
                else -> PurchaseOutcome.Purchased(purchase.toSubscriptionPurchase())
            }
        }

        // 사용자가 닫은 것은 오류가 아니다. 빨간 글씨로 적으면 겁을 준다.
        BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled

        BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseOutcome.AlreadyOwned

        else -> PurchaseOutcome.Failed(toUnavailable(), debugMessage)
    }
