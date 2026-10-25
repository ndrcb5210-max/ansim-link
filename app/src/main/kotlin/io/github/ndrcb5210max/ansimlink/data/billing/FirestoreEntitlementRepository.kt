package io.github.ndrcb5210max.ansimlink.data.billing

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Entitlements
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Users
import io.github.ndrcb5210max.ansimlink.domain.billing.Entitlement
import io.github.ndrcb5210max.ansimlink.domain.billing.EntitlementState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * `users/{uid}.entitlement` 를 읽는 [EntitlementRepository].
 *
 * 쓰는 코드가 이 파일에 없다. 보안 규칙이 막고 있기도 하지만, 쓸 수 있는 코드를 두지
 * 않는 것 자체가 설계다(인터페이스 주석 참고).
 */
class FirestoreEntitlementRepository(
    private val firestore: FirebaseFirestore,
) : EntitlementRepository {

    override fun observe(uid: String): Flow<Entitlement> = callbackFlow {
        val registration = userRef(uid).addSnapshotListener(
            // 캐시에서 온 값도 받는다. 비행기 모드에서 앱을 열었을 때 이미 받아 둔 구독
            // 상태로 화면을 그릴 수 있다. 캐시 값이 오래되어 기간이 지났으면
            // Entitlement.planAt() 이 무료로 내린다(그쪽이 기간을 보는 또 하나의 이유다).
            MetadataChanges.INCLUDE,
        ) { snapshot, error ->
            if (error != null) {
                // 흐름을 끊지 않는다. 끊으면 화면이 영원히 '불러오는 중'에 머문다.
                Log.w(TAG, "구독 상태를 읽지 못했다", error)
                trySend(Entitlement.none)
                return@addSnapshotListener
            }
            trySend(snapshot.toEntitlement())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun readOnce(uid: String): Entitlement =
        runCatching { userRef(uid).get().await().toEntitlement() }
            .getOrElse { error ->
                // 읽지 못하면 무료로 본다. 막는 방향으로 틀리는 쪽을 고른다.
                Log.w(TAG, "구독 상태를 한 번 읽는 데 실패했다", error)
                Entitlement.none
            }

    private fun userRef(uid: String) = firestore.collection(Users.COLLECTION).document(uid)

    private companion object {
        const val TAG = "Entitlement"
    }
}

/**
 * 문서를 [Entitlement] 로 바꾼다.
 *
 * 값이 없거나 형이 다르면 기본값을 쓴다(다른 매퍼와 같은 방식). 다만 **기본값이 늘
 * '프리미엄이 아닌 쪽'** 이어야 한다. 손상된 문서가 프리미엄을 열어 주면 안 된다.
 */
internal fun DocumentSnapshot?.toEntitlement(): Entitlement {
    if (this == null || !exists()) return Entitlement.none
    val map = get(Users.ENTITLEMENT) as? Map<*, *> ?: return Entitlement.none
    return Entitlement(
        state = EntitlementState.fromKey(map[Entitlements.STATE] as? String),
        productId = (map[Entitlements.PRODUCT_ID] as? String)?.takeIf { it.isNotBlank() },
        expiryTimeMillis = (map[Entitlements.EXPIRY_TIME] as? Number)?.toLong() ?: 0L,
        autoRenewing = map[Entitlements.AUTO_RENEWING] as? Boolean ?: false,
        verifiedAtMillis = (map[Entitlements.VERIFIED_AT] as? Number)?.toLong() ?: 0L,
    )
}
