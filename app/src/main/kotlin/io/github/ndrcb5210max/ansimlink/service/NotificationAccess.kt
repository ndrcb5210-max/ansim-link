package io.github.ndrcb5210max.ansimlink.service

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * 알림 접근 권한(그리고 그 설정 화면) 다루기.
 *
 * 알림 접근은 런타임 권한 요청으로 받을 수 없다. 사용자가 시스템 설정에서 직접 켜야 한다.
 * 그래서 앱이 할 수 있는 일은 ① 켜져 있는지 확인하고 ② 그 설정 화면을 열어 주는 것뿐이다.
 */
object NotificationAccess {

    private const val TAG = "NotificationAccess"

    /**
     * 우리 리스너가 켜져 있는지.
     *
     * `NotificationManager.isNotificationListenerAccessGranted` 는 API 27 부터인데
     * 이 앱의 `minSdk` 는 26 이다. [NotificationManagerCompat.getEnabledListenerPackages] 는
     * 설정 문자열을 직접 읽어서 버전을 가리지 않으므로 이쪽을 쓴다.
     * 우리 앱의 리스너는 하나뿐이므로 패키지 이름만 확인하면 충분하다.
     */
    fun isGranted(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun listenerComponent(context: Context) =
        ComponentName(context, DisasterAlertListenerService::class.java)

    /**
     * 알림 접근 설정 화면을 연다.
     *
     * 1. API 30 이상: 우리 앱 리스너의 상세 설정 화면으로 바로 간다.
     * 2. 그 화면이 없으면 알림 접근 목록 화면.
     * 3. 그것도 없으면 앱 정보 화면(제한된 설정 안내를 따라가야 하는 경우에도 쓸모가 있다).
     *
     * 기기 제조사에 따라 1·2 가 모두 없을 수 있어서 단계마다
     * [ActivityNotFoundException] 을 받아 다음으로 넘어간다.
     *
     * @return 화면을 열었으면 true.
     */
    fun openSettings(context: Context): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        listenerComponent(context).flattenToString(),
                    ),
                )
            }
            add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            add(appDetailsIntent(context))
        }

        candidates.forEach { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (error: ActivityNotFoundException) {
                Log.w(TAG, "설정 화면을 열 수 없다: " + intent.action, error)
            }
        }
        return false
    }

    /** '앱 정보' 화면. 사이드로드 설치에서 '제한된 설정 허용'을 누르려면 여기로 가야 한다. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    fun openAppDetails(context: Context): Boolean = try {
        context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: ActivityNotFoundException) {
        Log.w(TAG, "앱 정보 화면을 열 수 없다", error)
        false
    }
}
