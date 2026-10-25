package io.github.ndrcb5210max.ansimlink.data

/**
 * Firestore 컬렉션·필드 이름을 한곳에 모아 둔다.
 *
 * 같은 이름을 `firestore.rules` 와 `tests/rules/` 도 쓴다. 바꿀 때는 셋을 같이 고쳐야 한다.
 *
 * 구조
 * ```
 * users/{uid}                        displayName, email, consents{...}, createdAt
 * users/{uid}/pushTokens/{token}     platform, updatedAt — 계정 삭제 때 같이 지운다
 *
 * groups/{gid}                       name, ownerUid, memberUids[], createdAt
 * groups/{gid}/members/{uid}         displayName, consent{location, at, version}, paused, joinedWithCode,
 *                                    pushToken, sharing{active, startedAt, endsAt, sessionId}
 * groups/{gid}/invites/{code}        createdBy, createdAt, expiresAt
 * groups/{gid}/locations/{uid}       lat, lng, accuracy, at, sessionId, expiresAt
 * groups/{gid}/alerts/{id}           senderUid, senderName, severity, type, message, createdAt, sharing
 * groups/{gid}/provisionLogs/{id}    uid, sessionId, purpose, receiverUids[], startedAt, plannedEndAt, endedAt
 *
 * inviteCodes/{code}                 groupId, groupName, memberNames[], expiresAt, createdBy
 * ```
 *
 * 4단계에서 더한 것에 대한 메모
 *
 * - `locations/{uid}` 는 **문서 하나만** 둔다. 이동 경로를 쌓지 않는다. 구성원별 마지막
 *   위치 1건이면 '지금 어디 있나'를 보여 주는 데 충분하고, 경로를 쌓으면 그게 곧
 *   이동 기록이 된다(CLAUDE.md 원칙 4 최소 수집, 원칙 10 감시 앱처럼 보이지 않기).
 *   `expiresAt` 은 서버 정리 함수가 보는 값이다(세션 종료 24시간 뒤).
 * - `members/{uid}.pushToken` 과 `users/{uid}/pushTokens/{token}` 을 둘 다 둔다.
 *   members 쪽은 서버가 '이 그룹 사람들에게 보내라'를 한 번 읽어 처리하려고 두는 사본이고,
 *   users 쪽이 원본이다(계정 삭제 때 전부 지울 수 있어야 한다).
 * - `alerts/{id}` 는 앱이 만들고 서버(onAlertCreated)가 읽어 FCM 으로 보낸다.
 *   **앱은 FCM 을 직접 보내지 않는다** — 발송에는 서버 자격 증명이 필요하다.
 * - `provisionLogs/{id}` 는 위치정보법 제19조③의 제공 내역이다. 기기 안 사본
 *   (Room `provision_records`)과 같은 내용이고, 여기에도 좌표를 담지 않는다.
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

    /** `users/{uid}/pushTokens/{token}`. 문서 ID 가 곧 FCM 토큰이다. */
    object PushTokens {
        const val PLATFORM = "platform"
        const val UPDATED_AT = "updatedAt"

        const val PLATFORM_ANDROID = "android"
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
        const val ALERTS = "alerts"
        const val PROVISION_LOGS = "provisionLogs"
    }

    object Members {
        const val DISPLAY_NAME = "displayName"
        const val CONSENT = "consent"
        const val CONSENT_LOCATION = "location"
        const val CONSENT_AT = "at"
        const val CONSENT_VERSION = "version"
        const val PAUSED = "paused"
        const val JOINED_WITH_CODE = "joinedWithCode"

        /** 이 그룹에 보낼 때 쓰는 내 FCM 토큰 사본. 원본은 `users/{uid}/pushTokens`. */
        const val PUSH_TOKEN = "pushToken"

        /** 내가 지금 공유 중인지. 가족 위치 보기 화면이 '공유 중인 사람만' 보여 주는 근거다. */
        const val SHARING = "sharing"
        const val SHARING_ACTIVE = "active"
        const val SHARING_STARTED_AT = "startedAt"
        const val SHARING_ENDS_AT = "endsAt"
        const val SHARING_SESSION_ID = "sessionId"
    }

    /**
     * `groups/{gid}/locations/{uid}` — 구성원별 **마지막 위치 1건**.
     *
     * 이동 경로를 쌓지 않는다(위 클래스 주석 참고).
     */
    object Locations {
        const val LATITUDE = "lat"
        const val LONGITUDE = "lng"
        const val ACCURACY = "accuracy"
        const val AT = "at"
        const val SESSION_ID = "sessionId"

        /** 서버 정리 함수가 보는 값. 이 시각이 지나면 문서를 지운다. */
        const val EXPIRES_AT = "expiresAt"
    }

    /**
     * `groups/{gid}/alerts/{id}` — 안심 메시지 한 건.
     *
     * 앱이 만들고 서버(`onAlertCreated`)가 읽어 FCM 으로 보낸다.
     */
    object Alerts {
        const val SENDER_UID = "senderUid"
        const val SENDER_NAME = "senderName"
        const val SEVERITY = "severity"
        const val TYPE = "type"
        const val MESSAGE = "message"
        const val CREATED_AT = "createdAt"

        /** 이 메시지와 함께 위치 공유도 시작했는지. 받는 알림의 버튼이 달라진다. */
        const val SHARING = "sharing"
    }

    /** `groups/{gid}/provisionLogs/{id}` — 위치정보법 제19조③ 제공 내역. 좌표를 담지 않는다. */
    object ProvisionLogs {
        const val UID = "uid"
        const val SESSION_ID = "sessionId"
        const val PURPOSE = "purpose"
        const val RECEIVER_UIDS = "receiverUids"
        const val STARTED_AT = "startedAt"
        const val PLANNED_END_AT = "plannedEndAt"
        const val ENDED_AT = "endedAt"
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
