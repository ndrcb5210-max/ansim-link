package io.github.ndrcb5210max.ansimlink.data.billing

import io.github.ndrcb5210max.ansimlink.domain.billing.Entitlement
import kotlinx.coroutines.flow.Flow

/**
 * 서버가 검증한 구독 상태를 읽는다. **쓰는 함수가 없다.**
 *
 * 쓰기를 뺀 것은 실수가 아니다. 이 값은 `users/{uid}.entitlement` 에 있고 보안 규칙이
 * 클라이언트 쓰기를 막는다. 인터페이스에 쓰기 함수를 두면 '왜 안 되지'를 런타임에
 * 알게 되므로, 아예 없는 쪽이 낫다(4단계에서 [io.github.ndrcb5210max.ansimlink.data.share.ShareRepository]
 * 에 FCM 발송 함수를 두지 않은 것과 같은 생각이다).
 *
 * 검증 요청은 [SubscriptionVerifier] 가 한다(callable 함수 호출).
 */
interface EntitlementRepository {

    /**
     * 내 구독 상태. 서버가 문서를 고치면 바로 다시 흘린다.
     *
     * 구독 기록이 없으면([Entitlement.none]) 무료다. 읽기에 실패해도 무료를 흘린다 —
     * 흐름을 끊으면 화면이 영원히 '불러오는 중'에 머문다.
     */
    fun observe(uid: String): Flow<Entitlement>

    /**
     * 지금 값을 한 번 읽는다.
     *
     * 화면이 아닌 곳(포그라운드 서비스, WorkManager 작업)에서 요금제를 정할 때 쓴다.
     * 그쪽은 흐름을 구독할 수명이 없다.
     */
    suspend fun readOnce(uid: String): Entitlement
}
