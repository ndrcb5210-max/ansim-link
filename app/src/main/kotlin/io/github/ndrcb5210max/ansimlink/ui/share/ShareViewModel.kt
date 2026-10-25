package io.github.ndrcb5210max.ansimlink.ui.share

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.data.message.SafetyMessageStore
import io.github.ndrcb5210max.ansimlink.data.model.GroupMember
import io.github.ndrcb5210max.ansimlink.data.model.MemberLocation
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionRecordEntity
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionRecordRepository
import io.github.ndrcb5210max.ansimlink.data.share.ShareRepository
import io.github.ndrcb5210max.ansimlink.data.share.ShareSettingsStore
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits
import io.github.ndrcb5210max.ansimlink.domain.share.AutoStartFlow
import io.github.ndrcb5210max.ansimlink.domain.share.LocationPoint
import io.github.ndrcb5210max.ansimlink.domain.share.LocationWriteRule
import io.github.ndrcb5210max.ansimlink.domain.share.SharePolicy
import io.github.ndrcb5210max.ansimlink.domain.share.ShareTarget
import io.github.ndrcb5210max.ansimlink.service.LastKnownLocation
import io.github.ndrcb5210max.ansimlink.service.LocationShareService
import io.github.ndrcb5210max.ansimlink.service.ShareSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 4단계 화면들이 같이 쓰는 ViewModel.
 *
 * 화면은 상태와 콜백만 받는다(2·3단계와 같은 방식). 시각은 여기서 문자열로 만들어
 * 넘기므로 스크린샷이 기기 시간대나 찍는 시각에 따라 달라지지 않는다.
 *
 * @param applicationContext 권한 확인과 마지막 위치 읽기에만 쓴다. Activity 를 들고 있지 않는다.
 * @param serverReady Firebase 설정이 제대로 있는지. 가짜면 서버를 아예 건드리지 않는다.
 */
