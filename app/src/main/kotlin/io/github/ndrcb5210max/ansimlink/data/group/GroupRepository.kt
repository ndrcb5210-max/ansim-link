package io.github.ndrcb5210max.ansimlink.data.group

import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.InviteLookup
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import kotlinx.coroutines.flow.Flow

/** 안심 그룹 만들기·참여·구성원 상태 바꾸기. */
interface GroupRepository {

    /** 내가 속한 그룹과 그 구성원들. 그룹·구성원이 바뀌면 다시 흘린다. */
    fun observeMyGroups(uid: String): Flow<List<GroupWithMembers>>

    /**
     * 그룹을 만든다. 만든 사람은 바로 구성원이 되며 위치 제공에 동의한 것으로 기록한다
     * (만들 때 동의 화면을 거친다).
     *
     * @param plan 한도 판정에 쓸 요금제. **서버가 검증한 값만 넘긴다**
     *   ([io.github.ndrcb5210max.ansimlink.data.billing.PlanProvider.currentPlan]).
     *   저장소가 직접 읽지 않고 인자로 받는 이유: 요금제 판정이 두 군데로 갈라지면
     *   화면이 보여 주는 한도와 저장소가 적용하는 한도가 어긋난다.
     * @return 만들어진 그룹 ID
     */
    suspend fun createGroup(
        ownerUid: String,
        ownerDisplayName: String,
        groupName: String,
        currentGroupCount: Int,
        plan: Plan,
    ): String

    /**
     * 초대 코드를 만든다. 그룹을 만든 사람만 부를 수 있다.
     *
     * 인원 한도는 여기서 막는다(코드를 만들 때). 들어가는 쪽에서 막지 않는 이유:
     * 한도는 **그룹을 만든 사람의 요금제**로 정해지는데, 들어가는 사람은 그 사람의
     * 구독 상태를 읽을 수 없다(`users/{uid}` 는 본인만 읽는다). 코드를 만드는 사람이
     * 곧 소유자이므로 그 순간에 보면 맞는 요금제로 판정된다.
     *
     * 규칙(firestore.rules)에는 요금제와 무관한 상한 10명만 둔다.
     *
     * @param plan 코드를 만드는 사람(= 그룹 소유자)의 요금제.
     */
    suspend fun createInvite(groupId: String, byUid: String, plan: Plan): InviteLookup

    /**
     * 코드로 그룹을 찾는다. 참여 전 동의 화면에 보여 줄 내용(그룹 이름, 제공받는 구성원)을 담아 온다.
     * 코드가 없거나 만료됐으면 예외를 던진다.
     */
    suspend fun lookupInvite(rawCode: String): InviteLookup

    /**
     * 그룹에 참여한다. 위치 제공에 동의했을 때만 부른다.
     *
     * 인원 한도를 여기서 보지 않는다([createInvite] 주석 참고). 요금제별 한도는 코드를
     * 만들 때 소유자의 요금제로 막고, 규칙이 상한 10명을 막는다.
     *
     * @param consentVersion 동의 화면에서 보여 준 위치정보 이용약관 버전.
     */
    suspend fun joinGroup(
        invite: InviteLookup,
        uid: String,
        displayName: String,
        consentVersion: String,
    )

    /** 내 공유를 일시중지하거나 다시 켠다(위치정보법 제24조). */
    suspend fun setPaused(groupId: String, uid: String, paused: Boolean)

    /**
     * 위치 제공 동의를 철회한다. 그룹에서 나가고 내 구성원 문서와 위치를 지운다.
     * 내가 그룹을 만든 사람이면 먼저 [transferOwnership] 하거나 [deleteGroup] 해야 한다.
     */
    suspend fun withdrawConsent(groupId: String, uid: String)

    /** 그룹을 해산한다(구성원 문서·초대 코드까지 지운다). 만든 사람만 부를 수 있다. */
    suspend fun deleteGroup(groupId: String)

    /** 그룹 소유권을 남은 구성원에게 넘긴다. */
    suspend fun transferOwnership(groupId: String, newOwnerUid: String)
}
