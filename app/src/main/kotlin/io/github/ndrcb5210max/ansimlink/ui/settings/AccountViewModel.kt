package io.github.ndrcb5210max.ansimlink.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.data.account.AccountDeleter
import io.github.ndrcb5210max.ansimlink.data.account.OwnedGroupChoice
import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.data.auth.GoogleSignInClient
import io.github.ndrcb5210max.ansimlink.data.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 설정 > 계정과 계정 삭제 화면의 상태. */
class AccountViewModel(
    private val authRepository: AuthRepository,
    private val accountDeleter: AccountDeleter,
    private val googleSignInClient: GoogleSignInClient,
    private val serverReady: Boolean,
) : ViewModel() {

    private val _account = MutableStateFlow(
        AccountUiState(
            user = if (serverReady) authRepository.currentUserNow() else null,
            serverReady = serverReady,
        ),
    )
    val account: StateFlow<AccountUiState> = _account.asStateFlow()

    private val _delete = MutableStateFlow(
        DeleteAccountUiState(
            user = if (serverReady) authRepository.currentUserNow() else null,
            serverReady = serverReady,
        ),
    )
    val delete: StateFlow<DeleteAccountUiState> = _delete.asStateFlow()

    fun resendVerification() {
        if (!serverReady) return
        viewModelScope.launch {
            _account.update { it.copy(busy = true, error = null) }
            val result = runCatching { authRepository.resendVerificationEmail() }
            _account.update {
                it.copy(
                    busy = false,
                    error = result.exceptionOrNull()?.toAppError(),
                    verificationEmailSent = result.isSuccess,
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { runCatching { authRepository.signOut() } }
    }

    /** 삭제 화면을 열 때 어떤 그룹을 어떻게 처리해야 하는지 미리 불러온다. */
    fun loadDeletionPlan() {
        val uid = _delete.value.user?.uid ?: return
        if (!serverReady) return
        viewModelScope.launch {
            _delete.update { it.copy(busy = true, error = null) }
            val result = runCatching { accountDeleter.buildPlan(uid) }
            _delete.update {
                it.copy(
                    busy = false,
                    plan = result.getOrNull(),
                    error = result.exceptionOrNull()?.toAppError(),
                )
            }
        }
    }

    fun setPassword(value: String) = _delete.update { it.copy(password = value, error = null) }

    fun setChoice(groupId: String, choice: OwnedGroupChoice) =
        _delete.update { it.copy(choices = it.choices + (groupId to choice)) }

    /** @param activityContext Google 재인증은 계정 선택 화면을 띄우므로 Activity 컨텍스트가 필요하다. */
    fun reauthenticate(activityContext: Context) {
        val current = _delete.value
        if (!current.canReauthenticate) return
        viewModelScope.launch {
            _delete.update { it.copy(busy = true, error = null) }
            val result = runCatching {
                if (current.usesGoogleReauth) {
                    val idToken = googleSignInClient.requestIdToken(activityContext)
                    authRepository.reauthenticateWithGoogle(idToken)
                } else {
                    authRepository.reauthenticateWithPassword(current.password)
                }
            }
            _delete.update {
                it.copy(
                    busy = false,
                    // 비밀번호를 상태에 오래 들고 있지 않는다.
                    password = if (result.isSuccess) "" else it.password,
                    reauthenticated = result.isSuccess,
                    error = result.exceptionOrNull()?.toAppError(),
                )
            }
        }
    }

    fun deleteAccount() {
        val current = _delete.value
        val uid = current.user?.uid ?: return
        val plan = current.plan ?: return
        if (!current.canDelete) return

        viewModelScope.launch {
            _delete.update { it.copy(busy = true, error = null) }
            val result = runCatching {
                accountDeleter.delete(uid = uid, plan = plan, choices = current.choices)
            }
            _delete.update {
                it.copy(
                    busy = false,
                    deleted = result.isSuccess,
                    error = result.exceptionOrNull()?.toAppError(),
                    // 재인증이 오래됐다는 오류면 다시 본인 확인부터 해야 한다.
                    reauthenticated = it.reauthenticated &&
                        result.exceptionOrNull()?.toAppError() != AppError.RecentLoginRequired,
                )
            }
        }
    }
}
