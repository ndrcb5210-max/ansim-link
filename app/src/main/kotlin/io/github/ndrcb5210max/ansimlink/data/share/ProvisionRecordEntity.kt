package io.github.ndrcb5210max.ansimlink.data.share

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * '내 위치를 누구에게·언제·무슨 목적으로 제공했는지' 기록 한 건.
 *
 * 위치정보법 제19조③은 위치정보를 제3자에게 제공할 때마다 **즉시 통보**하고 그 내역을
 * 남기도록 한다. 그래서 공유를 시작할 때마다 이 기록을 남기고, 앱의 '내 위치 제공 기록'
 * 화면에서 볼 수 있게 한다. 같은 내용을 Firestore 에도 남긴다
 * (기기를 바꿔도 내역이 남아야 한다 — [ShareRepository]).
 *
 * **좌표를 담지 않는다.** 여기 들어가는 것은 '언제, 어느 그룹의 누구에게, 왜, 얼마 동안'
 * 뿐이다. 좌표는 공유 세션 동안만 Firestore 에 있고 세션이 끝나면 마지막 1건만 남긴 뒤
 * 24시간이 지나면 지운다(CLAUDE.md 원칙 4). 제공 내역은 그보다 오래 남아야 하므로
 * 좌표와 같은 자리에 두면 안 된다.
 *
 * @param purposeKey 제공 목적. [ProvisionPurpose] 의 키를 넣는다.
 * @param receiverNames 제공받은 사람 이름(시작 시점). 그룹 구성원은 바뀌므로 그때 값을 적어 둔다.
 * @param endedAtMillis 실제로 공유가 끝난 시각. 아직 공유 중이면 null.
 * @param plannedEndAtMillis 시작할 때 예정했던 끝 시각. '60분 예정이었는데 10분에 직접 멈췄다'를
 *   화면에 보여 줄 수 있다.
 */
@Entity(
    tableName = "provision_records",
    indices = [Index("startedAtMillis")],
)
data class ProvisionRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 같은 공유 세션에서 그룹마다 한 건씩 남는다. 세션 ID 로 묶어 본다. */
    val sessionId: String,
    val groupId: String,
    val groupName: String,
    val startedAtMillis: Long,
    val plannedEndAtMillis: Long,
    val endedAtMillis: Long?,
    val purposeKey: String,
    val receiverNames: List<String>,
    /** 이 그룹에 실제로 쓴 위치 건수. 세션이 끝날 때 채운다. */
    val writeCount: Int = 0,
) {
    val purpose: ProvisionPurpose
        get() = ProvisionPurpose.fromKey(purposeKey) ?: ProvisionPurpose.ManualShare

    val ongoing: Boolean get() = endedAtMillis == null
}

/**
 * 위치를 제공한 목적. 위치정보법 제19조③의 '목적'에 해당한다.
 *
 * 화면 문구는 strings.xml 에 둔다(사람이 읽는 설명이다).
 */
enum class ProvisionPurpose(val key: String) {
    /** 재난문자를 감지해 알림을 띄웠고, 사용자가 '공유 시작'을 눌렀다. */
    AlertPrompt("alert_prompt"),

    /** 재난문자를 감지해 자동 시작 모드로 시작했다. */
    AlertAuto("alert_auto"),

    /** 사용자가 앱에서 '지금 공유'를 눌렀다. 재난문자와 무관하다. */
    ManualShare("manual_share"),
    ;

    companion object {
        fun fromKey(key: String): ProvisionPurpose? = entries.firstOrNull { it.key == key }
    }
}
