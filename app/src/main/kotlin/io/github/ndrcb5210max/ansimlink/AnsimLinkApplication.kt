package io.github.ndrcb5210max.ansimlink

import android.app.Application
import io.github.ndrcb5210max.ansimlink.service.AlertNotifications
import io.github.ndrcb5210max.ansimlink.service.ShareNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 앱 시작점. [AppContainer] 를 하나 만들어 두기만 한다.
 *
 * 컨테이너 안의 Firebase 객체는 모두 지연 생성이라 여기서는 아무것도 초기화하지 않는다.
 * 설정이 없어도 앱이 죽지 않아야 하기 때문이다.
 */
class AnsimLinkApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)

        // 알림 채널은 미리 만들어 둔다. 채널이 없으면 알림이 조용히 사라진다.
        // 둘 다 리소스만 읽으므로 Firebase 설정과 무관하다.
        AlertNotifications.ensureChannels(this)
        ShareNotifications.ensureChannels(this)

        applicationScope.launch {
            // 서비스가 비정상 종료되면 제공 기록이 '아직 공유 중'으로 남는다.
            // 앱을 켤 때 한 번 정리한다(예정 종료 시각이 지난 것만 닫는다).
            runCatching {
                container.provisionRecordRepository
                    .closeStaleRecords(System.currentTimeMillis())
            }
        }
    }

    companion object {
        /**
         * 앱이 사는 동안 이어져야 하는 작업을 돌리는 자리.
         *
         * 쓰는 곳: 공유를 멈출 때의 정리 작업(제공 기록의 종료 시각 채우기,
         * `sharing.active` 끄기). 그 작업은 **서비스가 선 뒤에도 끝나야 한다.**
         * 서비스 범위에서 돌리면 `stopSelf` 와 함께 취소되어 기록이 열린 채 남는다.
         *
         * [SupervisorJob] 이라 한 작업이 실패해도 나머지가 멈추지 않는다.
         */
        val applicationScope: CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
