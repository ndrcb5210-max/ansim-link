package io.github.ndrcb5210max.ansimlink.ui.billing

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.data.billing.BillingRepository
import io.github.ndrcb5210max.ansimlink.data.billing.BillingState
import io.github.ndrcb5210max.ansimlink.data.billing.BillingUnavailable
import io.github.ndrcb5210max.ansimlink.data.billing.PlanProvider
import io.github.ndrcb5210max.ansimlink.data.billing.PurchaseOutcome
import io.github.ndrcb5210max.ansimlink.data.billing.SubscriptionPurchase
import io.github.ndrcb5210max.ansimlink.data.billing.SubscriptionVerifier
import io.github.ndrcb5210max.ansimlink.data.billing.VerificationResult
import io.github.ndrcb5210max.ansimlink.data.billing.obfuscatedAccountId
import io.github.ndrcb5210max.ansimlink.domain.billing.Entitlement
import io.github.ndrcb5210max.ansimlink.domain.billing.PlayStoreLinks
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionCatalog
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionOffers
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 결제 화면과 설정 > 구독 화면의 ViewModel.
 *
 * ## 구매 뒤 순서가 핵심이다
 * ```
 * 구매 성공 → 서버 검증(verifySubscription) → 확인(acknowledge) → entitlement 를 기다림
 * ```
 * 이 순서를 지키는 이유는 두 가지다.
 *
 * 1. **확인(acknowledge)을 검증 뒤에 한다.** Play 는 3일 안에 확인하지 않은 구매를
 *    자동으로 환불·취소한다. 확인을 먼저 해 버리면 검증이 실패했을 때 '돈은 빠져나갔고
 *    프리미엄은 안 열렸고 자동 환불도 안 되는' 상태가 된다. 검증을 먼저 하면 최악의
 *    경우에도 Play 가 되돌려 준다.
 * 2. **프리미엄은 entitlement 로만 열린다.** 검증 응답을 믿고 화면을 프리미엄으로
 *    바꾸지 않는다. 서버가 `users/{uid}.entitlement` 를 쓰고, 그것을 Firestore 구독으로
 *    읽어서야 열린다([PlanProvider]).
 *
 * ## 못 처리한 구매를 다시 줍는다
 * 화면을 열 때마다 [BillingRepository.queryPurchases] 로 훑고, 확인되지 않은 구매가
 * 있으면 검증부터 다시 한다. 결제 직후 앱이 죽거나 네트워크가 끊긴 경우가 여기로 온다.
 * 그대로 두면 3일 뒤 Play 가 환불한다.
 */
