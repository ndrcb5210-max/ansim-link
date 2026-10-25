package io.github.ndrcb5210max.ansimlink.data.alert

import io.github.ndrcb5210max.ansimlink.domain.alert.AlertClassification
import io.github.ndrcb5210max.ansimlink.domain.alert.PreviousTrigger
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerDecision
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerEngine
import kotlinx.coroutines.flow.Flow

/**
 * 발동 기록 저장소.
 *
 * 보관 기간을 [RETENTION_MILLIS] 로 제한한다. 발동 기록은 '내 휴대폰이 무엇을 감지했는지'를
 * 사용자가 확인하려고 두는 것이므로 오래 쌓아 둘 이유가 없다(CLAUDE.md 최소 수집).
 * 기록 화면에 '모두 지우기'도 둔다.
 */
class AlertEventRepository(
    private val alertEvents: AlertEventDao,
    private val observedPackages: ObservedPackageDao,
) {

    fun recentEvents(limit: Int = RECENT_LIMIT): Flow<List<AlertEventEntity>> =
        alertEvents.recent(limit)

    fun observedPackageList(): Flow<List<ObservedPackageEntity>> = observedPackages.all()

    /** 중복 판단에 넘길, 최근에 발동한 기록. */
    suspend fun recentTriggers(nowMillis: Long): List<PreviousTrigger> =
        alertEvents
            .triggeredSince(nowMillis - TriggerEngine.DEDUPE_WINDOW_MILLIS)
            .map(AlertEventEntity::toPreviousTrigger)

    suspend fun record(
        packageName: String,
        classification: AlertClassification,
        decision: TriggerDecision,
        nowMillis: Long,
    ) {
        alertEvents.insert(
            AlertEventEntity(
                detectedAtMillis = nowMillis,
                packageName = packageName,
                severityKey = classification.severity.key,
                typeKey = classification.type.key,
                categoryName = classification.category?.officialName,
                confidenceKey = classification.confidence.key,
                bodyPreview = classification.bodyPreview,
                bodyFingerprint = classification.bodyFingerprint,
                channelId = classification.channelId,
                triggered = decision.triggered,
                reasonKey = decision.reason.key,
            ),
        )
        alertEvents.deleteOlderThan(nowMillis - RETENTION_MILLIS)
    }

    suspend fun clearEvents() = alertEvents.deleteAll()

    /**
     * 디버그 빌드에서만 부른다. 패키지 이름만 적는다(알림 내용은 읽지도 저장하지도 않는다).
     */
    suspend fun recordObservedPackage(packageName: String, nowMillis: Long) =
        observedPackages.record(packageName, nowMillis)

    suspend fun clearObservedPackages() = observedPackages.deleteAll()

    companion object {
        /** 발동 기록 화면이 보여 주는 건수. */
        const val RECENT_LIMIT = 50

        /** 보관 기간 30일. */
        const val RETENTION_MILLIS: Long = 30L * 24 * 60 * 60 * 1000
    }
}
