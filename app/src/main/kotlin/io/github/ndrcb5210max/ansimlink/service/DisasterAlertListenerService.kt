package io.github.ndrcb5210max.ansimlink.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.ndrcb5210max.ansimlink.AnsimLinkApplication
import io.github.ndrcb5210max.ansimlink.AppContainer
import io.github.ndrcb5210max.ansimlink.BuildConfig
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertNotification
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertPackages
import io.github.ndrcb5210max.ansimlink.domain.share.AutoStartFlow
import io.github.ndrcb5210max.ansimlink.domain.share.ShareStartMode
import io.github.ndrcb5210max.ansimlink.work.SafetyMessageWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 재난문자 앱이 띄운 알림을 읽어 발동 여부를 판단하는 리스너.
 *
 * 왜 알림을 읽는가(CLAUDE.md 3장): 일반 앱은 셀 브로드캐스트를 직접 받을 수 없다.
 * `RECEIVE_EMERGENCY_BROADCAST` 는 signature|privileged 권한이고, 한국 재난문자 채널(4370~4392)은
 * 기본 재난문자 앱에만 전달된다(AOSP CellBroadcastService). 그래서 그 앱이 띄운 알림을 본다.
 *
 * 알림 접근 권한은 **휴대폰의 모든 알림**을 볼 수 있으므로, 여기서 가장 중요한 일은
 * **허용 목록 밖 알림을 곧바로 버리는 것**이다. 패키지 이름(과 디버그 테스트용 채널 ID)만 보고
 * 아니면 제목·본문을 읽지 않고 돌아 나간다.
 */
class DisasterAlertListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        AlertNotifications.ensureChannels(this)
        connectedState.value = true
        Log.i(TAG, "알림 접근이 연결됐다")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connectedState.value = false
        Log.i(TAG, "알림 접근이 끊겼다")
    }

    override fun onDestroy() {
        connectedState.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        val packageName = statusBarNotification.packageName ?: return

        // 우리 앱이 띄운 알림은 테스트 알림만 본다. '위치를 공유할까요?' 알림까지 다시 읽어
        // 기록을 더럽히지 않게 한다.
        val channelId = runCatching { statusBarNotification.notification?.channelId }.getOrNull()

        if (!AlertPackages.shouldInspect(
                packageName = packageName,
                channelId = channelId,
                selfPackageName = this.packageName,
                allowSelf = BuildConfig.DEBUG,
            )
        ) {
            // 허용 목록 밖이다. 제목·본문은 읽지 않는다.
            // 디버그 빌드에서만 '어떤 앱이 알림을 띄우는지' 패키지 이름만 적어 둔다
            // (실기기에서 삼성 재난문자 앱의 패키지 이름을 찾기 위한 것이다).
            if (BuildConfig.DEBUG && packageName != this.packageName) {
                scope.launch { container()?.alertDetector?.recordObservedPackage(packageName) }
            }
            return
        }

        val extras = statusBarNotification.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()

        handle(
            AlertNotification(
                packageName = packageName,
                title = title,
                body = body,
                channelId = channelId,
            ),
        )
    }

    /**
     * 분류 → 규칙 → 기록 → 발동.
     *
     * 발동하면 하는 일 두 가지.
     * 1. **안심 메시지**는 모드와 상관없이 규칙에 맞으면 보낸다. 위치가 필요 없으므로
     *    사용자가 아무것도 누르지 않아도 된다(CLAUDE.md 3장).
     * 2. **위치 공유**는 모드에 따라 다르다.
     *    - 기본 모드: '위치를 공유할까요?' 알림을 띄우고 사용자가 누를 때 시작한다.
     *    - 자동 시작 모드: 바로 시작한다. 실패하면 '눌러서 시작' 알림으로 바꾼다.
     */
    private fun handle(notification: AlertNotification) {
        val container = container() ?: return
        val detector = container.alertDetector
        scope.launch {
            val handling = detector.handle(notification)
            Log.i(
                TAG,
                "감지: " + handling.classification.severity.key +
                    " / " + handling.classification.type.key +
                    " → " + handling.decision.reason.key,
            )
            if (!handling.decision.triggered) return@launch

            val classification = handling.classification

            // ① 안심 메시지. 규칙에 맞아 발동했으므로 바로 보낸다.
            SafetyMessageWorker.enqueue(
                context = applicationContext,
                severity = classification.severity,
                type = classification.type,
                // 자동 시작 모드면 위치도 같이 간다고 알린다. 실패하면 받는 쪽 알림의
                // [위치 보기]가 빈 화면을 열 수 있는데, 그때 '위치를 아직 받지 못했습니다'
                // 로 보인다(FamilyLocationScreen).
                sharing = effectiveMode(container).isAuto,
            )

            // ② 위치 공유.
            if (effectiveMode(container).isAuto) {
                startAutomatically()
            } else {
                AlertNotifications.postTriggerPrompt(applicationContext, classification)
            }
        }
    }

    /**
     * 자동 시작 모드로 공유를 시작해 본다.
     *
     * 실패할 수 있다. 앱이 백그라운드에 있을 때는 배경 위치 권한 없이 위치 포그라운드
     * 서비스를 만들 수 없고, 권한이 있어도 기기 상태에 따라 막힐 수 있다.
     * [ShareController] 가 그 경우 '눌러서 시작' 알림으로 바꾼다.
     */
    private fun startAutomatically() {
        val started = ShareController.start(applicationContext, ProvisionPurpose.AlertAuto)
        if (!started) {
            Log.i(TAG, "자동 시작이 막혀 '눌러서 시작' 알림으로 바꿨다")
        }
    }

    /**
     * 지금 실제로 쓰는 발동 모드.
     *
     * 설정 스위치만 믿지 않는다. 사용자가 시스템 설정에서 위치 권한을 '항상 허용'에서
     * 내렸을 수 있으므로 권한을 매번 같이 본다
     * ([io.github.ndrcb5210max.ansimlink.domain.share.AutoStartFlow.effectiveMode]).
     */
    private suspend fun effectiveMode(container: AppContainer): ShareStartMode =
        AutoStartFlow.effectiveMode(
            autoStartEnabled = container.shareSettingsStore.settings.first().autoStartEnabled,
            locationPermissionGranted =
            LocationShareService.hasLocationPermission(applicationContext),
            backgroundLocationGranted =
            LocationShareService.hasBackgroundLocationPermission(applicationContext),
        )

    private fun container(): AppContainer? =
        (application as? AnsimLinkApplication)?.container

    companion object {
        private const val TAG = "AlertListener"

        private val connectedState = MutableStateFlow(false)

        /**
         * 리스너가 지금 연결돼 있는지. 홈 화면의 '재난문자 감지' 상태에 쓴다.
         *
         * 시스템이 서비스를 묶었다 풀 수 있으므로 인스턴스가 아니라 companion 에 둔다.
         * '권한이 켜져 있는지'는 [NotificationAccess.isGranted] 로 따로 본다.
         * 권한은 켜져 있어도 시스템이 서비스를 아직 묶지 않았을 수 있다.
         */
        val connected: StateFlow<Boolean> = connectedState.asStateFlow()
    }
}
