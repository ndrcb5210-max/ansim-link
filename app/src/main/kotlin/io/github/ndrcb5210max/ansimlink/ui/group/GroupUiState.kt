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
) {
    /** 2단계에는 결제가 없으므로 항상 무료 요금제로 본다. */
    val plan: Plan get() = Plan.Free

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
) {
    val canLookup: Boolean get() = !busy && code.isNotBlank()

    /** 체크해야만 참여된다(위치정보법 제19조②). */
    val canJoin: Boolean get() = !busy && invite != null && consentChecked
}
