package io.github.ndrcb5210max.ansimlink.data.share

import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.SharingMember
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.share.LocationPoint
import kotlinx.coroutines.flow.Flow

/**
 * 위치 공유와 안심 메시지의 서버(Firestore) 쪽.
 *
 * 화면과 서비스는 이 인터페이스만 본다. 덕분에 Firebase 없이 가짜 구현으로 테스트할 수 있다
 * (2단계 [io.github.ndrcb5210max.ansimlink.data.group.GroupRepository] 와 같은 방식).
 *
 * **FCM 을 직접 보내는 함수는 없다.** 발송에는 서버 자격 증명이 필요하므로
 * 앱은 `groups/{gid}/alerts/{id}` 문서를 만들고 서버(functions/)가 읽어 보낸다.
 */
interface ShareRepository {

    /** 내가 들어가 있는 그룹과 구성원. 공유를 시작할 때 동의 상태를 확인하는 데 쓴다. */
    suspend fun myGroupsOnce(uid: String): List<GroupWithMembers>

    /**
     * 공유 세션을 시작했다고 알린다. 그룹마다 한 번 부른다.
     *
     * 하는 일 두 가지.
     * 1. `members/{uid}.sharing` 을 켠다 — 다른 구성원이 '지금 공유 중'을 볼 수 있게.
     * 2. `provisionLogs/{id}` 를 남긴다 — 위치정보법 제19조③ 제공 내역.
     */
    suspend fun startSharing(
        groupId: String,
        uid: String,
        sessionId: String,
        startedAtMillis: Long,
        endsAtMillis: Long,
        purpose: ProvisionPurpose,
        receiverUids: List<String>,
    )

    /** 공유 세션이 끝났다고 알린다. `sharing.active` 를 끄고 제공 내역의 종료 시각을 채운다. */
    suspend fun endSharing(
        groupId: String,
        uid: String,
        sessionId: String,
        endedAtMillis: Long,
    )

    /**
     * 내 마지막 위치를 쓴다. 문서 하나를 덮어 쓴다(경로를 쌓지 않는다).
     *
     * @param expiresAtMillis 서버 정리 함수가 보는 값. 세션 종료 + 24시간.
     */
    suspend fun writeLocation(
        groupId: String,
        uid: String,
        sessionId: String,
        point: LocationPoint,
        expiresAtMillis: Long,
    )

    /** 내 위치 문서를 지운다. 동의 철회·공유 중지 직후에 부른다. */
    suspend fun deleteLocation(groupId: String, uid: String)

    /**
     * 안심 메시지 한 건을 만든다. 서버가 이 문서를 보고 FCM 을 보낸다.
     *
     * @param sharing 이 메시지와 함께 위치 공유도 시작했는지.
     */
    suspend fun createAlert(
        groupId: String,
        senderUid: String,
        senderName: String,
        severity: AlertSeverity,
        type: DisasterType,
        message: String,
        sharing: Boolean,
        createdAtMillis: Long,
    ): String

    /** FCM 토큰을 등록한다. `users/{uid}/pushTokens/{token}` 과 각 그룹의 내 구성원 문서에 쓴다. */
    suspend fun registerPushToken(uid: String, token: String, nowMillis: Long)

    /**
     * 지금 공유 중인 구성원과 그들의 마지막 위치. 가족 위치 보기 화면이 구독한다.
     *
     * 공유 중이 아닌 사람은 위치 문서가 남아 있어도 빠진다
     * ([io.github.ndrcb5210max.ansimlink.data.model.SharingMember] 주석 참고).
     */
    fun observeSharingMembers(groupId: String, myUid: String): Flow<List<SharingMember>>
}
