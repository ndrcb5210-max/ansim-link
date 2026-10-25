package io.github.ndrcb5210max.ansimlink.data.group

import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.InviteLookup
import kotlinx.coroutines.flow.Flow

/** 안심 그룹 만들기·참여·구성원 상태 바꾸기. */
interface GroupRepository {

    /** 내가 속한 그룹과 그 구성원들. 그룹·구성원이 바뀌면 다시 흘린다. */
    fun observeMyGroups(uid: String): Flow<List<GroupWithMembers>>

    /**
     * 그룹을 만든다. 만든 사람은 바로 구성원이 되며 위치 제공에 동의한 것으로 기록한다
     * (만들 때 동의 화면을 거친다).
     *
     * @return 만들어진 그룹 ID
     */
    suspend fun createGroup(
        ownerUid: String,
        ownerDisplayName: String,
        groupName: String,
        currentGroupCount: Int,
    ): String

    /** 초대 코드를 만든다. 그룹을 만든 사람만 부를 수 있다. */
    suspend fun createInvite(groupId: String, byUid: String): InviteLookup

    /**
     * 코드로 그룹을 찾는다. 참여 전 동의 화면에 보여 줄 내용(그룹 이름, 제공받는 구성원)을 담아 온다.
     * 코드가 없거나 만료됐으면 예외를 던진다.
     */
    suspend fun lookupInvite(rawCode: String): InviteLookup

    /**
     * 그룹에 참여한다. 위치 제공에 동의했을 때만 부른다.
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
