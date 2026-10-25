package io.github.ndrcb5210max.ansimlink.data

import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Entitlements
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Users
import io.github.ndrcb5210max.ansimlink.domain.billing.EntitlementState
import io.github.ndrcb5210max.ansimlink.domain.billing.SubscriptionCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 구독 상태의 필드 이름과 상태 키를 글자 그대로 못박는다.
 *
 * ## 왜 이런 테스트를 쓰나
 * 같은 이름을 **세 군데**가 쓴다.
 * - 앱: `FirestoreSchema.Entitlements`
 * - 서버: `functions/src/schema.ts` 의 `EntitlementFields` · `EntitlementStates`
 * - 규칙: `firestore.rules` 의 `entitlement`
 *
 * 한쪽만 고치면 **조용히 틀린다.** 앱은 모르는 키를 `unknown` 으로 읽고 프리미엄을
 * 주지 않는다(안전한 방향이라 예외도 로그도 나지 않는다). 돈을 낸 사람이 프리미엄을
 * 못 쓰는데 아무도 모르는 상태가 된다.
 *
 * 이 테스트는 상수를 상수와 비교하는 것처럼 보이지만, 하는 일은 '앱에서 이름을 바꾸면
 * 빨간불이 켜져서 서버와 규칙도 같이 고치게 만드는' 것이다. 그래서 기대값을 다른
 * 상수로 쓰지 않고 **문자열을 그대로 적는다.**
 */
class EntitlementSchemaTest {

    @Test
    fun `구독 상태는 users 문서의 entitlement 필드에 있다`() {
        assertEquals("users", Users.COLLECTION)
        assertEquals("entitlement", Users.ENTITLEMENT)
    }

    @Test
    fun `하위 필드 이름이 서버와 같다`() {
        // functions/src/schema.ts 의 EntitlementFields 와 같아야 한다.
        assertEquals("plan", Entitlements.PLAN)
        assertEquals("state", Entitlements.STATE)
        assertEquals("productId", Entitlements.PRODUCT_ID)
        assertEquals("expiryTime", Entitlements.EXPIRY_TIME)
        assertEquals("autoRenewing", Entitlements.AUTO_RENEWING)
        assertEquals("verifiedAt", Entitlements.VERIFIED_AT)
    }

    @Test
    fun `상태 키가 서버와 같다`() {
        // functions/src/schema.ts 의 EntitlementStates 와 같아야 한다.
        assertEquals("none", EntitlementState.None.key)
        assertEquals("pending", EntitlementState.Pending.key)
        assertEquals("active", EntitlementState.Active.key)
        assertEquals("canceled", EntitlementState.Canceled.key)
        assertEquals("in_grace_period", EntitlementState.InGracePeriod.key)
        assertEquals("on_hold", EntitlementState.OnHold.key)
        assertEquals("paused", EntitlementState.Paused.key)
        assertEquals("expired", EntitlementState.Expired.key)
        assertEquals("revoked", EntitlementState.Revoked.key)
        assertEquals("unknown", EntitlementState.Unknown.key)
    }

    @Test
    fun `요금제 이름이 서버와 같다`() {
        assertEquals("free", Entitlements.PLAN_FREE)
        assertEquals("premium", Entitlements.PLAN_PREMIUM)
    }

    @Test
    fun `구독 상품 ID 가 Play Console 과 같다`() {
        // Play Console 의 구독 상품 ID 와 글자 하나까지 같아야 한다. 다르면
        // queryProductDetailsAsync 가 빈 목록을 돌려주고 결제 버튼이 꺼진다.
        assertEquals("premium_monthly", SubscriptionCatalog.PREMIUM_MONTHLY)
    }

    @Test
    fun `상태 키가 서로 겹치지 않는다`() {
        val keys = EntitlementState.entries.map { it.key }

        assertEquals(keys.size, keys.distinct().size)
    }
}
