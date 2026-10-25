package io.github.ndrcb5210max.ansimlink.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ndrcb5210max.ansimlink.AnsimLinkApplication
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.message.SafetyMessages
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 안심 메시지를 그룹에 보낸다.
 *
 * 하는 일은 `groups/{gid}/alerts/{id}` 문서를 만드는 것까지다. **앱은 FCM 을 직접 보내지
 * 않는다** — 발송에는 서버 자격 증명이 필요하고, 그 키를 앱에 넣으면 누구나 꺼내서
 * 아무에게나 푸시를 보낼 수 있다. 문서를 보고 보내는 일은 `functions/` 의
 * `onAlertCreated` 가 한다.
 *
 * ## 왜 WorkManager 인가
 * 발동은 재난 상황에 일어난다. 그때 네트워크가 끊겼다 붙거나, 알림 버튼을 누른 직후
 * 사용자가 앱을 닫거나, 시스템이 프로세스를 거둬 갈 수 있다. WorkManager 는 그 모든
 * 경우에 작업을 **나중에 다시** 돌려 준다. 코루틴만 띄우면 프로세스가 죽는 순간 사라진다.
 *
 * 위치 공유와 달리 메시지 발송은 위치가 필요 없으므로, 규칙에 맞으면 사용자가 아무것도
 * 누르지 않아도 보낼 수 있다(CLAUDE.md 3장).
 */
class SafetyMessageWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? AnsimLinkApplication)?.container
            ?: return Result.failure()

        if (!container.isServerReady) {
            // 가짜 설정(CI·미리보기)에서는 보낼 곳이 없다. 재시도해도 달라지지 않는다.
            Log.i(TAG, "서버 설정이 없어 보내지 않는다")
            return Result.failure()
        }

        val user = container.authRepository.currentUserNow()
            ?: run {
                Log.w(TAG, "로그인하지 않아 보내지 않는다")
                return Result.failure()
            }

        val severity = AlertSeverity.fromKey(inputData.getString(KEY_SEVERITY).orEmpty())
            ?: AlertSeverity.Unknown
        val type = DisasterType.fromKey(inputData.getString(KEY_TYPE).orEmpty())
            ?: DisasterType.Unknown
        val sharing = inputData.getBoolean(KEY_SHARING, false)

        val messages = container.safetyMessageStore.messages.first()
        // 요금제를 이 순간에 다시 읽는다(LocationShareService 와 같은 이유 — 작업이
        // 돌 때 프로세스가 방금 깨어난 상태일 수 있다). 무료면 저장된 유형별 문구가
        // 남아 있어도 쓰지 않는다. 그 판정은 SafetyMessages.select 가 한다.
        val plan = container.planProvider.currentPlan()
        val message = SafetyMessages.select(messages, type, plan)

        val groups = runCatching { container.shareRepository.myGroupsOnce(user.uid) }
            .getOrElse { error ->
                Log.w(TAG, "그룹을 읽지 못했다", error)
                // 네트워크 때문일 수 있다. 다시 시도한다.
                return Result.retry()
            }

        if (groups.isEmpty()) {
            Log.i(TAG, "보낼 그룹이 없다")
            return Result.success()
        }

        val nowMillis = System.currentTimeMillis()
        var failed = false

        groups.forEach { entry ->
            val me = entry.memberOf(user.uid)
            // 동의를 철회했거나 일시중지한 그룹에는 보내지 않는다.
            //
            // 메시지 자체에는 위치가 없지만, '일시중지' 를 누른 사람은 이 그룹에 내 소식을
            // 보내지 않겠다는 뜻으로 눌렀을 것이다. 위치만 멈추고 메시지는 계속 보내면
            // 사용자가 예상한 것과 다르게 동작한다.
            if (me == null || !me.shareState.sharesLocation) {
                Log.i(TAG, "동의·일시중지 상태라 건너뛴다: " + entry.group.id)
                return@forEach
            }
            runCatching {
                container.shareRepository.createAlert(
                    groupId = entry.group.id,
                    senderUid = user.uid,
                    senderName = me.displayName.ifBlank { user.label },
                    severity = severity,
                    type = type,
                    message = message,
                    sharing = sharing,
                    createdAtMillis = nowMillis,
                )
            }.onFailure { error ->
                Log.w(TAG, "안심 메시지를 만들지 못했다: " + entry.group.id, error)
                failed = true
            }
        }

        // 한 그룹이라도 실패했으면 다시 시도한다.
        //
        // 대가: 성공한 그룹에는 메시지가 두 번 갈 수 있다. 재난 상황에서 '못 보내는 것'이
        // '두 번 보내는 것'보다 나쁘다고 보고 재시도를 골랐다. 서버가 보내는 알림은
        // 같은 알림 ID 로 덮어 쓰이므로(ShareNotifications) 받는 쪽에 두 줄로 쌓이지는 않는다.
        return if (failed) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "SafetyMessage"

        private const val KEY_SEVERITY = "severity"
        private const val KEY_TYPE = "type"
        private const val KEY_SHARING = "sharing"

        /** 같은 발동으로 작업이 두 번 쌓이지 않게 하는 이름. */
        private const val WORK_NAME = "safety-message"

        /**
         * 안심 메시지 발송 작업을 넣는다.
         *
         * [ExistingWorkPolicy.REPLACE] 를 쓴 이유: 같은 재난문자로 '공유 시작'과
         * '안심 메시지만 보내기'를 연달아 누를 수 있다. 그때 두 번 보내지 않고
         * 나중에 누른 쪽으로 바꾼다. 서로 다른 재난문자는 10분 중복 검사가 이미 걸러 낸다
         * (3단계 TriggerEngine).
         */
        fun enqueue(
            context: Context,
            severity: AlertSeverity,
            type: DisasterType,
            sharing: Boolean,
        ) {
            val request = OneTimeWorkRequestBuilder<SafetyMessageWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_SEVERITY, severity.key)
                        .putString(KEY_TYPE, type.key)
                        .putBoolean(KEY_SHARING, sharing)
                        .build(),
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                // 10초부터 시작해 늘려 간다. 기본값(30초)보다 짧게 둔 것은
                // 재난 상황에서 몇 분씩 기다릴 수 없기 때문이다.
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
