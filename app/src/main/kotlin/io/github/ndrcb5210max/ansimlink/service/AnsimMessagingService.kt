package io.github.ndrcb5210max.ansimlink.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.ndrcb5210max.ansimlink.AnsimLinkApplication
import kotlinx.coroutines.launch

/**
 * FCM 수신.
 *
 * 서버(`functions/onAlertCreated`, `onShareStarted`)가 **데이터 메시지**로 보낸다.
 * `notification` 블록을 쓰지 않는 이유: 그러면 앱이 백그라운드일 때 시스템이 알림을
 * 직접 띄워 버려서 버튼([위치 보기])을 붙일 수 없고, 알림 ID 를 우리가 정할 수 없어
 * 같은 메시지가 두 줄로 쌓인다. 데이터 메시지는 항상 [onMessageReceived] 로 와서
 * 우리가 [ShareNotifications] 로 모양을 정할 수 있다.
 *
 * 대가: 데이터 메시지는 앱이 강제 종료된 상태에서는 오지 않을 수 있고, Doze 중에는
 * 늦게 올 수 있다. 그래서 서버는 `priority: high` 로 보낸다(functions/ 참고).
 */
class AnsimMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "새 토큰을 받았다")
        registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data[KEY_TYPE]
        val groupId = data[KEY_GROUP_ID].orEmpty()
        val alertId = data[KEY_ALERT_ID].orEmpty()
        val senderName = data[KEY_SENDER_NAME].orEmpty()

        when (type) {
            TYPE_SAFETY_MESSAGE -> ShareNotifications.postSafetyMessage(
                context = this,
                senderName = senderName,
                message = data[KEY_MESSAGE].orEmpty(),
                groupId = groupId,
                alertId = alertId,
                // 보낸 사람이 위치 공유도 시작했을 때만 [위치 보기]를 붙인다.
                // 위치가 없는데 버튼이 있으면 눌러도 빈 화면이 나온다.
                showLocationAction = data[KEY_SHARING] == "true",
            )

            TYPE_SHARE_STARTED -> ShareNotifications.postShareStarted(
                context = this,
                senderName = senderName,
                groupId = groupId,
                alertId = alertId,
            )

            else -> Log.w(TAG, "모르는 메시지 종류: " + type)
        }
    }

    /**
     * 토큰을 Firestore 에 등록한다.
     *
     * 애플리케이션 범위에서 돌린다. 이 서비스는 [onNewToken] 이 끝나면 바로 거둬 가므로
     * 서비스 범위의 코루틴은 중간에 취소될 수 있다.
     */
    private fun registerToken(token: String) {
        val container = (application as? AnsimLinkApplication)?.container ?: return
        if (!container.isServerReady) return
        val uid = container.authRepository.currentUserNow()?.uid ?: return

        AnsimLinkApplication.applicationScope.launch {
            runCatching {
                container.shareRepository.registerPushToken(
                    uid = uid,
                    token = token,
                    nowMillis = System.currentTimeMillis(),
                )
            }.onFailure { Log.w(TAG, "토큰을 등록하지 못했다", it) }
        }
    }

    companion object {
        private const val TAG = "AnsimMessaging"

        // 서버가 보내는 데이터 키. functions/src/messages.ts 와 같아야 한다.
        const val KEY_TYPE = "type"
        const val KEY_GROUP_ID = "groupId"
        const val KEY_ALERT_ID = "alertId"
        const val KEY_SENDER_NAME = "senderName"
        const val KEY_MESSAGE = "message"
        const val KEY_SHARING = "sharing"

        const val TYPE_SAFETY_MESSAGE = "safety_message"
        const val TYPE_SHARE_STARTED = "share_started"
    }
}
