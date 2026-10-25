package io.github.ndrcb5210max.ansimlink.data

import com.google.firebase.firestore.DocumentSnapshot
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Groups
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Locations
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Members
import io.github.ndrcb5210max.ansimlink.data.model.AnsimGroup
import io.github.ndrcb5210max.ansimlink.data.model.GroupMember
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.MemberLocation

/**
 * Firestore 문서를 앱 모델로 바꾸는 함수들.
 *
 * 값이 없거나 형이 다르면 예외를 던지지 않고 기본값을 쓴다. 보안 규칙이 형을 검사하지만,
 * 오래된 문서나 손상된 문서 하나 때문에 화면 전체가 비는 것보다는 낫다.
 */

internal fun DocumentSnapshot.toAnsimGroup(): AnsimGroup? {
    if (!exists()) return null
    return AnsimGroup(
        id = id,
        name = getString(Groups.NAME).orEmpty(),
        ownerUid = getString(Groups.OWNER_UID).orEmpty(),
        memberUids = (get(Groups.MEMBER_UIDS) as? List<*>).orEmpty().filterIsInstance<String>(),
    )
}

internal fun DocumentSnapshot.toGroupMember(): GroupMember? {
    if (!exists()) return null
    val consent = get(Members.CONSENT) as? Map<*, *> ?: emptyMap<Any, Any>()
    val sharing = get(Members.SHARING) as? Map<*, *> ?: emptyMap<Any, Any>()
    return GroupMember(
        uid = id,
        displayName = getString(Members.DISPLAY_NAME).orEmpty(),
        locationConsent = consent[Members.CONSENT_LOCATION] as? Boolean ?: false,
        paused = getBoolean(Members.PAUSED) ?: false,
        consentVersion = consent[Members.CONSENT_VERSION] as? String ?: "",
        consentAtMillis = (consent[Members.CONSENT_AT] as? Number)?.toLong() ?: 0L,
        sharingActive = sharing[Members.SHARING_ACTIVE] as? Boolean ?: false,
        sharingEndsAtMillis = (sharing[Members.SHARING_ENDS_AT] as? Number)?.toLong() ?: 0L,
    )
}

internal fun DocumentSnapshot.toMemberLocation(): MemberLocation? {
    if (!exists()) return null
    // 좌표가 없는 문서는 쓸모가 없다. 기본값 0,0 으로 만들면 아프리카 앞바다에 점이 찍힌다.
    val latitude = (get(Locations.LATITUDE) as? Number)?.toDouble() ?: return null
    val longitude = (get(Locations.LONGITUDE) as? Number)?.toDouble() ?: return null
    return MemberLocation(
        uid = id,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = (get(Locations.ACCURACY) as? Number)?.toFloat(),
        atMillis = (get(Locations.AT) as? Number)?.toLong() ?: 0L,
        sessionId = getString(Locations.SESSION_ID).orEmpty(),
    )
}

internal fun toGroupWithMembers(
    groupDocument: DocumentSnapshot,
    memberDocuments: List<DocumentSnapshot>,
): GroupWithMembers? {
    val group = groupDocument.toAnsimGroup() ?: return null
    return GroupWithMembers(
        group = group,
        members = memberDocuments.mapNotNull { it.toGroupMember() },
    )
}
