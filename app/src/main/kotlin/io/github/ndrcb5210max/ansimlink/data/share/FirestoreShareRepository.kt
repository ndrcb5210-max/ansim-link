package io.github.ndrcb5210max.ansimlink.data.share

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Alerts
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Groups
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Locations
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Members
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.ProvisionLogs
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.PushTokens
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Users
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.SharingMember
import io.github.ndrcb5210max.ansimlink.data.toGroupMember
import io.github.ndrcb5210max.ansimlink.data.toGroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.toMemberLocation
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.share.LocationPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

/**
 * Firestore 를 쓰는 [ShareRepository] 구현.
 *
 * 문서 구조는 [io.github.ndrcb5210max.ansimlink.data.FirestoreSchema] 주석에 있고
 * 보안 규칙(firestore.rules)과 짝이다. 한쪽만 고치면 안 된다.
 *
 * @param now 시계. 테스트에서 바꿀 수 있게 인자로 받는다.
 */
class FirestoreShareRepository(
    private val firestore: FirebaseFirestore,
    private val now: () -> Long = System::currentTimeMillis,
) : ShareRepository {

    override suspend fun myGroupsOnce(uid: String): List<GroupWithMembers> {
        val snapshot = firestore.collection(Groups.COLLECTION)
            .whereArrayContains(Groups.MEMBER_UIDS, uid)
            .get()
            .await()
        return snapshot.documents.mapNotNull { document ->
            val members = document.reference.collection(Groups.MEMBERS).get().await()
            toGroupWithMembers(document, members.documents)
        }
    }

    override suspend fun startSharing(
        groupId: String,
        uid: String,
        sessionId: String,
        startedAtMillis: Long,
        endsAtMillis: Long,
        purpose: ProvisionPurpose,
        receiverUids: List<String>,
    ) {
        // 제공 내역을 **먼저** 남긴다. 순서가 중요하다.
        //
        // 위치정보법 제19조③은 제공 사실을 즉시 알리고 기록하게 한다. 'sharing.active' 를
        // 먼저 켜면 그 순간부터 다른 구성원이 내 위치를 기다리기 시작하는데, 기록 쓰기가
        // 실패하면 '기록 없이 제공되는' 상태가 된다. 반대 순서면 최악의 경우
        // '기록은 있는데 제공은 안 된' 상태가 되므로 이쪽이 안전하다.
        groupRef(groupId).collection(Groups.PROVISION_LOGS)
            .add(
                mapOf(
                    ProvisionLogs.UID to uid,
                    ProvisionLogs.SESSION_ID to sessionId,
                    ProvisionLogs.PURPOSE to purpose.key,
                    ProvisionLogs.RECEIVER_UIDS to receiverUids,
                    ProvisionLogs.STARTED_AT to startedAtMillis,
                    ProvisionLogs.PLANNED_END_AT to endsAtMillis,
                    ProvisionLogs.ENDED_AT to null,
                ),
            )
            .await()

        memberRef(groupId, uid).update(
            mapOf(
                sharingField(Members.SHARING_ACTIVE) to true,
                sharingField(Members.SHARING_STARTED_AT) to startedAtMillis,
                sharingField(Members.SHARING_ENDS_AT) to endsAtMillis,
                sharingField(Members.SHARING_SESSION_ID) to sessionId,
            ),
        ).await()
    }

    override suspend fun endSharing(
        groupId: String,
        uid: String,
        sessionId: String,
        endedAtMillis: Long,
    ) {
        // 끄는 쪽은 'sharing.active' 를 먼저 내린다. 기록 쓰기가 실패해도 공유 표시는
        // 꺼져 있어야 한다(원칙 1: 줄이는 방향은 즉시 반영).
        runCatching {
            memberRef(groupId, uid).update(
                mapOf(sharingField(Members.SHARING_ACTIVE) to false),
            ).await()
        }

        val logs = groupRef(groupId).collection(Groups.PROVISION_LOGS)
            .whereEqualTo(ProvisionLogs.SESSION_ID, sessionId)
            .whereEqualTo(ProvisionLogs.UID, uid)
            .get()
            .await()
        logs.documents.forEach { document ->
            runCatching {
                document.reference.update(ProvisionLogs.ENDED_AT, endedAtMillis).await()
            }
        }
    }

    override suspend fun writeLocation(
        groupId: String,
        uid: String,
        sessionId: String,
        point: LocationPoint,
        expiresAtMillis: Long,
    ) {
        locationRef(groupId, uid).set(
            mapOf(
                Locations.LATITUDE to point.latitude,
                Locations.LONGITUDE to point.longitude,
                Locations.ACCURACY to point.accuracyMeters,
                Locations.AT to point.atMillis,
                Locations.SESSION_ID to sessionId,
                Locations.EXPIRES_AT to expiresAtMillis,
            ),
        ).await()
    }

    override suspend fun deleteLocation(groupId: String, uid: String) {
        locationRef(groupId, uid).delete().await()
    }

    override suspend fun createAlert(
        groupId: String,
        senderUid: String,
        senderName: String,
        severity: AlertSeverity,
        type: DisasterType,
        message: String,
        sharing: Boolean,
        createdAtMillis: Long,
    ): String {
        val reference = groupRef(groupId).collection(Groups.ALERTS)
            .add(
                mapOf(
                    Alerts.SENDER_UID to senderUid,
                    Alerts.SENDER_NAME to senderName,
                    Alerts.SEVERITY to severity.key,
                    Alerts.TYPE to type.key,
                    Alerts.MESSAGE to message,
                    Alerts.SHARING to sharing,
                    Alerts.CREATED_AT to createdAtMillis,
                ),
            )
            .await()
        return reference.id
    }

    override suspend fun registerPushToken(uid: String, token: String, nowMillis: Long) {
        // 원본. 계정 삭제 때 이 컬렉션을 통째로 지운다(AccountDeleter).
        firestore.collection(Users.COLLECTION).document(uid)
            .collection(Users.PUSH_TOKENS).document(token)
            .set(
                mapOf(
                    PushTokens.PLATFORM to PushTokens.PLATFORM_ANDROID,
                    PushTokens.UPDATED_AT to nowMillis,
                ),
            )
            .await()

        // 서버가 '이 그룹 사람들에게 보내라'를 한 번 읽어 처리하도록 두는 사본.
        // 그룹마다 따로 쓴다. 한 그룹에서 실패해도 나머지는 등록되어야 한다.
        val groups = firestore.collection(Groups.COLLECTION)
            .whereArrayContains(Groups.MEMBER_UIDS, uid)
            .get()
            .await()
        groups.documents.forEach { document ->
            runCatching {
                document.reference.collection(Groups.MEMBERS).document(uid)
                    .update(Members.PUSH_TOKEN, token)
                    .await()
            }
        }
    }

    override fun observeSharingMembers(
        groupId: String,
        myUid: String,
    ): Flow<List<SharingMember>> {
        val members = groupRef(groupId).collection(Groups.MEMBERS).snapshots()
        val locations = groupRef(groupId).collection(Groups.LOCATIONS).snapshots()

        return combine(members, locations) { memberDocuments, locationDocuments ->
            val byUid = locationDocuments.mapNotNull { it.toMemberLocation() }.associateBy { it.uid }
            val nowMillis = now()
            memberDocuments
                .mapNotNull { it.toGroupMember() }
                // 나는 빼고 보여 준다. 내 상태는 홈 화면의 공유 배너에 있다.
                .filterNot { it.uid == myUid }
                // 공유 중이 아닌 사람은 위치가 남아 있어도 빼낸다.
                .filter { it.sharingNow(nowMillis) }
                .map { member -> SharingMember(member = member, location = byUid[member.uid]) }
                .sortedBy { it.member.displayName }
        }
    }

    // ---- 내부 도우미 ----

    private fun groupRef(groupId: String): DocumentReference =
        firestore.collection(Groups.COLLECTION).document(groupId)

    private fun memberRef(groupId: String, uid: String): DocumentReference =
        groupRef(groupId).collection(Groups.MEMBERS).document(uid)

    private fun locationRef(groupId: String, uid: String): DocumentReference =
        groupRef(groupId).collection(Groups.LOCATIONS).document(uid)

    /** `sharing.active` 처럼 지도(map) 안의 한 칸만 고치는 경로. */
    private fun sharingField(key: String): String = Members.SHARING + "." + key
}

/** Firestore 컬렉션 스냅샷 리스너를 Flow 로 바꾼다. */
private fun Query.snapshots(): Flow<List<DocumentSnapshot>> =
    callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            when {
                // 권한이 없거나 그룹에서 빠지면 오류가 온다. 화면이 죽지 않게 빈 목록을 흘린다.
                error != null -> trySend(emptyList())
                snapshot != null -> trySend(snapshot.documents)
            }
        }
        awaitClose { registration.remove() }
    }