class ShareViewModel(
    private val applicationContext: Context,
    private val authRepository: AuthRepository,
    private val shareRepository: ShareRepository,
    private val provisionRecords: ProvisionRecordRepository,
    private val settingsStore: ShareSettingsStore,
    private val messageStore: SafetyMessageStore,
    private val serverReady: Boolean,
) : ViewModel() {

    // 'HH:mm' — 상시 알림·배너용(공유는 최대 180분이라 날짜가 바뀌지 않는다).
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

    // 'M월 d일 HH:mm' — 제공 기록용(며칠 전 기록도 본다).
    private val dateTimeFormat = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)

    /** 요금제. 5단계에서 서버가 검증한 값으로 바꾼다. 지금은 항상 무료다. */
    private val plan = Plan.Free

    private val permissionState = MutableStateFlow(PermissionSnapshot())

    private val familySelection = MutableStateFlow<String?>(null)

    /** 내 마지막 위치. 거리 계산에만 쓰고 어디로도 보내지 않는다. */
    private val myLocation = MutableStateFlow<LocationPoint?>(null)

    // ---- 홈 화면의 공유 배너 ----

    val share: StateFlow<ShareUiState> =
        combine(
            ShareSessionState.active,
            blockReasonFlow(),
        ) { session, blockReason ->
            if (session == null) {
                ShareUiState(loading = false, blockReason = blockReason)
            } else {
                val nowMillis = System.currentTimeMillis()
                ShareUiState(
                    loading = false,
                    sharing = true,
                    groupNames = session.groupNames,
                    receiverCount = session.receiverCount,
                    startedText = clockFormat.format(Date(session.window.startedAtMillis)),
                    endsText = clockFormat.format(Date(session.window.endsAtMillis)),
                    remainingMinutes = session.window.remainingMinutes(nowMillis),
                    writeCount = session.writeCount,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ShareUiState(),
        )

    // ---- 위치 공유 설정 ----

    val settings: StateFlow<ShareSettingsUiState> =
        combine(settingsStore.settings, permissionState) { stored, permissions ->
            ShareSettingsUiState(
                shareMinutes = stored.shareMinutes,
                maxAllowedMinutes = PlanLimits.maxShareMinutes(plan),
                autoStartEnabled = stored.autoStartEnabled,
                autoStartStep = AutoStartFlow.nextStep(
                    disclosureAccepted = stored.autoStartDisclosureAccepted,
                    locationPermissionGranted = permissions.location,
                    backgroundLocationGranted = permissions.backgroundLocation,
                ),
                locationPermissionGranted = permissions.location,
                backgroundLocationGranted = permissions.backgroundLocation,
                notificationsEnabled = permissions.notifications,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ShareSettingsUiState(),
        )

    // ---- 안심 메시지 ----

    val messages: StateFlow<SafetyMessageUiState> = messageStore.messages
        .map { set ->
            SafetyMessageUiState(
                loading = false,
                common = set.common,
                byType = set.byType,
                premium = plan == Plan.Premium,
                senderName = authRepository.currentUserNow()?.label.orEmpty(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SafetyMessageUiState(),
        )

    // ---- 내 위치 제공 기록 ----

    val provisionLog: StateFlow<ProvisionLogUiState> = provisionRecords.recent()
        .map { records ->
            ProvisionLogUiState(loading = false, items = records.map(::toLogItem))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProvisionLogUiState(),
        )

    // ---- 가족 위치 보기 ----

    @OptIn(ExperimentalCoroutinesApi::class)
    val family: StateFlow<FamilyLocationUiState> =
        familySelection.flatMapLatest { selectedGroupId ->
            val uid = authRepository.currentUserNow()?.uid
            if (uid == null || !serverReady || selectedGroupId == null) {
                flowOf(FamilyLocationUiState(loading = false))
            } else {
                combine(
                    shareRepository.observeSharingMembers(selectedGroupId, uid),
                    myLocation,
                ) { sharingMembers, mine ->
                    FamilyLocationUiState(
                        loading = false,
                        groups = groupOptions.value,
                        selectedGroupId = selectedGroupId,
                        members = sharingMembers.map { entry ->
                            toFamilyItem(entry.member, entry.location, mine)
                        },
                        myLocationUnknown = mine == null,
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = FamilyLocationUiState(),
        )

    private val groupOptions = MutableStateFlow<List<FamilyGroupOption>>(emptyList())

    init {
        refreshPermissions()
    }

    /** 권한 설정에서 돌아올 때마다 부른다. */
    fun refreshPermissions() {
        permissionState.value = PermissionSnapshot(
            location = LocationShareService.hasLocationPermission(applicationContext),
            backgroundLocation =
            LocationShareService.hasBackgroundLocationPermission(applicationContext),
            notifications = NotificationManagerCompat
                .from(applicationContext)
                .areNotificationsEnabled(),
        )
    }

    /** 가족 위치 보기 화면을 열 때 부른다. 그룹 목록을 읽고 하나를 고른다. */
    fun loadFamily(preferredGroupId: String? = null) {
        val uid = authRepository.currentUserNow()?.uid ?: return
        if (!serverReady) return
        viewModelScope.launch {
            // 거리 계산용 내 위치. 권한이 없으면 null 로 남고 화면이 그 사실을 적는다.
            myLocation.value = LastKnownLocation.read(applicationContext)

            val groups = runCatching { shareRepository.myGroupsOnce(uid) }.getOrElse { emptyList() }
            groupOptions.value = groups.map { FamilyGroupOption(it.group.id, it.group.name) }
            familySelection.value = preferredGroupId?.takeIf { wanted ->
                groups.any { it.group.id == wanted }
            } ?: groups.firstOrNull()?.group?.id
        }
    }

    fun selectFamilyGroup(groupId: String) {
        familySelection.value = groupId
    }

    // ---- 설정 바꾸기 ----

    fun setShareMinutes(minutes: Int) {
        viewModelScope.launch { settingsStore.setShareMinutes(minutes) }
    }

    /** 명시적 공개 화면에서 '이해했습니다'를 눌렀을 때만 부른다. */
    fun acceptAutoStartDisclosure() {
        viewModelScope.launch {
            settingsStore.acceptAutoStartDisclosure(System.currentTimeMillis())
        }
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoStartEnabled(enabled) }
    }

    fun setCommonMessage(text: String) {
        viewModelScope.launch { messageStore.setCommon(text) }
    }

    fun setTypeMessage(type: DisasterType, text: String) {
        viewModelScope.launch { messageStore.setForType(type, text) }
    }

    fun resetCommonMessage() {
        viewModelScope.launch { messageStore.resetCommon() }
    }

    // ---- 내부 ----

    /**
     * 지금 공유를 시작할 수 없는 이유.
     *
     * 홈 화면 버튼을 누르기 **전에** 보여 주려고 미리 계산한다. 서비스도 시작할 때
     * 같은 판단을 다시 한다(화면 상태가 오래됐을 수 있다).
     */
    private fun blockReasonFlow() = combine(
        permissionState,
        ShareSessionState.active,
    ) { permissions, session ->
        val user = authRepository.currentUserNow()
        val targets = if (user != null && serverReady) {
            runCatching { shareRepository.myGroupsOnce(user.uid) }
                .getOrElse { emptyList() }
                .mapNotNull { entry ->
                    val me = entry.memberOf(user.uid) ?: return@mapNotNull null
                    ShareTarget(
                        groupId = entry.group.id,
                        groupName = entry.group.name,
                        shareState = me.shareState,
                        receiverCount = entry.others(user.uid).size,
                    )
                }
        } else {
            emptyList()
        }

        SharePolicy.decide(
            targets = targets,
            signedIn = user != null,
            locationPermissionGranted = permissions.location,
            alreadySharing = session != null,
        ).blockReason
    }

    private fun toLogItem(record: ProvisionRecordEntity) = ProvisionLogItem(
        id = record.id,
        groupName = record.groupName,
        purpose = record.purpose,
        periodText = if (record.endedAtMillis == null) {
            dateTimeFormat.format(Date(record.startedAtMillis))
        } else {
            dateTimeFormat.format(Date(record.startedAtMillis)) +
                " ~ " + clockFormat.format(Date(record.endedAtMillis))
        },
        ongoing = record.ongoing,
        plannedEndText = clockFormat.format(Date(record.plannedEndAtMillis)),
        receiverNames = record.receiverNames,
        writeCount = record.writeCount,
    )

    private fun toFamilyItem(
        member: GroupMember,
        location: MemberLocation?,
        mine: LocationPoint?,
    ) = FamilyMemberItem(
        uid = member.uid,
        displayName = member.displayName,
        updatedText = location?.let { clockFormat.format(Date(it.atMillis)) },
        accuracyText = location?.accuracyMeters?.let { it.roundToInt().toString() },
        distanceText = if (location != null && mine != null) {
            ShareFormatting.distanceText(
                LocationWriteRule.distanceMeters(
                    mine.latitude,
                    mine.longitude,
                    location.latitude,
                    location.longitude,
                ),
            )
        } else {
            null
        },
        endsAtText = member.sharingEndsAtMillis
            .takeIf { it > 0L }
            ?.let { clockFormat.format(Date(it)) },
        latitude = location?.latitude,
        longitude = location?.longitude,
    )

    private data class PermissionSnapshot(
        val location: Boolean = false,
        val backgroundLocation: Boolean = false,
        val notifications: Boolean = false,
    )

    private companion object {
        /** 화면을 잠깐 떠났다 돌아올 때 흐름을 다시 만들지 않도록 두는 여유. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
