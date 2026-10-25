package io.github.ndrcb5210max.ansimlink.data.alert

import io.github.ndrcb5210max.ansimlink.domain.alert.AlertClassification
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertClassifier
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertNotification
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerDecision
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 알림 한 건을 처리한 결과. 리스너가 이 값을 보고 알림을 띄울지 정한다. */
data class AlertHandling(
    val classification: AlertClassification,
    val decision: TriggerDecision,
)

/**
 * 알림 한 건을 '분류 → 규칙 → 기록'까지 처리한다.
 *
 * 리스너(안드로이드 서비스)에서 떼어 둔 이유: 서비스는 알림을 받아 넘기는 일만 하고,
 * 판단과 기록은 여기 모아 두면 테스트와 읽기가 쉽다.
 *
 * [mutex] 로 한 건씩 처리한다. 같은 재난문자의 한국어판·영어판이 거의 동시에 올 수 있는데,
 * 중복 판단이 '최근 발동 기록 읽기 → 쓰기' 순서라서 겹쳐 돌면 둘 다 발동해 버린다.
 */
class AlertDetector(
    private val repository: AlertEventRepository,
    private val rulesStore: TriggerRulesStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun handle(notification: AlertNotification): AlertHandling = mutex.withLock {
        val nowMillis = now()
        val classification = AlertClassifier.classify(notification)
        val decision = TriggerEngine.decide(
            classification = classification,
            rules = rulesStore.rules.first(),
            previous = repository.recentTriggers(nowMillis),
            nowMillis = nowMillis,
        )
        repository.record(
            packageName = notification.packageName,
            classification = classification,
            decision = decision,
            nowMillis = nowMillis,
        )
        AlertHandling(classification, decision)
    }

    /** 디버그 빌드에서만 부른다. 허용 목록 밖 앱의 패키지 이름만 적는다. */
    suspend fun recordObservedPackage(packageName: String) =
        repository.recordObservedPackage(packageName, now())
}
