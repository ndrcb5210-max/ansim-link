package io.github.ndrcb5210max.ansimlink.domain.billing

import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 엔타이틀먼트 → 요금제 판정.
 *
 * 이 판정이 틀리면 둘 중 하나가 일어난다. 돈을 낸 사람이 프리미엄을 못 쓰거나,
 * 안 낸 사람이 쓴다. 상태별로 하나씩 못박아 둔다.
 */
class EntitlementTest {

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun entitlement(
        state: EntitlementState,
        expiryOffsetMillis: Long = 24 * hour,
        autoRenewing: Boolean = true,
        productId: String? = SubscriptionCatalog.PREMIUM_MONTHLY,
    ) = Entitlement(
        state = state,
        productId = productId,
        expiryTimeMillis = now + expiryOffsetMillis,
        autoRenewing = autoRenewing,
        verifiedAtMillis = now - hour,
    )

    @Test
    fun `구독 기록이 없으면 무료다`() {
        assertEquals(Plan.Free, Entitlement.none.planAt(now))
    }

    @Test
    fun `활성 구독은 프리미엄이다`() {
        assertEquals(Plan.Premium, entitlement(EntitlementState.Active).planAt(now))
    }

    @Test
    fun `해지했지만 기간이 남았으면 프리미엄이다`() {
        // 돈을 낸 기간이다. 해지를 눌렀다고 바로 끊으면 안 된다.
        val cancelled = entitlement(EntitlementState.Canceled, autoRenewing = false)

        assertEquals(Plan.Premium, cancelled.planAt(now))
        assertTrue(cancelled.endsAfterCurrentPeriod(now))
    }

    @Test
    fun `유예 기간은 프리미엄이다`() {
        // 결제 수단을 고칠 시간을 준다(Play 구독 수명 주기 문서).
        // Play 가 expiryTime 을 늘려 주므로 기간 검사도 통과한다.
        val grace = entitlement(EntitlementState.InGracePeriod)

        assertEquals(Plan.Premium, grace.planAt(now))
        assertTrue(grace.needsPaymentFix)
    }

    @Test
    fun `보류는 바로 막는다`() {
        // 유예 기간이 지나도 결제가 안 된 상태다. 이때 expiryTime 은 과거로 온다.
        val onHold = entitlement(EntitlementState.OnHold, expiryOffsetMillis = -hour)

        assertEquals(Plan.Free, onHold.planAt(now))
        assertTrue(onHold.needsPaymentFix)
    }

    @Test
    fun `일시정지 중에는 프리미엄이 아니다`() {
        // 사용자가 Play 에서 일시정지했다. 돈을 내지 않고 쓰지도 못한다.
        // 기간이 남아 있어도 막아야 한다.
        assertEquals(Plan.Free, entitlement(EntitlementState.Paused).planAt(now))
    }

    @Test
    fun `결제가 끝나지 않은 가입은 프리미엄이 아니다`() {
        assertEquals(Plan.Free, entitlement(EntitlementState.Pending).planAt(now))
    }

    @Test
    fun `만료와 회수는 프리미엄이 아니다`() {
        assertEquals(Plan.Free, entitlement(EntitlementState.Expired).planAt(now))
        assertEquals(Plan.Free, entitlement(EntitlementState.Revoked).planAt(now))
    }

    @Test
    fun `상태가 활성이어도 기간이 지났으면 무료로 돌아간다`() {
        // 서버가 멈추면(배포 안 함·RTDN 안 옴·함수 죽음) 상태 값이 영원히 active 로 남는다.
        // 그때 저절로 무료로 내려와야 한다. 이 테스트가 그 안전망이다.
        val stale = entitlement(EntitlementState.Active, expiryOffsetMillis = -1L)

        assertEquals(Plan.Free, stale.planAt(now))
    }

    @Test
    fun `끝 시각에 닿으면 끝난 것으로 본다`() {
        val exact = Entitlement(
            state = EntitlementState.Active,
            productId = SubscriptionCatalog.PREMIUM_MONTHLY,
            expiryTimeMillis = now,
            autoRenewing = true,
        )

        assertEquals(Plan.Free, exact.planAt(now))
        assertEquals(Plan.Premium, exact.planAt(now - 1))
    }

    @Test
    fun `모르는 상품 ID 로는 프리미엄이 열리지 않는다`() {
        val unknownProduct = entitlement(EntitlementState.Active, productId = "premium_yearly")

        assertEquals(Plan.Free, unknownProduct.planAt(now))
    }

    @Test
    fun `상품 ID 가 없으면 프리미엄이 열리지 않는다`() {
        assertEquals(Plan.Free, entitlement(EntitlementState.Active, productId = null).planAt(now))
    }

    @Test
    fun `모르는 상태 값은 프리미엄을 주지 않는다`() {
        // Play 에 새 상태가 생겼을 때 '모르니까 열어 준다'로 동작하면 안 된다.
        assertEquals(EntitlementState.Unknown, EntitlementState.fromKey("subscription_state_新"))
        assertEquals(EntitlementState.Unknown, EntitlementState.fromKey(null))
        assertEquals(EntitlementState.Unknown, EntitlementState.fromKey(""))
        assertFalse(EntitlementState.Unknown.grantsPremium)
        assertEquals(Plan.Free, entitlement(EntitlementState.Unknown).planAt(now))
    }

    @Test
    fun `상태 키는 대소문자와 공백을 가린다`() {
        assertEquals(EntitlementState.Active, EntitlementState.fromKey(" ACTIVE "))
        assertEquals(EntitlementState.InGracePeriod, EntitlementState.fromKey("In_Grace_Period"))
    }

    @Test
    fun `자동 갱신 중이면 해지 예약 문구를 띄우지 않는다`() {
        assertFalse(entitlement(EntitlementState.Active).endsAfterCurrentPeriod(now))
    }

    @Test
    fun `프리미엄이 아니면 해지 예약 문구도 띄우지 않는다`() {
        // 이미 끝난 구독에 '기간이 끝나면 멈춥니다'를 적으면 아직 쓸 수 있다는 뜻이 된다.
        val expired = entitlement(
            EntitlementState.Expired,
            expiryOffsetMillis = -hour,
            autoRenewing = false,
        )

        assertFalse(expired.endsAfterCurrentPeriod(now))
    }
}
