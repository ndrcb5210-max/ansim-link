package io.github.ndrcb5210max.ansimlink.data.billing

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import io.github.ndrcb5210max.ansimlink.domain.billing.EntitlementState
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * 구매 토큰을 서버에 보내 검증을 요청한다.
 *
 * **검증 결과를 믿고 기능을 열지 않는다.** 이 함수가 돌려주는 값은 '서버가 받아서
 * 처리했는지'를 화면에 안내하기 위한 것이고, 프리미엄을 여는 근거는 서버가 쓴
 * `users/{uid}.entitlement` 하나다([PlanProvider]). 응답을 믿고 열면 중간에서 응답을
 * 바꿔치기하는 것으로 프리미엄이 열린다.
 */
interface SubscriptionVerifier {

    /**
     * 서버에 검증을 맡긴다.
     *
     * @param purchaseToken Play 가 준 구매 토큰.
     * @param productId 구독 상품 ID.
     */
    suspend fun verify(purchaseToken: String, productId: String): VerificationResult
}

/** 검증 요청의 결과. */
sealed interface VerificationResult {

    /**
     * 서버가 Play 에 물어보고 `entitlement` 를 썼다.
     *
     * [state] 는 서버가 적은 값이다. 화면이 '확인됐습니다' 같은 안내를 고를 때만 쓴다.
     */
    data class Verified(val state: EntitlementState) : VerificationResult

    /**
     * Play 가 이 구매를 인정하지 않았다(없는 토큰, 다른 앱의 토큰, 환불된 구매).
     *
     * 다시 시도해도 달라지지 않는다. 화면이 안내하고 멈춘다.
     */
    data class Rejected(val message: String?) : VerificationResult

    /**
     * 서버에 닿지 못했거나 서버가 오류를 냈다. **다시 시도할 수 있다.**
     *
     * 함수를 배포하지 않은 상태(이 저장소의 기본값)도 여기로 온다.
     */
    data class Retryable(val message: String?) : VerificationResult
}

/** callable 함수 `verifySubscription` 을 부르는 구현. */
class FunctionsSubscriptionVerifier(
    private val functions: FirebaseFunctions,
) : SubscriptionVerifier {

    override suspend fun verify(purchaseToken: String, productId: String): VerificationResult {
        val payload = mapOf(
            KEY_PURCHASE_TOKEN to purchaseToken,
            KEY_PRODUCT_ID to productId,
        )
        return runCatching {
            functions.getHttpsCallable(FUNCTION_NAME).call(payload).await()
        }.fold(
            onSuccess = { result ->
                val data = result.getData() as? Map<*, *>
                val state = EntitlementState.fromKey(data?.get(KEY_STATE) as? String)
                VerificationResult.Verified(state)
            },
            onFailure = { error ->
                Log.w(TAG, "구독 검증 요청이 실패했다", error)
                error.toVerificationResult()
            },
        )
    }

    private companion object {
        const val TAG = "Billing"
        const val FUNCTION_NAME = "verifySubscription"
        const val KEY_PURCHASE_TOKEN = "purchaseToken"
        const val KEY_PRODUCT_ID = "productId"
        const val KEY_STATE = "state"
    }
}

/**
 * callable 함수의 오류를 '다시 시도할 수 있는가'로 가른다.
 *
 * 이 구분이 화면 동작을 정한다. 다시 시도할 수 있으면 [다시 확인] 버튼을 주고,
 * 아니면 안내만 한다. 모르는 오류는 **다시 시도할 수 있는 쪽**으로 둔다 — 돈을 낸
 * 사람에게 '안 됩니다'로 끝내는 것보다 한 번 더 눌러 보게 하는 쪽이 낫다.
 */
internal fun Throwable.toVerificationResult(): VerificationResult {
    val functionsError = this as? FirebaseFunctionsException
        ?: return VerificationResult.Retryable(message)

    return when (functionsError.code) {
        // 서버가 '이 구매는 인정되지 않는다'고 분명히 말한 경우.
        FirebaseFunctionsException.Code.INVALID_ARGUMENT,
        FirebaseFunctionsException.Code.PERMISSION_DENIED,
        FirebaseFunctionsException.Code.NOT_FOUND,
        FirebaseFunctionsException.Code.FAILED_PRECONDITION,
        -> VerificationResult.Rejected(functionsError.message)

        else -> VerificationResult.Retryable(functionsError.message)
    }
}

/**
 * Play 에 넘길 사용자 식별값을 만든다.
 *
 * uid 를 그대로 넘기지 않는다. 공식 문서가 `obfuscatedAccountId` 에 '사용자를 특정할 수
 * 있는 값을 넣지 말라'고 못박고 있고, 이 값은 Play Console 의 주문 상세와 Developer API
 * 응답에 그대로 보인다. uid 가 보이면 그것으로 Firestore 문서 경로를 알 수 있다.
 *
 * SHA-256 16진수(64자)다. Play 의 상한도 64자라 딱 맞는다. 서버도 같은 방법으로 계산해
 * '이 구매가 이 계정의 것인가'를 맞춰 볼 수 있다(단방향이라 되돌릴 수는 없다).
 */
fun obfuscatedAccountId(uid: String): String {
    if (uid.isBlank()) return ""
    val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}
