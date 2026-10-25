package io.github.ndrcb5210max.ansimlink.data

import com.google.firebase.firestore.DocumentSnapshot
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Groups
import io.github.ndrcb5210max.ansimlink.data.FirestoreSchema.Members
import io.github.ndrcb5210max.ansimlink.data.model.AnsimGroup
import io.github.ndrcb5210max.ansimlink.data.model.GroupMember
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers

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
    return GroupMember(
        uid = id,
        displayName = getString(Members.DISPLAY_NAME).orEmpty(),
        locationConsent = consent[Members.CONSENT_LOCATION] as? Boolean ?: false,
        paused = getBoolean(Members.PAUSED) ?: false,
        consentVersion = consent[Members.CONSENT_VERSION] as? String ?: "",
        consentAtMillis = (consent[Members.CONSENT_AT] as? Number)?.toLong() ?: 0L,
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
