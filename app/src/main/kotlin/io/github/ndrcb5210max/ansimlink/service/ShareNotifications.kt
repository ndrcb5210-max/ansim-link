package io.github.ndrcb5210max.ansimlink.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.ndrcb5210max.ansimlink.MainActivity
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.share.ShareWindow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 위치 공유 관련 알림.
 *
 * 가장 중요한 것은 **상시 알림**이다. CLAUDE.md 원칙 2: 위치 공유가 진행되는 동안에는
 * 항상 알림을 보여 준다. 그래서 이 알림은
 * - 지울 수 없고(`setOngoing(true)`),
 * - 중요도를 낮추지 않고(채널 중요도 DEFAULT — 소리는 안 나지만 목록에서 접히지 않는다),
 * - '○명에게 공유 중 · 언제 시작 · 언제 종료 예정'을 그대로 적고,
 * - [지금 중지] 버튼을 단다.
 *
 * 채널을 발동 알림([AlertNotifications.TRIGGER_CHANNEL_ID])과 따로 둔 이유: 사용자가
 * 발동 알림을 끄더라도 공유 중 알림은 남아야 한다. 하나로 묶으면 '공유 중'을 숨길 수 있게 된다.
 */
object ShareNotifications {

    /** 공유 중 상시 알림 채널. */
    const val SHARING_CHANNEL_ID = "location_sharing"

    /** 받은 안심 메시지·다른 구성원의 공유 시작 알림 채널. */
    const val MESSAGE_CHANNEL_ID = "safety_message"

    /** 공유 중 상시 알림 ID. 포그라운드 서비스가 이 ID 로 붙는다. */
    const val SHARING_NOTIFICATION_ID = 3001

    /** '공유를 시작하지 못했습니다' 안내 알림 ID. */
    const val SHARE_FAILED_NOTIFICATION_ID = 3002

    /** 자동 시작에 실패했을 때 띄우는 '눌러서 시작' 알림 ID. */
    const val TAP_TO_START_NOTIFICATION_ID = 3003

    private const val MESSAGE_NOTIFICATION_ID_BASE = 3100

    // 'HH:mm'. 상시 알림은 오늘 안에 끝나므로(최대 180분) 날짜를 적지 않는다.
    private fun timeFormat() = SimpleDateFormat("HH:mm", Locale.KOREA)

