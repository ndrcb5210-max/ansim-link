package io.github.ndrcb5210max.ansimlink.data.account

import com.google.firebase.firestore.FirebaseFirestore
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Groups
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Users
import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.data.group.GroupRepository
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.toGroupWithMembers
import kotlinx.coroutines.tasks.await

/**
 * 내가 만든 그룹에 다른 구성원이 남아 있을 때 고를 수 있는 처리 방법.
 *
 * 구글 플레이 계정 삭제 정책상 내 데이터는 반드시 지워야 하지만, 남의 그룹까지 말없이
 * 없앨 수는 없다. 그래서 둘 중 하나를 사용자가 고르게 한다.
 */
sealed interface OwnedGroupChoice {
    /** 그룹을 통째로 해산한다. 남은 구성원의 구성원 문서·위치도 같이 지워진다. */
    data object Disband : OwnedGroupChoice

    /** 남은 구성원 한 명에게 소유권을 넘기고 나만 빠진다. */
    data class Transfer(val newOwnerUid: String) : OwnedGroupChoice
}

/** 계정을 지우기 전에 사용자에게 물어봐야 하는 것들. */
data class DeletionPlan(
    /** 내가 만들었고 나 말고 구성원이 남아 있는 그룹. 하나씩 처리 방법을 골라야 한다. */
    val ownedGroupsWithOthers: List<GroupWithMembers>,
    /** 내가 만들었고 나 혼자인 그룹. 묻지 않고 지운다. */
    val ownedGroupsAlone: List<GroupWithMembers>,
    /** 남이 만든 그룹 중 내가 들어가 있는 것. 나만 빠진다. */
    val joinedGroups: List<GroupWithMembers>,
) {
    val needsChoice: Boolean get() = ownedGroupsWithOthers.isNotEmpty()

    companion object {
        fun from(uid: String, groups: List<GroupWithMembers>): DeletionPlan {
            val (owned, joined) = groups.partition { it.group.isOwnedBy(uid) }
            val (withOthers, alone) = owned.partition { entry ->
                entry.group.memberUids.any { it != uid }
            }
            return DeletionPlan(
                ownedGroupsWithOthers = withOthers,
                ownedGroupsAlone = alone,
                joinedGroups = joined,
            )
        }
    }
}

/**
 * 계정 삭제. 구글 플레이 '계정 삭제' 정책에 맞춰 앱 안에서 끝까지 할 수 있게 한다.
 *
 * 순서는 되돌릴 수 없는 것을 마지막에 둔다.
 * 1. 그룹에서 나가거나(내가 만든 그룹은 사용자가 고른 대로) 해산한다.
 * 2. `users/{uid}` 와 푸시 토큰을 지운다.
 * 3. Firebase Auth 계정을 지운다.
 *
 * 2번까지 끝내고 3번이 실패하면(재인증이 오래됐을 때) 다시 로그인한 뒤 같은 작업을 부르면 된다.
 */
class AccountDeleter(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
) {

    suspend fun delete(
        uid: String,
        plan: DeletionPlan,
        /** [DeletionPlan.ownedGroupsWithOthers] 의 그룹 ID 마다 사용자가 고른 처리 방법. */
        choices: Map<String, OwnedGroupChoice>,
    ) {
        plan.ownedGroupsWithOthers.forEach { entry ->
            val groupId = entry.group.id
            when (val choice = choices[groupId]) {
                is OwnedGroupChoice.Transfer -> {
                    groupRepository.transferOwnership(groupId, choice.newOwnerUid)
                    groupRepository.withdrawConsent(groupId, uid)
                }
                OwnedGroupChoice.Disband, null -> groupRepository.deleteGroup(groupId)
            }
        }
        plan.ownedGroupsAlone.forEach { groupRepository.deleteGroup(it.group.id) }
        plan.joinedGroups.forEach { groupRepository.withdrawConsent(it.group.id, uid) }

        deleteUserDocument(uid)

        authRepository.deleteAuthUser()
    }

    private suspend fun deleteUserDocument(uid: String) {
        val userRef = firestore.collection(Users.COLLECTION).document(uid)

        // 푸시 토큰은 4단계에서 생긴다. 미리 비워 둔다.
        val tokens = userRef.collection(Users.PUSH_TOKENS).get().await()
        if (!tokens.isEmpty) {
            val batch = firestore.batch()
            tokens.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        userRef.delete().await()
    }

    /** 삭제 전에 보여 줄 계획을 만든다. */
    suspend fun buildPlan(uid: String): DeletionPlan {
        val snapshot = firestore.collection(Groups.COLLECTION)
            .whereArrayContains(Groups.MEMBER_UIDS, uid)
            .get()
            .await()
        val groups = snapshot.documents.mapNotNull { document ->
            val members = document.reference.collection(Groups.MEMBERS).get().await()
            toGroupWithMembers(document, members.documents)
        }
        return DeletionPlan.from(uid, groups)
    }
}
