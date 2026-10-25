package io.github.ndrcb5210max.ansimlink.ui.alert

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ndrcb5210max.ansimlink.data.alert.AlertEventEntity
import io.github.ndrcb5210max.ansimlink.data.alert.AlertEventRepository
import io.github.ndrcb5210max.ansimlink.data.alert.ObservedPackageEntity
import io.github.ndrcb5210max.ansimlink.data.alert.TriggerRulesStore
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertPackages
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.service.DisasterAlertListenerService
import io.github.ndrcb5210max.ansimlink.service.NotificationAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 감지 설정·발동 규칙·발동 기록 화면이 같이 쓰는 ViewModel.
 *
 * 화면들은 상태와 콜백만 받는 순수한 함수다(2단계와 같은 방식). 덕분에 DB·권한 없이
 * 미리보기와 스크린샷을 찍을 수 있다.
 *
 * @param applicationContext 권한 확인에만 쓴다. Activity 를 들고 있지 않는다.
 */
class AlertViewModel(
    private val applicationContext: Context,
    private val repository: AlertEventRepository,
    private val rulesStore: TriggerRulesStore,
) : ViewModel() {

    // 'M월 d일 HH:mm'. 재난문자 자체도 월·일·시각만 적는다.
    private val timeFormat = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)

    private val detectionState = MutableStateFlow(DetectionUiState())

    val detection: StateFlow<DetectionUiState> = detectionState.asStateFlow()

    val rules: StateFlow<TriggerRulesUiState> = rulesStore.rules
        .map(::TriggerRulesUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TriggerRulesUiState(),
        )

    val log: StateFlow<AlertLogUiState> = repository
        .recentEvents()
        .map { events -> AlertLogUiState(loading = false, items = events.map(::toLogItem)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AlertLogUiState(),
        )

    val observedPackages: StateFlow<ObservedPackagesUiState> = repository
        .observedPackageList()
        .map { list ->
            ObservedPackagesUiState(loading = false, items = list.map(::toObservedItem))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ObservedPackagesUiState(),
        )

    init {
        // 리스너 연결 상태는 시스템이 바꾼다(서비스를 묶었다 풀 수 있다).
        // 권한 상태는 설정에서 돌아올 때 refresh() 로 다시 읽는다.
        viewModelScope.launch {
            DisasterAlertListenerService.connected.collect { connected ->
                detectionState.value = detectionState.value.copy(listenerConnected = connected)
            }
        }
        refresh()
    }

    /** 설정 화면에서 돌아올 때마다 부른다. */
    fun refresh() {
        detectionState.value = detectionState.value.copy(
            accessGranted = NotificationAccess.isGranted(applicationContext),
            listenerConnected = DisasterAlertListenerService.connected.value,
            notificationsEnabled = NotificationManagerCompat
                .from(applicationContext)
                .areNotificationsEnabled(),
            settingsNotFound = false,
        )
    }

    fun openNotificationAccessSettings(context: Context) {
        val opened = NotificationAccess.openSettings(context)
        detectionState.value = detectionState.value.copy(settingsNotFound = !opened)
    }

    fun openAppDetails(context: Context) {
        NotificationAccess.openAppDetails(context)
    }

    fun setSeverityEnabled(severity: AlertSeverity, enabled: Boolean) {
        viewModelScope.launch { rulesStore.setSeverityEnabled(severity, enabled) }
    }

    fun setTypeEnabled(severity: AlertSeverity, type: DisasterType, on: Boolean) {
        viewModelScope.launch { rulesStore.setTypeEnabled(severity, type, on) }
    }

    fun setAllTypes(severity: AlertSeverity, on: Boolean) {
        viewModelScope.launch {
            DisasterType.selectableEntries.forEach { type ->
                rulesStore.setTypeEnabled(severity, type, on)
            }
        }
    }

    fun resetRules() {
        viewModelScope.launch { rulesStore.resetToDefault() }
    }

    fun clearLog() {
        viewModelScope.launch { repository.clearEvents() }
    }

    fun clearObservedPackages() {
        viewModelScope.launch { repository.clearObservedPackages() }
    }

    private fun toLogItem(event: AlertEventEntity) = AlertLogItem(
        id = event.id,
        timeText = timeFormat.format(Date(event.detectedAtMillis)),
        severity = event.severity,
        type = event.type,
        categoryName = event.categoryName,
        confidence = event.confidence,
        bodyPreview = event.bodyPreview,
        triggered = event.triggered,
        reason = event.reason,
    )

    private fun toObservedItem(entity: ObservedPackageEntity) = ObservedPackageItem(
        packageName = entity.packageName,
        count = entity.count,
        lastSeenText = timeFormat.format(Date(entity.lastSeenAtMillis)),
        known = AlertPackages.isKnownAlertPackage(entity.packageName),
    )

    private companion object {
        /** 화면을 잠깐 떠났다 돌아올 때 흐름을 다시 만들지 않도록 두는 여유. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
