package io.github.ndrcb5210max.ansimlink.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

/** Firebase 설정 상태. */
enum class ServerStatus {
    /** 진짜 설정이 들어 있어 서버와 통신할 수 있다. */
    Ready,

    /** google-services.json 이 없거나 자리표시자 값이다. 앱은 안내만 보여 준다. */
    NotConfigured,
}

/**
 * Firebase 설정이 제대로 들어 있는지 확인한다.
 *
 * 이 저장소에는 진짜 `app/google-services.json` 을 넣지 않는다(CLAUDE.md 원칙 6).
 * CI 와 로컬에서는 `scripts/fake-google-services.sh` 가 만든 자리표시자 설정으로 빌드하므로,
 * 앱이 그대로 서버를 부르면 알 수 없는 오류로 죽는다.
 *
 * 그래서 시작할 때 한 번 확인하고, 설정이 없거나 가짜면 화면에 '서버 설정이 필요합니다'를 보여 준다.
 */
object ServerConfig {

    private const val TAG = "ServerConfig"

    /** scripts/fake-google-services.sh 가 써 넣는 값. 이 값이면 진짜 설정이 아니다. */
    const val PLACEHOLDER_PROJECT_ID = "ansim-link-placeholder"

    /** 자리표시자 앱 ID 의 앞부분(프로젝트 번호가 0뿐이다). */
    private const val PLACEHOLDER_APP_ID_PREFIX = "1:000000000000:"

    fun status(context: Context): ServerStatus {
        val app = try {
            // google-services.json 이 없으면 FirebaseApp 이 아예 만들어지지 않는다.
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            Log.i(TAG, "Firebase 가 초기화되지 않았다. 서버 설정 안내를 보여 준다.", e)
            return ServerStatus.NotConfigured
        }

        val options = app.options
        val placeholder = options.projectId == PLACEHOLDER_PROJECT_ID ||
            options.applicationId.startsWith(PLACEHOLDER_APP_ID_PREFIX)

        return if (placeholder) ServerStatus.NotConfigured else ServerStatus.Ready
    }
}
