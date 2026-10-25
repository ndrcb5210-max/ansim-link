package io.github.ndrcb5210max.ansimlink.service

import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose
import io.github.ndrcb5210max.ansimlink.domain.share.ShareWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 지금 돌고 있는 공유 세션 하나.
 *
 * @param sessionId 제공 기록과 위치 문서를 묶는 ID.
 * @param window 시작·종료 시각.
 * @param groupNames 공유 중인 그룹 이름. 상시 알림 문구에 쓴다.
 * @param receiverCount 내 위치를 받는 사람 수(그룹 전체 합, 나는 뺀다).
 * @param writeCount 지금까지 Firestore 에 쓴 위치 건수. 제공 기록에 남긴다.
 */
data class ActiveShareSession(
    val sessionId: String,
    val window: ShareWindow,
    val groupNames: List<String>,
    val receiverCount: Int,
    val purpose: ProvisionPurpose,
    val writeCount: Int = 0,
)

/**
 * 공유 세션 상태를 앱 안에서 돌려 보는 자리.
 *
 * 왜 companion 이 아니라 따로 두는가: 상태를 보는 쪽(홈 화면 배너, 가족 위치 보기)과
 * 바꾸는 쪽([LocationShareService])이 다르고, 시스템이 서비스를 죽였다 되살릴 수 있다.
 * 서비스 인스턴스에 상태를 두면 그때 사라진다.
 *
 * 이 값은 **화면에 보여 주기 위한 것**이다. 공유해도 되는지는 매번
 * [io.github.ndrcb5210max.ansimlink.domain.share.SharePolicy] 와 Firestore 의
 * 동의 상태로 다시 판단한다. 여기 값을 믿고 위치를 쓰지 않는다.
 */
object ShareSessionState {

    private val state = MutableStateFlow<ActiveShareSession?>(null)

    /** 공유 중이면 세션, 아니면 null. */
    val active: StateFlow<ActiveShareSession?> = state.asStateFlow()

    val isSharing: Boolean get() = state.value != null

    internal fun start(session: ActiveShareSession) {
        state.value = session
    }

    internal fun updateWriteCount(writeCount: Int) {
        state.value = state.value?.copy(writeCount = writeCount)
    }

    internal fun clear() {
        state.value = null
    }
}
