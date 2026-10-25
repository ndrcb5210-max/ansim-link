package io.github.ndrcb5210max.ansimlink.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * '가족에게 위치를 공유할까요?' 알림의 버튼을 받는다.
 *
 * **3단계에서는 로그만 남긴다.** 위치 수집·전송과 안심 메시지 발송은 4단계다.
 * 지금 연결해 두면 동의 화면도, 포그라운드 서비스도, 상시 알림도 없는 상태로 위치를 켜는 셈이라
 * CLAUDE.md 원칙 1·2를 어기게 된다.
 */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START_SHARING ->
                Log.i(TAG, "[4단계 연결 예정] '공유 시작'을 눌렀다")
            ACTION_MESSAGE_ONLY ->
                Log.i(TAG, "[4단계 연결 예정] '안심 메시지만 보내기'를 눌렀다")
            ACTION_DISMISS ->
                Log.i(TAG, "'닫기'를 눌렀다")
            else ->
                Log.w(TAG, "모르는 동작: " + intent.action)
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
    }
}