    fun ensureChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)

        manager.createNotificationChannel(
            NotificationChannel(
                SHARING_CHANNEL_ID,
                context.getString(R.string.channel_sharing_name),
                // LOW 로 두면 기기에 따라 알림 목록에서 접혀 눈에 안 띈다.
                // 공유 중임을 숨기지 않으려면 DEFAULT 가 맞다. 소리는 따로 끈다.
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_sharing_description)
                setSound(null, null)
                enableVibration(false)
                // 사용자가 이 채널을 끄는 것은 막을 수 없다(시스템 설정). 그 대신
                // 앱 안 배너(HomeScreen)가 늘 같은 내용을 보여 준다.
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGE_CHANNEL_ID,
                context.getString(R.string.channel_message_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_message_description)
            },
        )
    }

    /**
     * 공유 중 상시 알림. 포그라운드 서비스가 `startForeground` 에 넘긴다.
     *
     * 문구 예: "가족 그룹 2명에게 내 위치를 공유 중 · 18:05 시작 · 19:05 종료 예정"
     */
    fun sharingNotification(
        context: Context,
        groupNames: List<String>,
        receiverCount: Int,
        window: ShareWindow,
    ): Notification {
        ensureChannels(context)
        val format = timeFormat()

        val title = context.getString(
            R.string.sharing_notification_title,
            groupNames.joinToString(", "),
            receiverCount,
        )
        val body = context.getString(
            R.string.sharing_notification_body,
            format.format(Date(window.startedAtMillis)),
            format.format(Date(window.endsAtMillis)),
        )

        return NotificationCompat.Builder(context, SHARING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_share)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // 지울 수 없게 한다. 공유 중임을 숨기지 않는다(원칙 2).
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_notification_share,
                context.getString(R.string.sharing_action_stop),
                servicePendingIntent(context, LocationShareService.ACTION_STOP, 11),
            )
            .build()
    }

    /**
     * 공유를 시작하지 못했을 때 이유를 알려 주는 알림.
     *
     * 알림 버튼을 눌렀는데 아무 일도 없는 상황을 막으려고 둔다. 동의를 철회했거나
     * 일시중지 중이거나 권한이 없을 때 뜬다.
     */
    fun postShareFailed(context: Context, reasonText: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_share)
            .setContentTitle(context.getString(R.string.sharing_failed_title))
            .setContentText(reasonText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reasonText))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        AlertNotifications.post(context, SHARE_FAILED_NOTIFICATION_ID, notification)
    }

    /**
     * 자동 시작이 막혔을 때 대신 띄우는 '눌러서 시작' 알림.
     *
     * 왜 필요한가: 앱이 백그라운드일 때는 배경 위치 권한 없이 위치 포그라운드 서비스를
     * 만들 수 없다(공식 문서). 사용자가 알림을 눌러 시작하면 예외로 허용된다.
     * 그래서 자동 시작이 실패하면 조용히 포기하지 않고 이 알림으로 바꾼다.
     */
    fun postTapToStart(context: Context, bodyText: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_share)
            .setContentTitle(context.getString(R.string.sharing_tap_to_start_title))
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_notification_share,
                context.getString(R.string.trigger_action_share),
                // 알림 버튼 → 브로드캐스트 → 서비스. 사용자가 누른 것이므로 시작이 허용된다.
                AlertActionReceiver.pendingIntent(
                    context,
                    AlertActionReceiver.ACTION_START_SHARING,
                    12,
                ),
            )
            .build()
        AlertNotifications.post(context, TAP_TO_START_NOTIFICATION_ID, notification)
    }

    /**
     * 그룹 구성원이 보낸 안심 메시지 알림.
     *
     * 문구 예: "엄마: 재난문자 받았어. 나는 괜찮아…" + [위치 보기]
     *
     * @param alertId 알림 ID 를 나누는 데 쓴다. 메시지가 여러 건 오면 겹쳐 쓰지 않는다.
     */
    fun postSafetyMessage(
        context: Context,
        senderName: String,
        message: String,
        groupId: String,
        alertId: String,
        showLocationAction: Boolean,
    ) {
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_share)
            .setContentTitle(senderName)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))

        if (showLocationAction) {
            builder.addAction(
                R.drawable.ic_notification_share,
                context.getString(R.string.sharing_action_view_location),
                openFamilyLocationIntent(context, groupId, alertId.hashCode()),
            )
        }

        AlertNotifications.post(context, messageNotificationId(alertId), builder.build())
    }

    /** 다른 구성원이 공유를 시작했다는 알림. */
    fun postShareStarted(context: Context, senderName: String, groupId: String, alertId: String) {
        ensureChannels(context)
        val body = context.getString(R.string.sharing_started_body, senderName)
        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_share)
            .setContentTitle(context.getString(R.string.sharing_started_title))
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openFamilyLocationIntent(context, groupId, alertId.hashCode()))
            .build()
        AlertNotifications.post(context, messageNotificationId(alertId), notification)
    }

    fun cancelSharing(context: Context) {
        NotificationManagerCompat.from(context).cancel(SHARING_NOTIFICATION_ID)
    }

    fun cancelTapToStart(context: Context) {
        NotificationManagerCompat.from(context).cancel(TAP_TO_START_NOTIFICATION_ID)
    }

    /** 알림 ID 를 alertId 로 나눈다. 음수가 되지 않게 절댓값을 쓴다. */
    private fun messageNotificationId(alertId: String): Int =
        MESSAGE_NOTIFICATION_ID_BASE + (alertId.hashCode().mod(1000))

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 가족 위치 보기 화면을 바로 여는 인텐트.
     *
     * `MainActivity` 가 `EXTRA_OPEN_GROUP_ID` 를 보고 그 화면으로 보낸다.
     */
    private fun openFamilyLocationIntent(
        context: Context,
        groupId: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_GROUP_ID, groupId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 서비스를 바로 부르는 PendingIntent('지금 중지').
     *
     * `getForegroundService` 를 쓰지 않고 `getService` 를 쓰는 이유: 멈추는 동작이라
     * 포그라운드로 올릴 필요가 없다. 이미 돌고 있는 서비스에 인텐트만 더 보낸다.
     */
    private fun servicePendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, LocationShareService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
