package io.github.ndrcb5210max.ansimlink.ui.group

import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.InviteLookup
import io.github.ndrcb5210max.ansimlink.domain.group.GroupLimits
import io.github.ndrcb5210max.ansimlink.domain.group.LimitCheck
import io.github.ndrcb5210max.ansimlink.domain.group.Plan

/** 그룹 탭 전체 상태. */
data class GroupUiState(
    val loading: Boolean = true,
    val serverReady: Boolean = true,
    val myUid: String = "",
    val groups: List<GroupWithMembers> = emptyList(),
    val error: AppError? = null,
    /** 방금 만든 초대 코드. 만들면 화면에 띄워 준다. */
    val freshInvite: InviteLookup? = null,
    val busy: Boolean = false,
    /**
     * 지금 요금제. 서버가 검증한 구독 상태에서 나온다
     * ([io.github.ndrcb5210max.ansimlink.data.billing.PlanProvider]).
     *
     * 기본값을 [Plan.Free] 로 둔 것은 '모를 때는 막는다'를 뜻한다. 구독 상태를 아직
     * 못 읽었을 때 프리미엄으로 보면 그 사이에 그룹을 더 만들 수 있다.
     */
    val plan: Plan = Plan.Free,
) {
    val canCreateGroup: Boolean
        get() = !busy && serverReady &&
            GroupLimits.canCreateGroup(plan, groups.size) == LimitCheck.Allowed

    val isEmpty: Boolean get() = !loading && groups.isEmpty()
}

/** 그룹 만들기 화면 상태. */
data class CreateGroupUiState(
    val name: String = "",
    val busy: Boolean = false,
    val error: AppError? = null,
    val createdGroupId: String? = null,
) {
    val canSubmit: Boolean get() = !busy && name.isNotBlank()
}

/** 참여 화면 상태. 코드를 확인하면 [invite] 가 채워지고 동의 화면으로 넘어간다. */
data class JoinGroupUiState(
    val code: String = "",
    val invite: InviteLookup? = null,
    val consentChecked: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    val joined: Boolean = false,
    /**
     * 들어가는 사람의 요금제.
     *
     * 동의 문구의 '최대 몇 분 동안 제공됩니다'를 정한다. 그 시간은 **내가 공유할 때의
     * 상한**이므로 그룹 소유자가 아니라 내 요금제로 본다(위치정보법 제19조②의
     * '제공 기간'을 사실과 다르게 적으면 동의 자체가 어긋난다).
     */
    val plan: Plan = Plan.Free,
) {
    val canLookup: Boolean get() = !busy && code.isNotBlank()

    /** 체크해야만 참여된다(위치정보법 제19조②). */
    val canJoin: Boolean get() = !busy && invite != null && consentChecked
}
