package io.github.ndrcb5210max.ansimlink.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.data.group.GroupRepository
import io.github.ndrcb5210max.ansimlink.data.toAppError
import io.github.ndrcb5210max.ansimlink.domain.consent.TermsVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 그룹 탭(목록·만들기·참여)의 상태를 들고 저장소를 부른다. */
class GroupViewModel(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
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

    fun resetJoinState() = _joinState.update { JoinGroupUiState() }

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
        val invite = groupRepository.createInvite(groupId, _state.value.myUid)
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
