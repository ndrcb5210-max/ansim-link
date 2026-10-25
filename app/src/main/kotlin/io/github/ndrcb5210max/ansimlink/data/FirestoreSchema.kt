package io.github.ndrcb5210max.ansimlink.data

/**
 * Firestore 컬렉션·필드 이름을 한곳에 모아 둔다.
 *
 * 같은 이름을 `firestore.rules` 와 `tests/rules/` 도 쓴다. 바꿀 때는 셋을 같이 고쳐야 한다.
 *
 * 구조
 * ```
 * users/{uid}                        displayName, email, consents{...}, createdAt
 * users/{uid}/pushTokens/{token}     (4단계에서 씀) 계정 삭제 때 같이 지운다
 *
 * groups/{gid}                       name, ownerUid, memberUids[], createdAt
 * groups/{gid}/members/{uid}         displayName, consent{location, at, version}, paused, joinedWithCode
 * groups/{gid}/invites/{code}        createdBy, createdAt, expiresAt
 * groups/{gid}/locations/{uid}       (4단계에서 씀) 계정 삭제 때 같이 지운다
 *
 * inviteCodes/{code}                 groupId, groupName, memberNames[], expiresAt, createdBy
 * ```
 *
 * `inviteCodes/{code}` 가 왜 따로 있나:
 * 초대 코드를 받은 사람은 아직 그룹 구성원이 아니라서 `groups/{gid}` 를 읽을 수 없다.
 * 코드만 가지고 그룹을 찾으려면 `invites` 를 컬렉션 그룹으로 조회해야 하는데,
 * 보안 규칙으로 '코드를 아는 사람만' 을 표현할 수 없어서 결국 모든 초대를 훑을 수 있게 된다.
 * 그래서 문서 ID 가 곧 코드인 최상위 문서를 하나 두고, 규칙에서 목록 조회(list)를 막고
 * 단건 조회(get)만 허용한다. 즉 코드를 아는 사람만 읽을 수 있다.
 * 이 문서에는 동의 화면에 법적으로 보여 줘야 하는 내용(그룹 이름, 제공받는 구성원 이름)만 담는다.
 */
object FirestoreSchema {

    object Users {
        const val COLLECTION = "users"
        const val DISPLAY_NAME = "displayName"
        const val EMAIL = "email"
        const val CONSENTS = "consents"
        const val CREATED_AT = "createdAt"

        const val PUSH_TOKENS = "pushTokens"

        /** consents 아래 한 항목의 하위 필드. */
        const val CONSENT_ACCEPTED = "accepted"
        const val CONSENT_AT = "at"
        const val CONSENT_VERSION = "version"
    }

    object Groups {
        const val COLLECTION = "groups"
        const val NAME = "name"
        const val OWNER_UID = "ownerUid"
        const val MEMBER_UIDS = "memberUids"
        const val CREATED_AT = "createdAt"

        const val MEMBERS = "members"
        const val INVITES = "invites"
        const val LOCATIONS = "locations"
    }

    object Members {
        const val DISPLAY_NAME = "displayName"
        const val CONSENT = "consent"
        const val CONSENT_LOCATION = "location"
        const val CONSENT_AT = "at"
        const val CONSENT_VERSION = "version"
        const val PAUSED = "paused"
        const val JOINED_WITH_CODE = "joinedWithCode"
    }

    object Invites {
        const val CREATED_BY = "createdBy"
        const val CREATED_AT = "createdAt"
        const val EXPIRES_AT = "expiresAt"
    }

    object InviteCodes {
        const val COLLECTION = "inviteCodes"
        const val GROUP_ID = "groupId"
        const val GROUP_NAME = "groupName"
        const val MEMBER_NAMES = "memberNames"
        const val EXPIRES_AT = "expiresAt"
        const val CREATED_BY = "createdBy"
    }
}
