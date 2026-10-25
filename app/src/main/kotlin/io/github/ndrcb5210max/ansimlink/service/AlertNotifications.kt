package io.github.ndrcb5210max.ansimlink.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.ndrcb5210max.ansimlink.MainActivity
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertClassification
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertPackages

/**
 * 재난문자를 감지했을 때 띄우는 알림.
 *
 * 왜 알림으로 묻는가(CLAUDE.md 3장): Google Play 는 포그라운드 서비스 위치 사용을
 * '사용자가 시작한 동작의 연장'으로 본다. 기본 모드는 '한 번 눌러 시작'이라서
 * 배경 위치 권한이 필요 없다.
 *
 * 4단계에서 버튼 세 개를 실제로 연결했다([AlertActionReceiver]).
 * 공유 중 상시 알림은 채널이 다르다([ShareNotifications]) — 사용자가 이 발동 알림을
 * 꺼도 '공유 중' 알림은 남아야 한다.
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
                actionIntent(context, AlertActionReceiver.ACTION_START_SHARING, 1, classification),
            )
            .addAction(
                R.drawable.ic_notification_alert,
                context.getString(R.string.trigger_action_message_only),
                actionIntent(context, AlertActionReceiver.ACTION_MESSAGE_ONLY, 2, classification),
            )
            .addAction(
                R.drawable.ic_notification_alert,
                context.getString(R.string.trigger_action_dismiss),
                actionIntent(context, AlertActionReceiver.ACTION_DISMISS, 3, classification),
            )
            .build()

        // 권한을 거절했으면 알림은 뜨지 않는다. 그래도 발동 기록은 이미 남았으므로
        // 발동 기록 화면에서 확인할 수 있다.
        post(context, TRIGGER_NOTIFICATION_ID, notification)
    }

    /**
     * 알림 하나를 띄운다. 권한이 없거나 사용자가 알림을 꺼 두었으면 아무 일도 하지 않는다.
     *
     * 권한 검사를 다른 함수로 빼지 않고 여기에 그대로 적은 이유: lint 의 MissingPermission
     * 검사는 `notify` 를 부르는 **같은 함수 안에 있는** checkSelfPermission 만 알아본다.
     * 검사를 helper 로 빼면 lint 가 오류를 낸다.
     *
     * @return 실제로 띄웠으면 true.
     */
    fun post(context: Context, id: Int, notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        // Android 12 이하에는 런타임 권한이 없다. 설정에서 알림을 껐는지만 볼 수 있다.
        if (!manager.areNotificationsEnabled()) return false
        manager.notify(id, notification)
        return true
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 알림 버튼의 인텐트. 어떤 재난문자였는지 같이 실어 보낸다.
     *
     * 4단계에서 extras 를 더했다. 안심 메시지를 고를 때 재난 유형이 필요하고
     * (프리미엄은 유형별 문구), 서버가 보낼 알림에도 종류·유형이 들어간다.
     */
    private fun actionIntent(
        context: Context,
        action: String,
        requestCode: Int,
        classification: AlertClassification,
    ): PendingIntent = AlertActionReceiver.pendingIntent(
        context = context,
        action = action,
        requestCode = requestCode,
        severity = classification.severity,
        type = classification.type,
    )
}
