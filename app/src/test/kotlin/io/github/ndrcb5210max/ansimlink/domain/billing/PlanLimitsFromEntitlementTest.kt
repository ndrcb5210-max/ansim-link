package io.github.ndrcb5210max.ansimlink.domain.billing

import io.github.ndrcb5210max.ansimlink.domain.group.GroupLimits
import io.github.ndrcb5210max.ansimlink.domain.group.LimitCheck
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits
import io.github.ndrcb5210max.ansimlink.domain.message.SafetyMessageSet
import io.github.ndrcb5210max.ansimlink.domain.message.SafetyMessages
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.share.ShareDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서버가 쓴 구독 상태에서 **네 가지 한도가 모두** 바뀌는지 한 번에 본다.
 *
 * 왜 따로 두는가: 한도를 보는 곳이 네 군데다(그룹 개수, 인원, 공유 시간, 유형별 메시지).
 * 각 한도는 자기 테스트가 있지만, '구독 하나로 네 개가 같이 열리고 같이 닫히는지'는
 * 어느 테스트도 보지 않았다. 4단계에서 `Plan.Free` 가 다섯 군데에 흩어져 있던 것을
 * 한 군데로 모았으므로(`PlanProvider`), 그 한 군데가 틀리면 네 개가 같이 틀린다.
 *
 * 여기서는 그 연결만 본다 — 저장소도 Firebase 도 쓰지 않는다.
 */
class PlanLimitsFromEntitlementTest {

    private val now = 1_800_000_000_000L
    private val month = 30L * 24 * 60 * 60 * 1000

    private val premiumMessages = SafetyMessageSet(
        common = "공통 문구",
        byType = mapOf(DisasterType.Earthquake to "지진이야. 책상 아래 있어."),
    )

    /** 활성 구독. 서버가 Play 응답을 옮겨 적은 모양이다. */
    private val active = Entitlement(
        state = EntitlementState.Active,
        productId = SubscriptionCatalog.PREMIUM_MONTHLY,
        expiryTimeMillis = now + month,
        autoRenewing = true,
        verifiedAtMillis = now,
    )

    @Test
    fun `구독이 없으면 네 가지가 모두 무료 한도다`() {
        val plan = Entitlement.none.planAt(now)

        assertEquals(Plan.Free, plan)
        assertEquals(1, PlanLimits.maxGroups(plan))
        assertEquals(3, PlanLimits.maxMembers(plan))
        assertEquals(60, ShareDuration.effectiveMinutes(180, plan))
        // 유형별 문구가 저장돼 있어도 쓰지 않는다.
        assertEquals(
            "공통 문구",
            SafetyMessages.select(premiumMessages, DisasterType.Earthquake, plan),
        )
    }

    @Test
    fun `활성 구독이면 네 가지가 모두 프리미엄 한도다`() {
        val plan = active.planAt(now)

        assertEquals(Plan.Premium, plan)
        assertEquals(3, PlanLimits.maxGroups(plan))
        assertEquals(10, PlanLimits.maxMembers(plan))
        assertEquals(180, ShareDuration.effectiveMinutes(180, plan))
        assertEquals(
            "지진이야. 책상 아래 있어.",
            SafetyMessages.select(premiumMessages, DisasterType.Earthquake, plan),
        )
    }

    @Test
    fun `기간이 끝나면 네 가지가 같이 무료로 돌아온다`() {
        // 서버가 멈춰 state 가 active 로 남아 있어도 기간이 지나면 내려온다.
        val plan = active.planAt(now + month + 1)

        assertEquals(Plan.Free, plan)
        assertEquals(1, PlanLimits.maxGroups(plan))
        assertEquals(3, PlanLimits.maxMembers(plan))
        assertEquals(60, ShareDuration.effectiveMinutes(180, plan))
        assertEquals(
            "공통 문구",
            SafetyMessages.select(premiumMessages, DisasterType.Earthquake, plan),
        )
    }

    @Test
    fun `해지를 눌렀어도 기간 안에는 프리미엄 한도를 쓴다`() {
        val cancelled = active.copy(
            state = EntitlementState.Canceled,
            autoRenewing = false,
        )

        assertEquals(Plan.Premium, cancelled.planAt(now))
        assertEquals(180, ShareDuration.effectiveMinutes(180, cancelled.planAt(now)))
    }

    @Test
    fun `보류 상태에서는 저장된 180분이 60분으로 내려간다`() {
        // 프리미엄일 때 180분을 골라 두었는데 결제가 막힌 경우. 저장된 값을 지우지
        // 않으므로(다시 결제하면 되살아난다) 쓸 때마다 요금제를 봐야 한다.
        val onHold = active.copy(
            state = EntitlementState.OnHold,
            expiryTimeMillis = now - 1,
        )
        val savedMinutes = 180

        assertEquals(60, ShareDuration.effectiveMinutes(savedMinutes, onHold.planAt(now)))
    }

    @Test
    fun `구독이 열리면 그룹을 두 개 더 만들 수 있다`() {
        val freePlan = Entitlement.none.planAt(now)
        val premiumPlan = active.planAt(now)

        // 무료로 하나 만든 상태.
        assertTrue(GroupLimits.canCreateGroup(freePlan, 1) is LimitCheck.Blocked)
        assertEquals(LimitCheck.Allowed, GroupLimits.canCreateGroup(premiumPlan, 1))
        assertEquals(LimitCheck.Allowed, GroupLimits.canCreateGroup(premiumPlan, 2))
        assertTrue(GroupLimits.canCreateGroup(premiumPlan, 3) is LimitCheck.Blocked)
    }

    @Test
    fun `구독이 열리면 구성원을 열 명까지 받는다`() {
        val freePlan = Entitlement.none.planAt(now)
        val premiumPlan = active.planAt(now)

        assertTrue(GroupLimits.canAddMember(freePlan, 3) is LimitCheck.Blocked)
        assertEquals(LimitCheck.Allowed, GroupLimits.canAddMember(premiumPlan, 3))
        assertEquals(LimitCheck.Allowed, GroupLimits.canAddMember(premiumPlan, 9))
        assertTrue(GroupLimits.canAddMember(premiumPlan, 10) is LimitCheck.Blocked)
    }

    @Test
    fun `구독을 해지해 한도가 줄어도 이미 만든 그룹은 건드리지 않는다`() {
        // 프리미엄으로 그룹 3개를 만든 뒤 구독이 끝난 경우. 한도는 '더 만드는 것'만
        // 막는다(PlanLimits 주석). 이미 있는 그룹을 지우거나 잠그지 않는다 —
        // 재난 상황에 쓰는 앱에서 가족 연결을 끊는 것이 가장 나쁘다.
        val expired = active.copy(state = EntitlementState.Expired, expiryTimeMillis = now - 1)
        val plan = expired.planAt(now)

        assertEquals(Plan.Free, plan)
        assertTrue(GroupLimits.canCreateGroup(plan, 3) is LimitCheck.Blocked)
    }
}
