package io.github.ndrcb5210max.ansimlink.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.work.SafetyMessageWorker

/**
 * '가족에게 위치를 공유할까요?' 알림의 버튼을 받는다.
 *
 * 4단계에서 세 버튼을 모두 연결했다.
 * - 공유 시작 → 위치 포그라운드 서비스 + 안심 메시지
 * - 안심 메시지만 보내기 → 메시지만(위치는 수집하지 않는다)
 * - 닫기 → 알림만 치운다
 *
 * **이 경로가 기본 모드의 핵심이다.** Google Play 는 포그라운드 서비스 위치 사용을
 * '사용자가 시작한 동작의 연장'으로 본다. 사용자가 알림 버튼을 누른 것이 그 '동작'이라서
 * 배경 위치 권한 없이 시작할 수 있다.
 *
 * 왜 '공유 시작'에도 안심 메시지를 같이 보내는가: 받는 쪽이 앱을 안 켜 두고 있으면
 * 위치만 올라가도 아무도 모른다. 푸시가 가야 '지금 열어 봐야 한다'는 것을 안다.
 */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val severity = AlertSeverity.fromKey(intent.getStringExtra(EXTRA_SEVERITY).orEmpty())
            ?: AlertSeverity.Unknown
        val type = DisasterType.fromKey(intent.getStringExtra(EXTRA_TYPE).orEmpty())
            ?: DisasterType.Unknown

        when (intent.action) {
            ACTION_START_SHARING -> {
                Log.i(TAG, "'공유 시작'을 눌렀다")
                ShareController.start(context, ProvisionPurpose.AlertPrompt)
                SafetyMessageWorker.enqueue(context, severity, type, sharing = true)
            }

            ACTION_MESSAGE_ONLY -> {
                Log.i(TAG, "'안심 메시지만 보내기'를 눌렀다")
                // 위치는 건드리지 않는다. 메시지 발송에는 위치가 필요 없다.
                SafetyMessageWorker.enqueue(context, severity, type, sharing = false)
            }

            ACTION_DISMISS -> Log.i(TAG, "'닫기'를 눌렀다")

            else -> Log.w(TAG, "모르는 동작: " + intent.action)
        }

        NotificationManagerCompat.from(context)
            .cancel(AlertNotifications.TRIGGER_NOTIFICATION_ID)
    }

    companion object {
        private const val TAG = "AlertAction"

        private const val PREFIX = "io.github.ndrcb5210max.ansimlink.action."

        const val ACTION_START_SHARING = PREFIX + "START_SHARING"
        const val ACTION_MESSAGE_ONLY = PREFIX + "MESSAGE_ONLY"
        const val ACTION_DISMISS = PREFIX + "DISMISS"

        /** 어떤 재난문자 때문인지. 안심 메시지를 고를 때 쓴다([DisasterType.key]). */
        const val EXTRA_SEVERITY = PREFIX + "SEVERITY"
        const val EXTRA_TYPE = PREFIX + "TYPE"

        /**
         * 알림 버튼에 붙일 PendingIntent.
         *
         * `FLAG_UPDATE_CURRENT` 로 두면 같은 requestCode 의 기존 인텐트에 새 extras 가
         * 덮어 써진다. 발동마다 종류·유형이 다르므로 이게 필요하다.
         */
        fun pendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
            severity: AlertSeverity = AlertSeverity.Unknown,
            type: DisasterType = DisasterType.Unknown,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlertActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_SEVERITY, severity.key)
                .putExtra(EXTRA_TYPE, type.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
