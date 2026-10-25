package io.github.ndrcb5210max.ansimlink.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.data.billing.PlanProvider
import io.github.ndrcb5210max.ansimlink.data.group.GroupRepository
import io.github.ndrcb5210max.ansimlink.data.toAppError
import io.github.ndrcb5210max.ansimlink.domain.consent.TermsVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 그룹 탭(목록·만들기·참여)의 상태를 들고 저장소를 부른다.
 *
 * @param planProvider 요금제. 그룹 개수·인원 한도가 여기서 나온다. 화면에 보여 줄 때와
 *   저장소를 부를 때 **같은 값**을 쓴다(둘이 갈라지면 '만들 수 있다고 했는데 거부당했다'
 *   가 된다).
 */
class GroupViewModel(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val planProvider: PlanProvider,
    private val serverReady: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(
        GroupUiState(
            serverReady = serverReady,
            loading = serverReady,
            myUid = authRepository.currentUserNow()?.uid.orEmpty(),
        ),
    )
    val state: StateFlow<GroupUiState> = _state.asStateFlow()

    private val _createState = MutableStateFlow(CreateGroupUiState())
    val createState: StateFlow<CreateGroupUiState> = _createState.asStateFlow()

    private val _joinState = MutableStateFlow(JoinGroupUiState())
    val joinState: StateFlow<JoinGroupUiState> = _joinState.asStateFlow()

    init {
        if (serverReady) observeGroups()
        observePlan()
    }

    private fun observePlan() {
        viewModelScope.launch {
            planProvider.plan.collect { plan ->
                _state.update { it.copy(plan = plan) }
                // 참여 동의 화면의 '최대 몇 분 동안 제공됩니다' 문구도 요금제에 따라
                // 달라진다. 두 상태가 같은 값을 보게 한곳에서 같이 넣는다.
                _joinState.update { it.copy(plan = plan) }
            }
        }
    }

    private fun observeGroups() {
        val uid = _state.value.myUid
        if (uid.isEmpty()) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            groupRepository.observeMyGroups(uid)
                .catch { throwable ->
                    _state.update { it.copy(loading = false, error = throwable.toAppError()) }
                }
                .collect { groups ->
                    _state.update { it.copy(loading = false, groups = groups, error = null) }
                }
        }
    }

    // ---- 그룹 만들기 ----

    fun setNewGroupName(value: String) =
        _createState.update { it.copy(name = value, error = null) }

    fun resetCreateState() = _createState.update { CreateGroupUiState() }

    fun createGroup() {
        val current = _createState.value
        if (!current.canSubmit) return
        val user = authRepository.currentUserNow() ?: return

        viewModelScope.launch {
            _createState.update { it.copy(busy = true, error = null) }
            val result = runCatching {
                groupRepository.createGroup(
                    ownerUid = user.uid,
                    ownerDisplayName = user.label,
                    groupName = current.name,
                    currentGroupCount = _state.value.groups.size,
                    // 흐름의 마지막 값이 아니라 지금 값을 다시 읽는다. 결제를 막 끝내고
                    // 바로 그룹을 만드는 경우, 흐름이 아직 프리미엄을 못 받았을 수 있다.
                    plan = planProvider.currentPlan(),
                )
            }
            _createState.update {
                it.copy(
                    busy = false,
                    error = result.exceptionOrNull()?.toAppError(),
                    createdGroupId = result.getOrNull(),
                )
            }
        }
    }

    // ---- 참여 ----

    fun setInviteCode(value: String) =
        _joinState.update { it.copy(code = value, error = null) }

    fun setJoinConsent(value: Boolean) = _joinState.update { it.copy(consentChecked = value) }

    // 요금제는 지우지 않는다. 화면을 다시 열 때 동의 문구의 분 수가 잠깐 60분으로
    // 보였다가 180분으로 바뀌면 사용자가 무엇에 동의하는지 헷갈린다.
    fun resetJoinState() = _joinState.update { JoinGroupUiState(plan = it.plan) }

    fun lookupInvite() {
        val current = _joinState.value
        if (!current.canLookup) return

        viewModelScope.launch {
            _joinState.update { it.copy(busy = true, error = null) }
            val result = runCatching { groupRepository.lookupInvite(current.code) }
            val invite = result.getOrNull()
            val alreadyMember = invite != null &&
                _state.value.groups.any { it.group.id == invite.groupId }

            _joinState.update {
                it.copy(
                    busy = false,
                    invite = if (alreadyMember) null else invite,
                    error = when {
                        alreadyMember -> AppError.AlreadyMember
                        else -> result.exceptionOrNull()?.toAppError()
                    },
                )
            }
        }
    }

    fun joinGroup() {
        val current = _joinState.value
        val invite = current.invite ?: return
        if (!current.canJoin) return
        val user = authRepository.currentUserNow() ?: return

        viewModelScope.launch {
            _joinState.update { it.copy(busy = true, error = null) }
            val result = runCatching {
                groupRepository.joinGroup(
                    invite = invite,
                    uid = user.uid,
                    displayName = user.label,
                    consentVersion = TermsVersion.LOCATION_TERMS,
                )
            }
            _joinState.update {
                it.copy(
                    busy = false,
                    error = result.exceptionOrNull()?.toAppError(),
                    joined = result.isSuccess,
                )
            }
        }
    }

    // ---- 내 공유 상태 ----

    fun setPaused(groupId: String, paused: Boolean) = runOnGroups {
        groupRepository.setPaused(groupId, _state.value.myUid, paused)
    }

    fun withdrawConsent(groupId: String) = runOnGroups {
        groupRepository.withdrawConsent(groupId, _state.value.myUid)
    }

    // ---- 초대 코드 ----

    fun createInvite(groupId: String) = runOnGroups {
        val invite = groupRepository.createInvite(
            groupId = groupId,
            byUid = _state.value.myUid,
            plan = planProvider.currentPlan(),
        )
        _state.update { it.copy(freshInvite = invite) }
    }

    fun dismissInvite() = _state.update { it.copy(freshInvite = null) }

    private fun runOnGroups(block: suspend () -> Unit) {
        if (!serverReady) {
            _state.update { it.copy(error = AppError.ServerNotConfigured) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val result = runCatching { block() }
            _state.update {
                it.copy(busy = false, error = result.exceptionOrNull()?.toAppError())
            }
        }
    }
}
