package io.github.ndrcb5210max.ansimlink.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.data.auth.AuthRepository
import io.github.ndrcb5210max.ansimlink.data.auth.GoogleSignInClient
import io.github.ndrcb5210max.ansimlink.data.toAppError
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 로그인·가입 화면의 상태를 들고 저장소를 부른다. */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val googleSignInClient: GoogleSignInClient,
    serverReady: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(
            serverReady = serverReady,
            googleSignInAvailable = serverReady && googleSignInClient.isAvailable,
        ),
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) = _state.update {
        it.copy(mode = mode, error = null, notice = null, showValidation = false)
    }

    fun setEmail(value: String) = _state.update { it.copy(email = value, error = null) }

    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun setPasswordConfirmation(value: String) =
        _state.update { it.copy(passwordConfirmation = value, error = null) }

    fun setDisplayName(value: String) = _state.update { it.copy(displayName = value) }

    fun toggleConsent(item: SignUpConsent) =
        _state.update { it.copy(consents = it.consents.toggle(item)) }

    fun toggleAllConsents(value: Boolean) =
        _state.update { it.copy(consents = it.consents.setAll(value)) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) {
            _state.update { it.copy(showValidation = true) }
            return
        }
        run(
            block = {
                when (current.mode) {
                    AuthMode.SignIn ->
                        authRepository.signInWithEmail(current.email, current.password)

                    AuthMode.SignUp -> {
                        authRepository.signUpWithEmail(
                            email = current.email,
                            password = current.password,
                            displayName = current.displayName,
                            consents = current.consents,
                        )
                        _state.update { it.copy(notice = AuthNotice.VerificationEmailSent) }
                    }
                }
            },
        )
    }

    /** @param activityContext 계정 선택 화면을 띄워야 하므로 Activity 컨텍스트가 필요하다. */
    fun signInWithGoogle(activityContext: Context) {
        val current = _state.value
        if (!current.canUseGoogle) {
            _state.update { it.copy(showValidation = true) }
            return
        }
        run(
            block = {
                val idToken = googleSignInClient.requestIdToken(activityContext)
                authRepository.signInWithGoogle(idToken, current.consents)
            },
        )
    }

    fun sendPasswordReset() {
        val current = _state.value
        if (current.emailProblem != null) {
            _state.update { it.copy(showValidation = true) }
            return
        }
        run(
            block = {
                authRepository.sendPasswordReset(current.email)
                _state.update { it.copy(notice = AuthNotice.PasswordResetSent) }
            },
        )
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun run(block: suspend () -> Unit) {
        if (!_state.value.serverReady) {
            _state.update { it.copy(error = AppError.ServerNotConfigured) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, notice = null) }
            val result = runCatching { block() }
            _state.update { current ->
                current.copy(
                    busy = false,
                    error = result.exceptionOrNull()?.toAppError(),
                )
            }
        }
    }
}
