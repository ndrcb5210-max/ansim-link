package io.github.ndrcb5210max.ansimlink.domain.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 무료·프리미엄 한도. 숫자는 CLAUDE.md 에서 가져왔다. */
class PlanLimitsTest {

    @Test
    fun `무료 한도는 그룹 1개 구성원 3명 공유 60분이다`() {
        assertEquals(1, PlanLimits.maxGroups(Plan.Free))
        assertEquals(3, PlanLimits.maxMembers(Plan.Free))
        assertEquals(60, PlanLimits.maxShareMinutes(Plan.Free))
    }

    @Test
    fun `프리미엄 한도는 그룹 3개 구성원 10명 공유 180분이다`() {
        assertEquals(3, PlanLimits.maxGroups(Plan.Premium))
        assertEquals(10, PlanLimits.maxMembers(Plan.Premium))
        assertEquals(180, PlanLimits.maxShareMinutes(Plan.Premium))
    }

    @Test
    fun `무료는 그룹이 없을 때만 새로 만들 수 있다`() {
        assertEquals(LimitCheck.Allowed, GroupLimits.canCreateGroup(Plan.Free, 0))

        val blocked = GroupLimits.canCreateGroup(Plan.Free, 1)
        assertTrue(blocked is LimitCheck.Blocked)
        assertEquals(LimitReason.GroupCount, (blocked as LimitCheck.Blocked).reason)
        assertEquals(1, blocked.limit)
    }

    @Test
    fun `프리미엄은 그룹을 세 개까지 만든다`() {
        assertEquals(LimitCheck.Allowed, GroupLimits.canCreateGroup(Plan.Premium, 2))
        assertTrue(GroupLimits.canCreateGroup(Plan.Premium, 3) is LimitCheck.Blocked)
    }

    @Test
    fun `무료는 구성원 세 명까지 받는다`() {
        // 그룹을 만든 사람을 포함한 수다.
        assertEquals(LimitCheck.Allowed, GroupLimits.canAddMember(Plan.Free, 1))
        assertEquals(LimitCheck.Allowed, GroupLimits.canAddMember(Plan.Free, 2))

        val blocked = GroupLimits.canAddMember(Plan.Free, 3)
        assertTrue(blocked is LimitCheck.Blocked)
        assertEquals(LimitReason.MemberCount, (blocked as LimitCheck.Blocked).reason)
        assertEquals(3, blocked.limit)
    }

    @Test
    fun `한도를 이미 넘긴 상태에서도 막는다`() {
        assertTrue(GroupLimits.canCreateGroup(Plan.Free, 5) is LimitCheck.Blocked)
        assertTrue(GroupLimits.canAddMember(Plan.Free, 9) is LimitCheck.Blocked)
    }

    @Test
    fun `프리미엄 한도가 무료보다 느슨하다`() {
        // 5단계에서 서버 검증 값을 붙일 때 두 값이 뒤집히면 안 된다.
        assertTrue(PlanLimits.maxGroups(Plan.Premium) > PlanLimits.maxGroups(Plan.Free))
        assertTrue(PlanLimits.maxMembers(Plan.Premium) > PlanLimits.maxMembers(Plan.Free))
        assertTrue(
            PlanLimits.maxShareMinutes(Plan.Premium) > PlanLimits.maxShareMinutes(Plan.Free),
        )
    }
}
