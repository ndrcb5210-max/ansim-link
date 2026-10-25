package io.github.ndrcb5210max.ansimlink.data.billing

import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.domain.billing.Entitlement
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 지금 적용되는 요금제를 한 군데에서 내놓는다.
 *
 * ## 왜 따로 두는가
 * 요금제를 보는 곳이 다섯 군데다. 그룹 한도(만들기·초대), 공유 시간 상한, 유형별 안심
 * 메시지, 결제 화면, 설정 > 구독. 4단계까지는 그 다섯 곳에 `Plan.Free` 가 하드코딩되어
 * 있었고, 거기에 각각 '서버에서 읽어 오기'를 적으면 **다섯 군데가 서로 다른 판정을 하게
 * 된다.** 특히 '언제 무료로 떨어지는가'(기간 만료)가 어긋나면 화면은 프리미엄인데
 * 저장은 무료 한도로 되는 상태가 생긴다.
 *
 * 그래서 판정은 [io.github.ndrcb5210max.ansimlink.domain.billing.Entitlement.planAt] 하나,
 * 그것을 부르는 곳은 이 클래스 하나로 둔다.
 *
 * ## 서버 설정이 없으면
 * `serverReady` 가 false 면(가짜 google-services.json, CI·스크린샷 테스트) Firestore 를
 * 아예 건드리지 않고 늘 무료다. 4단계의 다른 저장소들과 같은 규칙이다.
 *
 * @param now 시계. 기간 만료 판정에 쓴다. 테스트에서 바꿀 수 있게 인자로 받는다.
 */
class PlanProvider(
    private val authRepository: AuthRepository,
    private val entitlements: EntitlementRepository,
    private val serverReady: Boolean,
    scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * 서버가 검증한 구독 상태. 로그인한 사람이 바뀌면 그 사람의 것으로 갈아탄다.
     *
     * 로그아웃 상태에서는 [Entitlement.none] 이다. 남의 구독 상태가 남아 있으면 안 된다.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entitlement: StateFlow<Entitlement> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null || !serverReady) {
                flowOf(Entitlement.none)
            } else {
                entitlements.observe(user.uid)
            }
        }
        .stateIn(
            scope = scope,
            // 앱이 사는 동안 계속 구독한다. 화면이 없을 때도 알아야 하기 때문이다
            // (발동은 앱이 닫혀 있을 때 일어난다 — 그때 공유 시간 상한을 정해야 한다).
            started = SharingStarted.Eagerly,
            initialValue = Entitlement.none,
        )

    /**
     * 화면이 구독하는 요금제.
     *
     * 시계를 흐름 안에서 읽으므로, 구독 문서가 바뀔 때마다 기간도 다시 본다.
     * **기간이 지나는 '그 순간'에는 다시 흐르지 않는다** — 타이머를 두지 않았다.
     * 쓰는 쪽이 모두 '동작을 시작할 때' 요금제를 다시 보므로(아래 [currentPlan])
     * 실제 한도 적용은 어긋나지 않는다. 화면 표시만 다음 갱신까지 늦을 수 있다.
     */
    val plan: StateFlow<Plan> = entitlement
        .map { it.planAt(now()) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = Plan.Free,
        )

    /**
     * 지금 요금제를 즉시 읽는다.
     *
     * 포그라운드 서비스와 WorkManager 작업이 쓴다. 그쪽은 흐름을 구독할 수명이 없고,
     * '동작을 시작하는 그 순간'의 값이 필요하다.
     *
     * 흐름의 마지막 값을 쓰지 않고 서버에 다시 묻는 이유: 앱 프로세스가 방금 깨어난
     * 경우(재난문자 알림으로 발동) 흐름이 아직 첫 값을 못 받았을 수 있다. 그때
     * 초깃값인 무료를 쓰면 **돈을 낸 사람이 60분만 공유하게 된다.**
     *
     * 서버를 못 읽으면 무료다. 막는 방향으로 틀린다.
     */
    suspend fun currentPlan(): Plan = currentEntitlement().planAt(now())

    /** [currentPlan] 과 같은 경로로 구독 상태 자체를 읽는다. */
    suspend fun currentEntitlement(): Entitlement {
        val user = authRepository.currentUserNow() ?: return Entitlement.none
        if (!serverReady) return Entitlement.none
        return entitlements.readOnce(user.uid)
    }

    /**
     * 서버가 프리미엄을 적어 주기를 기다린다. 결제가 끝난 직후에 쓴다.
     *
     * 왜 기다리는가: 결제 흐름이 끝나면 앱이 `verifySubscription` 을 부르고, 그 함수가
     * `users/{uid}.entitlement` 를 쓴다. 쓰기가 Firestore 구독으로 돌아오기까지 한 박자가
     * 있다. 그 사이에 결제 화면이 '무료'를 그대로 보여 주면 사용자는 결제가 실패한 줄
     * 안다.
     *
     * 시간이 다 되면 마지막으로 한 번 더 직접 읽고 그 값을 돌려준다. 구독 흐름이
     * (오프라인 등으로) 늦었을 뿐 서버에는 이미 적혀 있을 수 있다. 그래도 프리미엄이
     * 아니면 화면이 '확인이 늦어지고 있다'고 적는다 — **열어 주지 않는다.**
     */
    suspend fun awaitPremium(timeoutMillis: Long): Entitlement =
        withTimeoutOrNull(timeoutMillis) {
            entitlement.first { it.planAt(now()) == Plan.Premium }
        } ?: currentEntitlement()
}
