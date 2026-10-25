package io.github.ndrcb5210max.ansimlink.data.group

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.ndrcb5210max.ansimlink.data.AnsimException
import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Groups
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.InviteCodes
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Invites
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Members
import io.github.ndrcb5210max.ansimlink.data.model.AnsimGroup
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.InviteLookup
import io.github.ndrcb5210max.ansimlink.data.toAnsimGroup
import io.github.ndrcb5210max.ansimlink.data.toGroupMember
import io.github.ndrcb5210max.ansimlink.domain.consent.TermsVersion
import io.github.ndrcb5210max.ansimlink.domain.group.GroupLimits
import io.github.ndrcb5210max.ansimlink.domain.group.InviteCode
import io.github.ndrcb5210max.ansimlink.domain.group.LimitCheck
import io.github.ndrcb5210max.ansimlink.domain.group.LimitReason
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Firestore 를 쓰는 [GroupRepository] 구현.
 *
 * 쓰는 문서 구조는 [io.github.ndrcb5210max.ansimlink.data.FirestoreSchema] 주석에 적어 두었다.
 * 보안 규칙(firestore.rules)과 짝이므로 한쪽만 고치면 안 된다.
 */
class FirestoreGroupRepository(
    private val firestore: FirebaseFirestore,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) : GroupRepository {

    @Suppress("OPT_IN_USAGE") // flatMapLatest
    override fun observeMyGroups(uid: String): Flow<List<GroupWithMembers>> =
        groupsQuery(uid).snapshots()
            .flatMapLatest { groups ->
                if (groups.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    // 그룹마다 구성원 목록을 따로 구독해 합친다.
                    combine(groups.map { group -> membersFlow(group) }) { it.toList() }
                }
            }

    override suspend fun createGroup(
        ownerUid: String,
        ownerDisplayName: String,
        groupName: String,
        currentGroupCount: Int,
    ): String {
        // 2단계에는 결제가 없으므로 항상 무료 한도로 본다(5단계에서 서버가 검증한 값으로 바꾼다).
        val check = GroupLimits.canCreateGroup(Plan.Free, currentGroupCount)
        if (check is LimitCheck.Blocked) throw AnsimException(AppError.GroupLimitReached)

        val name = groupName.trim()
        require(name.isNotEmpty()) { "그룹 이름이 비어 있다." }

        val groupRef = firestore.collection(Groups.COLLECTION).document()
        val batch = firestore.batch()
        batch.set(
            groupRef,
            mapOf(
                Groups.NAME to name,
                Groups.OWNER_UID to ownerUid,
                Groups.MEMBER_UIDS to listOf(ownerUid),
                Groups.CREATED_AT to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            groupRef.collection(Groups.MEMBERS).document(ownerUid),
            memberDocument(
                displayName = ownerDisplayName,
                consentVersion = TermsVersion.LOCATION_TERMS,
                joinedWithCode = null,
            ),
        )
        batch.commit().await()
        return groupRef.id
    }

    override suspend fun createInvite(groupId: String, byUid: String): InviteLookup {
        val groupRef = firestore.collection(Groups.COLLECTION).document(groupId)
        val groupSnapshot = groupRef.get().await()
        val group = groupSnapshot.toAnsimGroup() ?: throw AnsimException(AppError.InviteNotFound)

        val memberCheck = GroupLimits.canAddMember(Plan.Free, group.memberCount)
        if (memberCheck is LimitCheck.Blocked && memberCheck.reason == LimitReason.MemberCount) {
            throw AnsimException(AppError.GroupFull)
        }

        val memberNames = groupRef.collection(Groups.MEMBERS).get().await()
            .documents.mapNotNull { it.getString(Members.DISPLAY_NAME) }

        val code = InviteCode.generate(random)
        val expiresAt = InviteCode.expiresAtMillis(now())

        val batch = firestore.batch()
        // 그룹 안의 기록(만든 사람이 목록으로 보고 지울 수 있다).
        batch.set(
            groupRef.collection(Groups.INVITES).document(code),
            mapOf(
                Invites.CREATED_BY to byUid,
                Invites.CREATED_AT to FieldValue.serverTimestamp(),
                Invites.EXPIRES_AT to expiresAt,
            ),
        )
        // 코드를 아는 사람만 읽는 조회용 문서(FirestoreSchema 주석 참고).
        batch.set(
            firestore.collection(InviteCodes.COLLECTION).document(code),
            mapOf(
                InviteCodes.GROUP_ID to groupId,
                InviteCodes.GROUP_NAME to group.name,
                InviteCodes.MEMBER_NAMES to memberNames,
                InviteCodes.EXPIRES_AT to expiresAt,
                InviteCodes.CREATED_BY to byUid,
            ),
        )
        batch.commit().await()

        return InviteLookup(
            code = code,
            groupId = groupId,
            groupName = group.name,
            memberNames = memberNames,
            expiresAtMillis = expiresAt,
        )
    }

    override suspend fun lookupInvite(rawCode: String): InviteLookup {
        val code = InviteCode.normalize(rawCode)
        if (!InviteCode.isWellFormed(code)) throw AnsimException(AppError.InviteNotFound)

        val snapshot = firestore.collection(InviteCodes.COLLECTION).document(code).get().await()
        if (!snapshot.exists()) throw AnsimException(AppError.InviteNotFound)

        val expiresAt = snapshot.getLong(InviteCodes.EXPIRES_AT) ?: 0L
        if (InviteCode.isExpired(expiresAt, now())) throw AnsimException(AppError.InviteExpired)

        return InviteLookup(
            code = code,
            groupId = snapshot.getString(InviteCodes.GROUP_ID)
                ?: throw AnsimException(AppError.InviteNotFound),
            groupName = snapshot.getString(InviteCodes.GROUP_NAME).orEmpty(),
            memberNames = (snapshot.get(InviteCodes.MEMBER_NAMES) as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty(),
            expiresAtMillis = expiresAt,
        )
    }

    override suspend fun joinGroup(
        invite: InviteLookup,
        uid: String,
        displayName: String,
        consentVersion: String,
    ) {
        if (InviteCode.isExpired(invite.expiresAtMillis, now())) {
            throw AnsimException(AppError.InviteExpired)
        }
        val groupRef = firestore.collection(Groups.COLLECTION).document(invite.groupId)

        // 두 번에 나눠 쓴다. 보안 규칙이 '먼저 만들어진 구성원 문서'를 보고 memberUids 추가를 허용하기 때문에
        // 한 배치로 묶으면(배치는 쓰기 전 상태로 평가된다) 규칙에 걸린다.
        groupRef.collection(Groups.MEMBERS).document(uid)
            .set(
                memberDocument(
                    displayName = displayName,
                    consentVersion = consentVersion,
                    joinedWithCode = invite.code,
                ),
            )
            .await()

        groupRef.update(Groups.MEMBER_UIDS, FieldValue.arrayUnion(uid)).await()
    }

    override suspend fun setPaused(groupId: String, uid: String, paused: Boolean) {
        memberRef(groupId, uid).update(Members.PAUSED, paused).await()
    }

    override suspend fun withdrawConsent(groupId: String, uid: String) {
        val groupRef = firestore.collection(Groups.COLLECTION).document(groupId)

        // 위치 문서는 4단계에서 생긴다. 지금은 없지만 미리 지워 둔다(없으면 아무 일도 없다).
        runCatching { groupRef.collection(Groups.LOCATIONS).document(uid).delete().await() }

        memberRef(groupId, uid).delete().await()
        groupRef.update(Groups.MEMBER_UIDS, FieldValue.arrayRemove(uid)).await()
    }

    override suspend fun deleteGroup(groupId: String) {
        val groupRef = firestore.collection(Groups.COLLECTION).document(groupId)

        deleteAllInCollection(groupRef.collection(Groups.MEMBERS))
        deleteAllInCollection(groupRef.collection(Groups.LOCATIONS))

        // 초대 코드는 그룹 안 기록과 조회용 문서 두 군데에 있다. 둘 다 지운다.
        val invites = groupRef.collection(Groups.INVITES).get().await()
        if (!invites.isEmpty) {
            val batch = firestore.batch()
            invites.documents.forEach { document ->
                batch.delete(document.reference)
                batch.delete(firestore.collection(InviteCodes.COLLECTION).document(document.id))
            }
            batch.commit().await()
        }

        groupRef.delete().await()
    }

    override suspend fun transferOwnership(groupId: String, newOwnerUid: String) {
        firestore.collection(Groups.COLLECTION).document(groupId)
            .update(Groups.OWNER_UID, newOwnerUid)
            .await()
    }

    // ---- 내부 도우미 ----

    private fun groupsQuery(uid: String): Query =
        firestore.collection(Groups.COLLECTION).whereArrayContains(Groups.MEMBER_UIDS, uid)

    private fun memberRef(groupId: String, uid: String): DocumentReference =
        firestore.collection(Groups.COLLECTION).document(groupId)
            .collection(Groups.MEMBERS).document(uid)

    private fun memberDocument(
        displayName: String,
        consentVersion: String,
        joinedWithCode: String?,
    ): Map<String, Any> = buildMap {
        put(Members.DISPLAY_NAME, displayName)
        put(
            Members.CONSENT,
            mapOf(
                Members.CONSENT_LOCATION to true,
                Members.CONSENT_VERSION to consentVersion,
                Members.CONSENT_AT to now(),
            ),
        )
        put(Members.PAUSED, false)
        if (joinedWithCode != null) put(Members.JOINED_WITH_CODE, joinedWithCode)
    }

    private fun membersFlow(groupSnapshot: DocumentSnapshot): Flow<GroupWithMembers> {
        val group = groupSnapshot.toAnsimGroup()
            ?: AnsimGroup(groupSnapshot.id, "", "", emptyList())
        return groupSnapshot.reference.collection(Groups.MEMBERS).snapshots().map { documents ->
            GroupWithMembers(
                group = group,
                members = documents.mapNotNull { it.toGroupMember() },
            )
        }
    }

    private suspend fun deleteAllInCollection(collection: CollectionReference) {
        val snapshot = collection.get().await()
        if (snapshot.isEmpty) return
        val batch = firestore.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }
}

/** Firestore 스냅샷 리스너를 Flow 로 바꾼다. */
private fun Query.snapshots(): Flow<List<DocumentSnapshot>> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot.documents)
        }
    }
    awaitClose { registration.remove() }
}
