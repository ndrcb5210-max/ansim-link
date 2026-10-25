package io.github.ndrcb5210max.ansimlink.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.ndrcb5210max.ansimlink.MainActivity
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertClassification
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertPackages

/**
 * 재난문자를 감지했을 때 띄우는 알림.
 *
 * 3단계에서는 **묻기만 한다.** '공유 시작'·'안심 메시지만 보내기'를 눌러도 지금은 로그만 남고
 * 위치를 수집하거나 메시지를 보내지 않는다(그 연결은 4단계).
 *
 * 왜 알림으로 묻는가(CLAUDE.md 3장): Google Play 는 포그라운드 서비스 위치 사용을
 * '사용자가 시작한 동작의 연장'으로 본다. 기본 모드는 '한 번 눌러 시작'이라서
 * 배경 위치 권한이 필요 없다.
 */
object AlertNotifications {

    /** 발동 알림 채널. 바로 봐야 하는 알림이라 중요도를 높게 둔다. */
    const val TRIGGER_CHANNEL_ID = "disaster_trigger"

    /** 발동 알림 ID. 새 발동이 오면 이전 알림을 갈아 치운다. */
    const val TRIGGER_NOTIFICATION_ID = 2001

    /** 디버그 빌드의 '테스트 재난문자' 알림 ID. */
    const val DEBUG_TEST_NOTIFICATION_ID = 2002

    fun ensureChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)

        manager.createNotificationChannel(
            NotificationChannel(
                TRIGGER_CHANNEL_ID,
                context.getString(R.string.channel_trigger_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_trigger_description)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                AlertPackages.DEBUG_TEST_CHANNEL_ID,
                context.getString(R.string.channel_debug_test_name),
                // 테스트 알림은 흐름 점검용이라 조용히 띄운다.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.channel_debug_test_description)
            },
        )
    }

    /**
     * '가족에게 위치를 공유할까요?' 알림.
     *
     * 버튼 세 개: 공유 시작 / 안심 메시지만 보내기 / 닫기.
     */
    fun postTriggerPrompt(context: Context, classification: AlertClassification) {
        ensureChannels(context)

        val body = context.getString(
            R.string.trigger_notification_body,
            classification.severity.label,
            classification.type.label,
        )

        val notification = NotificationCompat.Builder(context, TRIGGER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(context.getString(R.string.trigger_notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_notification_alert,
                context.getString(R.string.trigger_action_share),
                actionIntent(context, AlertActionReceiver.ACTION_START_SHARING, 1),
            )
            .addAction(
                R.drawable.ic_notification_alert,
                context.getString(R.string.trigger_action_message_only),
                actionIntent(context, AlertActionReceiver.ACTION_MESSAGE_ONLY, 2),
            )
            .addAction(
                R.drawable.ic_notification_alert,
                context.getString(R.string.trigger_action_dismiss),
                actionIntent(context, AlertActionReceiver.ACTION_DISMISS, 3),
            )
            .build()

        // POST_NOTIFICATIONS 가 없으면(사용자가 거절) 조용히 아무 일도 일어나지 않는다.
        NotificationManagerCompat.from(context)
            .notify(TRIGGER_NOTIFICATION_ID, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, AlertActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
