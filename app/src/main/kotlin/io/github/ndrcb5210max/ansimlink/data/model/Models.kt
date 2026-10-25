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

/**
 * `groups/{gid}/members/{uid}` 한 건.
 *
 * @param sharingEndsAtMillis 공유 중이면 예정 종료 시각. 공유 중이 아니면 0.
 *   `sharing.active` 를 그대로 믿지 않고 이 값도 같이 본다([sharingNow]).
 */
data class GroupMember(
    val uid: String,
    val displayName: String,
    val locationConsent: Boolean,
    val paused: Boolean,
    val consentVersion: String,
    val consentAtMillis: Long,
    val sharingActive: Boolean = false,
    val sharingEndsAtMillis: Long = 0L,
) {
    val shareState: ShareState get() = ShareState.of(locationConsent, paused)

    /**
     * 지금 실제로 공유 중인지.
     *
     * `sharing.active` 만 보지 않는 이유: 앱이 강제 종료되면 이 값을 false 로 되돌리지
     * 못해 영원히 '공유 중'으로 남는다. 예정 종료 시각이 지났으면 공유 중이 아니라고 본다.
     * 동의를 철회하거나 일시중지한 사람도 공유 중이 아니다(원칙 1: 즉시 반영).
     */
    fun sharingNow(nowMillis: Long): Boolean =
        sharingActive && shareState.sharesLocation && nowMillis < sharingEndsAtMillis
}

/**
 * `groups/{gid}/locations/{uid}` 한 건 — 구성원의 **마지막** 위치.
 *
 * 이동 경로가 아니다. 문서 하나가 덮어 써진다.
 */
data class MemberLocation(
    val uid: String,
    val latitude: Double,
    val longitude: Double,
    /** 수평 정확도(m). 모르면 null. */
    val accuracyMeters: Float?,
    val atMillis: Long,
    val sessionId: String,
)

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

    /** 나를 뺀 구성원. 내 위치를 받게 되는 사람들이다. */
    fun others(uid: String): List<GroupMember> = members.filterNot { it.uid == uid }
}

/**
 * 가족 위치 보기 화면에 쓰는 묶음.
 *
 * 공유 중인 사람만 담는다. 위치 문서가 남아 있어도 공유 중이 아니면 보여 주지 않는다
 * (공유가 끝나면 마지막 위치 1건이 24시간 남는데, 그것을 지도에 계속 띄우면
 *  '지금 거기 있다'로 잘못 읽힌다).
 */
data class SharingMember(
    val member: GroupMember,
    val location: MemberLocation?,
)
