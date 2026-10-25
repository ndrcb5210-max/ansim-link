package io.github.ndrcb5210max.ansimlink.domain.group

/**
 * 요금제. 실제 구독 판정은 5단계(결제)에서 서버가 검증한 값을 읽어 정한다.
 * 2단계에서는 항상 [Free] 로 본다.
 */
enum class Plan { Free, Premium }

/**
 * 요금제별 한도. 숫자는 CLAUDE.md '3. 기술 스택과 구조 > 결제'에서 가져왔다.
 *
 * 앱이 혼자 판단해서 기능을 열지 않는다는 원칙이 있으므로, 여기 값은 '막을 때'만 쓴다.
 * 즉 한도를 넘으면 안내하고, 한도를 늘리는 것은 서버가 검증한 구독 상태가 있어야 한다.
 */
object PlanLimits {

    fun maxGroups(plan: Plan): Int = when (plan) {
        Plan.Free -> 1
        Plan.Premium -> 3
    }

    fun maxMembers(plan: Plan): Int = when (plan) {
        Plan.Free -> 3
        Plan.Premium -> 10
    }

    fun maxShareMinutes(plan: Plan): Int = when (plan) {
        Plan.Free -> 60
        Plan.Premium -> 180
    }
}

/** 한도 검사 결과. [Blocked] 면 화면에 이유를 보여 주고 동작을 막는다. */
sealed interface LimitCheck {
    data object Allowed : LimitCheck

    data class Blocked(val reason: LimitReason, val limit: Int) : LimitCheck
}

enum class LimitReason { GroupCount, MemberCount }

object GroupLimits {

    /** 그룹을 하나 더 만들 수 있는지. */
    fun canCreateGroup(plan: Plan, currentGroupCount: Int): LimitCheck {
        val limit = PlanLimits.maxGroups(plan)
        return if (currentGroupCount < limit) {
            LimitCheck.Allowed
        } else {
            LimitCheck.Blocked(LimitReason.GroupCount, limit)
        }
    }

    /**
     * 구성원을 한 명 더 받을 수 있는지.
     *
     * @param currentMemberCount 그룹을 만든 사람을 포함한 지금 구성원 수.
     */
    fun canAddMember(plan: Plan, currentMemberCount: Int): LimitCheck {
        val limit = PlanLimits.maxMembers(plan)
        return if (currentMemberCount < limit) {
            LimitCheck.Allowed
        } else {
            LimitCheck.Blocked(LimitReason.MemberCount, limit)
        }
    }
}
