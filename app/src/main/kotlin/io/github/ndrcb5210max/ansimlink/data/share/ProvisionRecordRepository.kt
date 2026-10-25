package io.github.ndrcb5210max.ansimlink.data.share

import kotlinx.coroutines.flow.Flow

/**
 * 내 위치 제공 기록 저장소(기기 안).
 *
 * 보관 기간을 [RETENTION_MILLIS] 로 둔 근거. **근거와 기간을 나눠서 본다.**
 *
 * - 근거: 위치정보법 제16조②는 '위치정보 수집·이용·제공사실 확인자료'를 자동으로
 *   기록·보존하도록 한다. **기간은 정하지 않는다**(시행령 2026-02-10 시행본에도 없다).
 * - 기간: 제18조①4호·제19조①4호에 따라 **약관으로 정하는 사항**이다. 약관
 *   (docs/legal/location-terms.md 제6조)에 180일로 적었고 이 상수가 그 숫자다.
 *
 * 보존 의무는 사업자(서버)에게 있고 서버 쪽 기록은 Firestore 에 따로 남는다. 기기 안
 * 기록은 사용자가 보기 위한 사본이라 약관과 같은 기간으로 맞췄다.
 *
 * 이 상수를 고치면 약관·처리방침·계정 삭제 안내의 숫자도 같이 고쳐야 한다. 화면 문구는
 * 이 상수에서 일수를 받아 가므로(ProvisionLogScreen, DeleteAccountScreen) 저절로 따라온다.
 *
 * **좌표는 담지 않는다**([ProvisionRecordEntity] 주석 참고).
 */
class ProvisionRecordRepository(
    private val dao: ProvisionRecordDao,
) {

    fun recent(limit: Int = RECENT_LIMIT): Flow<List<ProvisionRecordEntity>> = dao.recent(limit)

    /** 공유를 시작할 때 그룹마다 한 건 남긴다. */
    suspend fun recordStart(
        sessionId: String,
        groupId: String,
        groupName: String,
        startedAtMillis: Long,
        plannedEndAtMillis: Long,
        purpose: ProvisionPurpose,
        receiverNames: List<String>,
    ) {
        dao.insert(
            ProvisionRecordEntity(
                sessionId = sessionId,
                groupId = groupId,
                groupName = groupName,
                startedAtMillis = startedAtMillis,
                plannedEndAtMillis = plannedEndAtMillis,
                endedAtMillis = null,
                purposeKey = purpose.key,
                receiverNames = receiverNames,
            ),
        )
        dao.deleteOlderThan(startedAtMillis - RETENTION_MILLIS)
    }

    /** 공유가 끝날 때 실제 종료 시각과 쓴 건수를 채운다. */
    suspend fun recordEnd(
        sessionId: String,
        groupId: String,
        endedAtMillis: Long,
        writeCount: Int,
    ) = dao.markEnded(sessionId, groupId, endedAtMillis, writeCount)

    /**
     * 끝나지 않은 채 남은 기록을 정리한다. 앱을 켤 때 한 번 부른다.
     *
     * 서비스가 비정상 종료되면 종료 시각이 비어 '아직 공유 중'으로 보인다.
     * 예정 종료 시각이 이미 지난 기록만 닫으므로, 정말 공유 중인 세션은 건드리지 않는다.
     */
    suspend fun closeStaleRecords(nowMillis: Long) = dao.closeStaleRecords(nowMillis)

    companion object {
        /** 제공 기록 화면이 보여 주는 건수. */
        const val RECENT_LIMIT = 100

        /** 보관 기간 180일. 약관(docs/legal/location-terms.md 제6조)에서 정한 기간이다. */
        const val RETENTION_MILLIS: Long = 180L * 24 * 60 * 60 * 1000
    }
}
