package io.github.ndrcb5210max.ansimlink.data.alert

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertConfidence
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.alert.PreviousTrigger
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerReason

/**
 * 발동 기록 한 건. 기기 안에만 있고 서버로 보내지 않는다.
 *
 * 저장하는 것: 시각, 문자 종류, 재난 유형, **본문 앞 60자**, 본문 해시, 발동 여부와 이유.
 * 저장하지 않는 것: 본문 전문, 허용 목록 밖 앱의 알림 내용(아예 읽지 않는다).
 *
 * enum 은 문자열 키로 넣는다. `ordinal` 로 넣으면 나중에 enum 순서를 바꿀 때 기록이 어긋난다.
 */
@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val detectedAtMillis: Long,
    val packageName: String,
    val severityKey: String,
    val typeKey: String,
    /** 재난구분 35종 이름(예: '화재'). 아무것도 맞지 않았으면 null. */
    val categoryName: String?,
    val confidenceKey: String,
    val bodyPreview: String,
    val bodyFingerprint: String,
    val channelId: String?,
    val triggered: Boolean,
    val reasonKey: String,
) {
    val severity: AlertSeverity
        get() = AlertSeverity.fromKey(severityKey) ?: AlertSeverity.Unknown

    val type: DisasterType
        get() = DisasterType.fromKey(typeKey) ?: DisasterType.Unknown

    val confidence: AlertConfidence
        get() = AlertConfidence.fromKey(confidenceKey) ?: AlertConfidence.Low

    val reason: TriggerReason
        get() = TriggerReason.fromKey(reasonKey) ?: TriggerReason.SeverityUnknown

    fun toPreviousTrigger(): PreviousTrigger = PreviousTrigger(
        bodyFingerprint = bodyFingerprint,
        severity = severity,
        type = type,
        triggeredAtMillis = detectedAtMillis,
    )
}

/**
 * 디버그 빌드에서만 쌓는 '받은 알림 패키지' 기록.
 *
 * 실기기(특히 삼성)의 재난문자 앱 패키지 이름을 찾기 위한 것이다.
 * **패키지 이름과 횟수·시각만** 남긴다. 제목·본문은 읽지도 저장하지도 않는다.
 * 릴리스 빌드에서는 아무것도 쌓이지 않는다.
 */
@Entity(tableName = "observed_packages")
data class ObservedPackageEntity(
    @PrimaryKey
    val packageName: String,
    val count: Long,
    val firstSeenAtMillis: Long,
    val lastSeenAtMillis: Long,
)
