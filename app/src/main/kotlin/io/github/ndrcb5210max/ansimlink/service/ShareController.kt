package io.github.ndrcb5210max.ansimlink.service

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose

/**
 * 공유 서비스를 시작·중지하는 한 군데.
 *
 * 세 시작 경로(알림 버튼, 앱 화면, 자동 시작)가 모두 여기를 지난다. 그러지 않으면
 * 백그라운드 시작 제한을 다루는 코드가 세 군데로 흩어진다.
 *
 * ## 왜 시작이 실패할 수 있나
 * 앱이 백그라운드에 있을 때는 ACCESS_BACKGROUND_LOCATION 없이 위치 포그라운드 서비스를
 * 만들 수 없다(공식 문서). 사용자가 알림을 눌러 시작하는 것은 예외로 허용된다.
 * 그래서 `startForegroundService` 자체가 `ForegroundServiceStartNotAllowedException`
 * (Android 12+)으로 터질 수 있고, 통과해도 서비스 안의 `startForeground` 에서 터질 수 있다.
 * 두 자리를 모두 막아 두었다(여기와 [LocationShareService.promoteToForeground]).
 */
object ShareController {

    private const val TAG = "ShareController"

    /**
     * 공유를 시작한다.
     *
     * @return 서비스를 띄우는 데 성공했으면 true. 실제로 공유가 시작됐는지는 서비스가
     *   동의·권한을 확인한 뒤에 정하므로, true 라도 '시작하지 못했습니다' 알림이 뜰 수 있다.
     */
    fun start(context: Context, purpose: ProvisionPurpose): Boolean {
        // '눌러서 시작' 알림이 떠 있었으면 치운다. 눌렀으니 할 일이 끝났다.
        ShareNotifications.cancelTapToStart(context)

        val intent = LocationShareService.startIntent(context, purpose)
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (error: IllegalStateException) {
            // Android 12+ 의 ForegroundServiceStartNotAllowedException 이
            // IllegalStateException 의 하위 타입이다. minSdk 26 이라 타입을 직접 잡으면
            // 낮은 버전에서 클래스를 찾을 수 없으므로 상위 타입으로 받는다.
            Log.w(TAG, "서비스를 띄우지 못했다(백그라운드 시작 제한)", error)
            onStartFailed(context, purpose)
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "서비스를 띄우지 못했다(권한)", error)
            onStartFailed(context, purpose)
            false
        }
    }

    /** 공유를 멈춘다. 돌고 있지 않으면 아무 일도 없다. */
    fun stop(context: Context) {
        try {
            context.startService(LocationShareService.stopIntent(context))
        } catch (error: IllegalStateException) {
            // 서비스가 이미 서 있으면 백그라운드에서 startService 가 막힌다.
            // 멈추려던 것이므로 그대로 두면 된다.
            Log.i(TAG, "서비스가 이미 서 있다", error)
        }
    }

    /**
     * 시작이 막혔을 때.
     *
     * 자동 시작이었으면 '눌러서 시작' 알림으로 바꾼다. 사용자가 직접 눌러 시작하는
     * 경로는 예외로 허용되므로 그쪽으로 돌린다.
     */
    private fun onStartFailed(context: Context, purpose: ProvisionPurpose) {
        if (purpose == ProvisionPurpose.AlertAuto) {
            ShareNotifications.postTapToStart(
                context,
                context.getString(R.string.sharing_tap_to_start_body),
            )
        } else {
            ShareNotifications.postShareFailed(
                context,
                context.getString(R.string.sharing_failed_foreground),
            )
        }
    }
}