class BillingViewModel(
    private val authRepository: AuthRepository,
    private val billing: BillingRepository,
    private val verifier: SubscriptionVerifier,
    private val planProvider: PlanProvider,
    private val packageName: String,
    private val serverReady: Boolean,
) : ViewModel() {

    // 'yyyy년 M월 d일' — 갱신일. 날짜만 보여 준다(시각까지는 쓸모가 없고 시간대 때문에
    // 헷갈린다).
    private val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)

    private val notice = MutableStateFlow<PaywallNotice?>(null)
    private val verifying = MutableStateFlow(false)

    val state: StateFlow<PaywallUiState> = combine(
        billing.state,
        planProvider.entitlement,
        planProvider.plan,
        notice,
        verifying,
    ) { billingState, entitlement, plan, currentNotice, isVerifying ->
        toUiState(billingState, entitlement, plan, currentNotice, isVerifying)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PaywallUiState(
            serverReady = serverReady,
            manageUrl = PlayStoreLinks.manageSubscription(null, packageName),
        ),
    )

    init {
        // 결제 흐름의 결과는 PurchasesUpdatedListener 를 거쳐 billing.state 로 온다.
        // 상태 변환(combine) 안에서 처리하면 화면이 다시 구독할 때마다 같은 결과를
        // 또 처리하게 된다. 그래서 여기서 한 번만 구독하고, 값이 **바뀔 때만** 다룬다.
        viewModelScope.launch {
            var handled: PurchaseOutcome? = null
            billing.state.collect { billingState ->
                val outcome = billingState.lastResult ?: return@collect
                if (outcome === handled) return@collect
                handled = outcome
                handleOutcome(outcome)
            }
        }
    }

    /**
     * 화면을 열 때 부른다. 연결·상품 조회 + 못 처리한 구매 줍기.
     *
     * 서버 설정이 없으면 Play 를 건드리지 않는다. 가짜 설정으로 결제를 시험하면
     * 검증 함수를 부를 수 없어서 '결제는 됐는데 프리미엄은 안 열리는' 상태가 된다.
     */
    fun refresh() {
        if (!serverReady) return
        viewModelScope.launch {
            billing.refresh()
            recoverPendingPurchases()
        }
    }

    /** 결제 흐름을 띄운다. */
    fun subscribe(activity: Activity) {
        val current = state.value
        val offerToken = current.offer?.offerToken
        if (offerToken == null || !current.canSubscribe) {
            // 버튼이 꺼져 있어야 하는 상태다. 여기까지 왔다면 화면 상태가 오래된 것이다.
            Log.w(TAG, "결제할 수 없는 상태에서 구독을 눌렀다")
            return
        }
        val uid = authRepository.currentUserNow()?.uid
        viewModelScope.launch {
            notice.value = null
            billing.launchPurchase(
                activity = activity,
                offerToken = offerToken,
                obfuscatedAccountId = uid?.let(::obfuscatedAccountId),
            )
            // 결과는 PurchasesUpdatedListener 로 돌아온다. billing.state 를 보고 있는
            // [handleOutcome] 이 받는다.
        }
    }

    /** 가격을 못 받았을 때의 [다시 시도]. */
    fun retry() = refresh()

    /**
     * [구독 상태 다시 확인]. 검증이 늦어졌을 때 사용자가 직접 누른다.
     *
     * 할 일은 '못 처리한 구매 줍기'와 같다. 다만 이미 확인된 구매도 다시 검증한다 —
     * 검증은 됐는데 서버가 entitlement 를 못 쓴 경우가 있을 수 있다.
     */
    fun recheck() {
        if (!serverReady) return
        viewModelScope.launch {
            verifying.value = true
            try {
                val purchases = billing.queryPurchases()
                if (purchases.isEmpty()) {
                    notice.value = PaywallNotice.VerificationPending
                    return@launch
                }
                purchases.forEach { verifyAndAcknowledge(it, restored = true) }
            } finally {
                verifying.value = false
            }
        }
    }

    fun dismissNotice() {
        notice.value = null
    }

    override fun onCleared() {
        billing.close()
        super.onCleared()
    }

    // ---- 내부 ----

    /** 결제 흐름의 결과를 처리한다. [init] 의 구독이 값이 바뀔 때마다 한 번씩 부른다. */
    private fun handleOutcome(outcome: PurchaseOutcome) {
        when (outcome) {
            is PurchaseOutcome.Purchased -> viewModelScope.launch {
                verifying.value = true
                try {
                    verifyAndAcknowledge(outcome.purchase, restored = false)
                } finally {
                    verifying.value = false
                }
            }

            PurchaseOutcome.Pending -> notice.value = PaywallNotice.PurchasePending

            PurchaseOutcome.Cancelled -> notice.value = PaywallNotice.PurchaseCancelled

            // 이미 구독 중이다. 토큰을 모르므로 조회해서 검증한다.
            PurchaseOutcome.AlreadyOwned -> recheck()

            is PurchaseOutcome.Failed -> {
                Log.w(TAG, "결제 실패: " + outcome.reason + " " + outcome.debugMessage)
                notice.value = PaywallNotice.PurchaseFailed
            }
        }
    }

    /**
     * 확인되지 않은 구매를 다시 검증한다.
     *
     * 이미 확인된(acknowledged) 구매는 건드리지 않는다. 그쪽은 서버가 RTDN 으로
     * 상태를 갱신하고 있고, 화면을 열 때마다 검증 함수를 부르면 Play Developer API
     * 쿼터만 쓴다.
     */
    private suspend fun recoverPendingPurchases() {
        val purchases = billing.queryPurchases()
        val unacknowledged = purchases.filter { !it.acknowledged && !it.pending }
        if (unacknowledged.isEmpty()) return

        Log.i(TAG, "확인되지 않은 구매를 다시 검증한다: " + unacknowledged.size + "건")
        verifying.value = true
        try {
            unacknowledged.forEach { verifyAndAcknowledge(it, restored = true) }
        } finally {
            verifying.value = false
        }
    }

    /**
     * 검증 → 확인 → entitlement 기다리기.
     *
     * @param restored 사용자가 방금 결제한 것이 아니라 복원된 구매인지. 안내 문구가 달라진다.
     */
    private suspend fun verifyAndAcknowledge(purchase: SubscriptionPurchase, restored: Boolean) {
        val productId = purchase.productIds.firstOrNull { it in SubscriptionCatalog.PREMIUM_PRODUCT_IDS }
            ?: purchase.productIds.firstOrNull()
            ?: SubscriptionCatalog.PREMIUM_MONTHLY

        when (val result = verifier.verify(purchase.purchaseToken, productId)) {
            is VerificationResult.Verified -> {
                // **검증 뒤에** 확인한다(클래스 주석 참고).
                if (!purchase.acknowledged) {
                    val acknowledged = billing.acknowledge(purchase.purchaseToken)
                    if (!acknowledged) {
                        // 다음에 화면을 열 때 다시 잡는다. 3일 안에만 하면 된다.
                        Log.w(TAG, "구매를 확인하지 못했다. 다음에 다시 시도한다")
                    }
                }
                awaitEntitlement(restored, result.state.grantsPremium)
            }

            is VerificationResult.Rejected -> {
                Log.w(TAG, "Play 가 이 구매를 인정하지 않았다: " + result.message)
                notice.value = PaywallNotice.PurchaseRejected
            }

            is VerificationResult.Retryable -> {
                Log.w(TAG, "검증을 다시 시도해야 한다: " + result.message)
                notice.value = PaywallNotice.VerificationPending
            }
        }
    }

    /**
     * 서버가 `entitlement` 를 쓸 때까지 잠깐 기다린다.
     *
     * 기다리지 않으면 결제가 끝난 직후 화면이 '무료'를 그대로 보여 주고, 사용자는
     * 결제가 실패한 줄 안다. 시간이 다 돼도 프리미엄이 아니면 **열어 주지 않고**
     * '확인이 늦어지고 있다'고 적는다.
     */
    private suspend fun awaitEntitlement(restored: Boolean, serverSaysPremium: Boolean) {
        val entitlement = planProvider.awaitPremium(ENTITLEMENT_WAIT_MILLIS)
        notice.value = when {
            entitlement.planAt(System.currentTimeMillis()) == Plan.Premium ->
                if (restored) PaywallNotice.PurchaseRestored else PaywallNotice.PremiumActivated

            // 서버는 '접근을 주는 상태'라고 했는데 문서가 아직 안 보인다.
            // Firestore 구독이 늦었을 뿐일 수 있다.
            serverSaysPremium -> PaywallNotice.VerificationPending

            // 서버가 '프리미엄이 아니다'라고 했다(보류·일시정지·만료).
            else -> PaywallNotice.PurchaseRejected
        }
    }

    private fun toUiState(
        billingState: BillingState,
        entitlement: Entitlement,
        plan: Plan,
        currentNotice: PaywallNotice?,
        isVerifying: Boolean,
    ): PaywallUiState {
        val productId = entitlement.productId
            ?: billingState.offers.firstOrNull()?.productId
        return PaywallUiState(
            loading = billingState.connecting,
            serverReady = serverReady,
            plan = plan,
            entitlement = entitlement,
            offer = SubscriptionOffers.presentBest(billingState.offers),
            unavailable = when {
                !serverReady -> BillingUnavailable.ServerNotConfigured
                else -> billingState.unavailable
            },
            purchaseInProgress = billingState.purchaseInProgress,
            verifying = isVerifying,
            manageUrl = PlayStoreLinks.manageSubscription(productId, packageName),
            renewalDateText = entitlement.expiryTimeMillis
                .takeIf { it > 0L }
                ?.let { dateFormat.format(Date(it)) },
            verifiedAtText = entitlement.verifiedAtMillis
                .takeIf { it > 0L }
                ?.let { dateFormat.format(Date(it)) },
            notice = currentNotice,
        )
    }

    private companion object {
        const val TAG = "Billing"
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * 서버가 entitlement 를 쓰기를 기다리는 시간.
         *
         * callable 함수가 Play Developer API 를 한 번 부르고 Firestore 에 쓴 다음,
         * 그 쓰기가 구독으로 돌아오기까지를 덮을 만한 길이로 잡았다. 콜드 스타트가
         * 섞이면 더 걸릴 수 있어서 넉넉하게 둔다. 이 시간이 지나도 화면은 열리지
         * 않고 '확인이 늦어지고 있다 + [다시 확인]' 으로 간다.
         */
        const val ENTITLEMENT_WAIT_MILLIS = 15_000L
    }
}
