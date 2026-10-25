package io.github.ndrcb5210max.ansimlink.data.model

import io.github.ndrcb5210max.ansimlink.domain.group.ShareState

/** 로그인한 사람. Firebase Auth 의 값만 담는다. */
data class SignedInUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val emailVerified: Boolean,
    /** Google 로그인으로 들어왔는지. 계정 삭제 재인증 방법이 달라진다. */
    val signedInWithGoogle: Boolean,
) {
    /** 화면에 보여 줄 이름. 이름이 없으면 이메일 앞부분을 쓴다. */
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')
            ?: uid.take(6)
}

/** `groups/{gid}` 한 건. */
data class AnsimGroup(
    val id: String,
    val name: String,
    val ownerUid: String,
    val memberUids: List<String>,
) {
    val memberCount: Int get() = memberUids.size

    fun isOwnedBy(uid: String): Boolean = ownerUid == uid
}

/** `groups/{gid}/members/{uid}` 한 건. */
data class GroupMember(
    val uid: String,
    val displayName: String,
    val locationConsent: Boolean,
    val paused: Boolean,
    val consentVersion: String,
    val consentAtMillis: Long,
) {
    val shareState: ShareState get() = ShareState.of(locationConsent, paused)
}

/** `inviteCodes/{code}` 한 건. 참여 동의 화면에 보여 줄 내용이 들어 있다. */
data class InviteLookup(
    val code: String,
    val groupId: String,
    val groupName: String,
    /** 코드를 만든 시점의 구성원 이름. 위치정보법 제19조②의 '제공받는 자' 고지에 쓴다. */
    val memberNames: List<String>,
    val expiresAtMillis: Long,
)

/** 그룹 화면에 쓰는 그룹 + 구성원 묶음. */
data class GroupWithMembers(
    val group: AnsimGroup,
    val members: List<GroupMember>,
) {
    fun memberOf(uid: String): GroupMember? = members.firstOrNull { it.uid == uid }
}
