package io.github.ndrcb5210max.ansimlink.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.ndrcb5210max.ansimlink.AnsimLinkApplication
import io.github.ndrcb5210max.ansimlink.AppContainer
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.share.LocationPoint
import io.github.ndrcb5210max.ansimlink.domain.share.LocationWriteRule
import io.github.ndrcb5210max.ansimlink.domain.share.SharePolicy
import io.github.ndrcb5210max.ansimlink.domain.share.ShareTarget
import io.github.ndrcb5210max.ansimlink.domain.share.ShareWindow
import io.github.ndrcb5210max.ansimlink.ui.share.ShareBlockMessages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * 위치 공유 포그라운드 서비스(`foregroundServiceType="location"`).
 *
 * 위치는 **이 서비스가 돌고 있을 때만** 수집한다. 서비스가 서면 위치 요청도 선다.
 * 그동안 상시 알림이 항상 보인다(CLAUDE.md 원칙 2).
 *
 * ## 시작 경로 세 가지
 * 1. 발동 알림의 '공유 시작' → [AlertActionReceiver] → 여기.
 * 2. 앱 화면의 '지금 공유' → [ShareController] → 여기.
 * 3. 자동 시작 모드 → [DisasterAlertListenerService] → 여기.
 *
 * ## 3번이 실패할 수 있다
 * 앱이 백그라운드에 있을 때는 ACCESS_BACKGROUND_LOCATION 없이 위치 포그라운드 서비스를
 * 만들 수 없다(공식 문서). 권한이 있어도 기기 상태에 따라
 * `ForegroundServiceStartNotAllowedException` 이나 `SecurityException` 이 날 수 있다.
 * 그때는 조용히 포기하지 않고 '눌러서 시작' 알림으로 바꾼다
 * ([ShareNotifications.postTapToStart]). 사용자가 알림을 눌러 시작하는 것은 예외로 허용된다.
 *
 * ## 동의 확인은 시작할 때 한 번이 아니다
 * 시작할 때 [SharePolicy.decide] 로 막고, 그 뒤 위치를 쓸 때마다
 * [SharePolicy.sharingTargets] 로 다시 걸러 낸다. 공유 중에 일시중지를 누르면
 * 그 그룹에는 바로 쓰기가 멈춘다(원칙 1: 즉시 반영).
 */
class LocationShareService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 시작·중지 요청이 겹쳐 들어와도 한 번에 하나만 처리한다. */
    private val mutex = Mutex()

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var locationCallback: LocationCallback? = null

    /** 자동 종료 타이머. 세션이 끝나면 스스로 멈춘다. */
    private var autoStopJob: Job? = null

    /** 지금 세션에서 위치를 보내는 그룹들. 매 위치마다 동의 상태로 다시 걸러 낸다. */
    private var targets: List<ShareTarget> = emptyList()

    private var sessionId: String = ""
    private var window: ShareWindow? = null
    private var purpose: ProvisionPurpose = ProvisionPurpose.ManualShare
    private var uid: String = ""

    /** 이 세션에서 **마지막으로 쓴** 위치. 50m·60초 판정의 기준점이다. */
    private var lastWritten: LocationPoint? = null

    private var writeCount: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop(StopCause.UserRequested)
            else -> {
                // 시스템이 서비스를 되살렸는데 인텐트가 없는 경우(START_NOT_STICKY 라서
                // 보통 오지 않는다). 세션 정보가 없으면 공유할 수 없으니 그냥 선다.
                Log.w(TAG, "인텐트가 없어 선다: " + intent?.action)
                stopSelfSafely()
            }
        }
        // 시스템이 서비스를 거둬 갔다가 되살리지 않게 한다. 사용자가 모르는 사이에
        // 위치 수집이 되살아나면 안 된다(원칙 2·3).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        autoStopJob?.cancel()
        ShareSessionState.clear()
        scope.cancel()
        super.onDestroy()
    }

    // ---- 시작 ----

    private fun handleStart(intent: Intent) {
        val container = container() ?: run {
            Log.w(TAG, "컨테이너가 없다")
            stopSelfSafely()
            return
        }
        val requestedPurpose = ProvisionPurpose.fromKey(
            intent.getStringExtra(EXTRA_PURPOSE).orEmpty(),
        ) ?: ProvisionPurpose.ManualShare

        scope.launch {
            mutex.withLock {
                if (window != null) {
                    // 이미 돌고 있다. 상시 알림만 다시 띄우고 끝낸다.
                    Log.i(TAG, "이미 공유 중이다")
                    refreshNotification()
                    return@withLock
                }
                startSession(container, requestedPurpose)
            }
        }
    }

    private suspend fun startSession(container: AppContainer, requestedPurpose: ProvisionPurpose) {
        val user = container.authRepository.currentUserNow()
        val locationGranted = hasLocationPermission(this)

        val groups = if (user != null && container.isServerReady) {
            runCatching { container.shareRepository.myGroupsOnce(user.uid) }.getOrElse {
                Log.w(TAG, "그룹을 읽지 못했다", it)
                emptyList()
            }
        } else {
            emptyList()
        }

        val candidateTargets = groups.mapNotNull { entry ->
            val me = entry.memberOf(user?.uid.orEmpty()) ?: return@mapNotNull null
            ShareTarget(
                groupId = entry.group.id,
                groupName = entry.group.name,
                shareState = me.shareState,
                receiverCount = entry.others(user?.uid.orEmpty()).size,
            )
        }

        val decision = SharePolicy.decide(
            targets = candidateTargets,
            signedIn = user != null,
            locationPermissionGranted = locationGranted,
            // 서비스 안에서는 '이미 공유 중'을 window 로 본다(위에서 걸렀다).
            alreadySharing = false,
            // 자동 시작 여부는 시작 경로가 이미 판단해서 purpose 로 넘긴다.
            autoStart = requestedPurpose == ProvisionPurpose.AlertAuto,
            backgroundLocationGranted = hasBackgroundLocationPermission(this),
        )

        val reason = decision.blockReason
        if (reason != null) {
            Log.i(TAG, "공유를 시작하지 않는다: " + reason.key)
            ShareNotifications.postShareFailed(
                this,
                getString(ShareBlockMessages.messageRes(reason)),
            )
            stopSelfSafely()
            return
        }

        val settings = container.shareSettingsStore.settings.first()
        // 요금제는 5단계에서 서버가 검증한 값으로 바꾼다. 지금은 항상 무료 한도로 본다.
        val nowMillis = System.currentTimeMillis()
        val shareWindow = ShareWindow.start(
            nowMillis = nowMillis,
            requestedMinutes = settings.shareMinutes,
            plan = Plan.Free,
        )

        targets = SharePolicy.sharingTargets(candidateTargets)
        sessionId = UUID.randomUUID().toString()
        window = shareWindow
        purpose = requestedPurpose
        uid = user?.uid.orEmpty()
        lastWritten = null
        writeCount = 0

        // 포그라운드로 올리는 것을 **제일 먼저** 한다. 상시 알림 없이 위치를 수집하는
        // 순간이 있어서는 안 되고, Android 는 서비스 시작 뒤 짧은 시간 안에
        // startForeground 를 부르지 않으면 앱을 죽인다.
        if (!promoteToForeground(shareWindow)) {
            // 포그라운드로 올리지 못했다(백그라운드 시작 제한). 자동 시작이면
            // '눌러서 시작' 알림으로 바꾼다.
            window = null
            onForegroundStartFailed(requestedPurpose)
            return
        }

        ShareSessionState.start(
            ActiveShareSession(
                sessionId = sessionId,
                window = shareWindow,
                groupNames = targets.map { it.groupName },
                receiverCount = targets.sumOf { it.receiverCount },
                purpose = requestedPurpose,
            ),
        )

        recordStart(container, shareWindow, nowMillis)
        startLocationUpdates()
        scheduleAutoStop(shareWindow)
    }

    /**
     * 제공 기록을 남긴다(위치정보법 제19조③ 즉시 통보·기록).
     *
     * 위치를 한 건이라도 보내기 **전에** 남긴다. 기록보다 제공이 먼저 가면 안 된다.
     */
    private suspend fun recordStart(
        container: AppContainer,
        shareWindow: ShareWindow,
        nowMillis: Long,
    ) {
        val groups = runCatching {
            container.shareRepository.myGroupsOnce(uid)
        }.getOrElse { emptyList() }

        targets.forEach { target ->
            val entry = groups.firstOrNull { it.group.id == target.groupId }
            val others = entry?.others(uid).orEmpty()

            // 기기 안 사본. 서버가 없어도 사용자가 제공 기록 화면에서 볼 수 있어야 한다.
            runCatching {
                container.provisionRecordRepository.recordStart(
                    sessionId = sessionId,
                    groupId = target.groupId,
                    groupName = target.groupName,
                    startedAtMillis = nowMillis,
                    plannedEndAtMillis = shareWindow.endsAtMillis,
                    purpose = purpose,
                    receiverNames = others.map { it.displayName },
                )
            }.onFailure { Log.w(TAG, "제공 기록(기기)을 남기지 못했다", it) }

            // 서버 사본 + 'sharing.active' 켜기.
            runCatching {
                container.shareRepository.startSharing(
                    groupId = target.groupId,
                    uid = uid,
                    sessionId = sessionId,
                    startedAtMillis = nowMillis,
                    endsAtMillis = shareWindow.endsAtMillis,
                    purpose = purpose,
                    receiverUids = others.map { it.uid },
                )
            }.onFailure { Log.w(TAG, "제공 기록(서버)을 남기지 못했다", it) }
        }
    }

    /**
     * 포그라운드 서비스로 올린다.
     *
     * @return 성공했으면 true. 백그라운드 시작 제한에 걸리면 false.
     */
    private fun promoteToForeground(shareWindow: ShareWindow): Boolean {
        val notification = ShareNotifications.sharingNotification(
            context = this,
            groupNames = targets.map { it.groupName },
            receiverCount = targets.sumOf { it.receiverCount },
            window = shareWindow,
        )
        return try {
            ServiceCompat.startForeground(
                this,
                ShareNotifications.SHARING_NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
            true
        } catch (error: SecurityException) {
            // 위치 권한이 중간에 빠진 경우. 포그라운드 서비스 타입 location 에는 권한이 필요하다.
            Log.w(TAG, "포그라운드로 올리지 못했다(권한)", error)
            false
        } catch (error: IllegalStateException) {
            // Android 12+ 의 ForegroundServiceStartNotAllowedException 이
            // IllegalStateException 의 하위 타입이다. minSdk 26 이라 타입을 직접 잡으면
            // 낮은 버전에서 클래스를 찾을 수 없으므로 상위 타입으로 받는다.
            Log.w(TAG, "포그라운드로 올리지 못했다(백그라운드 시작 제한)", error)
            false
        }
    }

    /** 자동 시작이 막혔을 때. 조용히 포기하지 않고 '눌러서 시작' 알림으로 바꾼다. */
    private fun onForegroundStartFailed(requestedPurpose: ProvisionPurpose) {
        if (requestedPurpose == ProvisionPurpose.AlertAuto) {
            ShareNotifications.postTapToStart(
                this,
                getString(R.string.sharing_tap_to_start_body),
            )
        } else {
            ShareNotifications.postShareFailed(
                this,
                getString(R.string.sharing_failed_foreground),
            )
        }
        stopSelfSafely()
    }

    // ---- 위치 받기 ----

    private fun startLocationUpdates() {
        if (!hasLocationPermission(this)) {
            Log.w(TAG, "위치 권한이 없다")
            handleStop(StopCause.PermissionLost)
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LocationWriteRule.INTERVAL_MILLIS,
        )
            // 60초보다 자주 받지 않는다. 배터리와 쓰기 건수를 같이 줄인다.
            .setMinUpdateIntervalMillis(LocationWriteRule.INTERVAL_MILLIS)
            // 시스템이 위치를 모아 한꺼번에 주지 않게 한다. 재난 상황에서는 늦게 오면 안 된다.
            .setMaxUpdateDelayMillis(LocationWriteRule.INTERVAL_MILLIS)
            // HIGH_ACCURACY 를 쓰지 않는 이유: GPS 를 계속 켜면 배터리를 많이 쓴다.
            // BALANCED 는 보통 수십 미터 정확도라 '가족이 어디쯤 있나'에는 충분하다.
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val point = LocationPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                    atMillis = System.currentTimeMillis(),
                )
                scope.launch { onLocation(point) }
            }
        }
        locationCallback = callback

        try {
            fusedClient.requestLocationUpdates(request, callback, mainLooper)
        } catch (error: SecurityException) {
            Log.w(TAG, "위치 요청이 거부됐다", error)
            handleStop(StopCause.PermissionLost)
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private suspend fun onLocation(point: LocationPoint) = mutex.withLock {
        val shareWindow = window ?: return@withLock
        val container = container() ?: return@withLock

        if (shareWindow.isExpired(point.atMillis)) {
            // 자동 종료 타이머보다 위치가 먼저 왔다.
            handleStop(StopCause.Expired)
            return@withLock
        }

        if (LocationWriteRule.isTooInaccurate(point)) {
            Log.i(TAG, "정확도가 나빠 버린다: " + point.accuracyMeters)
            return@withLock
        }

        val verdict = LocationWriteRule.decide(lastWritten, point)
        if (!verdict.shouldWrite) return@withLock

        // 동의 상태를 **다시** 읽는다. 공유 중에 일시중지하거나 철회했으면 그 그룹은 빠진다.
        val groups = runCatching { container.shareRepository.myGroupsOnce(uid) }
            .getOrElse {
                // 서버를 못 읽었다. 저장해 둔 목록으로 계속 간다. 네트워크가 끊겼을 때
                // 공유가 멈추는 것보다 이어지는 쪽이 재난 상황에서 낫다고 봤다.
                Log.w(TAG, "동의 상태를 다시 읽지 못했다", it)
                null
            }
        if (groups != null) {
            val refreshed = targets.mapNotNull { target ->
                val me = groups.firstOrNull { it.group.id == target.groupId }
                    ?.memberOf(uid)
                    ?: return@mapNotNull null
                target.copy(shareState = me.shareState)
            }
            targets = SharePolicy.sharingTargets(refreshed)
            if (targets.isEmpty()) {
                Log.i(TAG, "보낼 그룹이 없어 선다(동의 철회·일시중지)")
                handleStop(StopCause.ConsentChanged)
                return@withLock
            }
        }

        // 세션이 끝난 뒤 24시간이 지나면 서버가 지운다(CLAUDE.md 원칙 4).
        val expiresAt = shareWindow.endsAtMillis + LAST_LOCATION_RETENTION_MILLIS

        var wrote = false
        targets.forEach { target ->
            runCatching {
                container.shareRepository.writeLocation(
                    groupId = target.groupId,
                    uid = uid,
                    sessionId = sessionId,
                    point = point,
                    expiresAtMillis = expiresAt,
                )
                wrote = true
            }.onFailure { Log.w(TAG, "위치를 쓰지 못했다: " + target.groupId, it) }
        }

        if (wrote) {
            // 쓰기에 성공한 것만 기준점으로 삼는다. 실패한 위치를 기준점으로 두면
            // 다음 위치가 '50m 안쪽'으로 걸려 한참 동안 아무것도 못 보낸다.
            lastWritten = point
            writeCount++
            ShareSessionState.updateWriteCount(writeCount)
        }
    }

    // ---- 자동 종료와 중지 ----

    private fun scheduleAutoStop(shareWindow: ShareWindow) {
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(shareWindow.remainingMillis(System.currentTimeMillis()))
            mutex.withLock { handleStop(StopCause.Expired) }
        }
    }

    /**
     * 공유를 멈춘다.
     *
     * [mutex] 안에서 부르거나(위치 콜백·자동 종료) 바깥에서 부른다('지금 중지').
     * 중지는 두 번 불려도 문제가 없게 만들어 두었다(window 가 null 이면 바로 선다).
     */
    private fun handleStop(cause: StopCause) {
        val shareWindow = window
        window = null
        stopLocationUpdates()
        autoStopJob?.cancel()
        autoStopJob = null

        if (shareWindow == null) {
            stopSelfSafely()
            return
        }

        val endedAt = System.currentTimeMillis()
        val finishedTargets = targets
        val finishedSessionId = sessionId
        val finishedWriteCount = writeCount
        val finishedUid = uid

        Log.i(TAG, "공유를 멈춘다: " + cause.name + " (쓰기 " + finishedWriteCount + "건)")

        // 정리 작업은 서비스가 선 뒤에도 끝나야 하므로 애플리케이션 범위에서 돌린다.
        val container = container()
        AnsimLinkApplication.applicationScope.launch {
            finishedTargets.forEach { target ->
                runCatching {
                    container?.provisionRecordRepository?.recordEnd(
                        sessionId = finishedSessionId,
                        groupId = target.groupId,
                        endedAtMillis = endedAt,
                        writeCount = finishedWriteCount,
                    )
                }
                runCatching {
                    container?.shareRepository?.endSharing(
                        groupId = target.groupId,
                        uid = finishedUid,
                        sessionId = finishedSessionId,
                        endedAtMillis = endedAt,
                    )
                }
                // 동의가 바뀌어서 멈춘 경우에는 마지막 위치도 바로 지운다.
                // 그 밖의 경우에는 마지막 1건을 남기고 24시간 뒤 서버가 지운다(원칙 4).
                if (cause == StopCause.ConsentChanged) {
                    runCatching {
                        container?.shareRepository?.deleteLocation(target.groupId, finishedUid)
                    }
                }
            }
        }

        targets = emptyList()
        ShareSessionState.clear()
        stopSelfSafely()
    }

    private fun refreshNotification() {
        val shareWindow = window ?: return
        ShareNotifications.sharingNotification(
            context = this,
            groupNames = targets.map { it.groupName },
            receiverCount = targets.sumOf { it.receiverCount },
            window = shareWindow,
        ).let { notification ->
            AlertNotifications.post(this, ShareNotifications.SHARING_NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        ShareNotifications.cancelSharing(this)
        stopSelf()
    }

    private fun container(): AppContainer? =
        (application as? AnsimLinkApplication)?.container

    /** 공유가 멈춘 이유. 로그와 '마지막 위치를 지울지' 판단에 쓴다. */
    private enum class StopCause {
        UserRequested,
        Expired,
        ConsentChanged,
        PermissionLost,
    }

    companion object {
        private const val TAG = "LocationShare"

        private const val PREFIX = "io.github.ndrcb5210max.ansimlink.share."

        const val ACTION_START = PREFIX + "START"
        const val ACTION_STOP = PREFIX + "STOP"

        /** [ProvisionPurpose.key] 를 넘긴다. */
        const val EXTRA_PURPOSE = PREFIX + "PURPOSE"

        /** 세션이 끝난 뒤 마지막 위치를 남겨 두는 시간. CLAUDE.md 원칙 4: 24시간. */
        const val LAST_LOCATION_RETENTION_MILLIS: Long = 24L * 60 * 60 * 1000

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED

        fun hasBackgroundLocationPermission(context: Context): Boolean {
            // Android 9 이하에는 이 권한이 없다. 전경 권한만 있으면 배경에서도 받을 수 있었다.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return hasLocationPermission(context)
            }
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun startIntent(context: Context, purpose: ProvisionPurpose): Intent =
            Intent(context, LocationShareService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PURPOSE, purpose.key)

        fun stopIntent(context: Context): Intent =
            Intent(context, LocationShareService::class.java).setAction(ACTION_STOP)
    }
}
