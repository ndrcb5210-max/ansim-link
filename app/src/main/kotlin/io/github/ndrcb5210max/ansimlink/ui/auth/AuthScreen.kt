package io.github.ndrcb5210max.ansimlink.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.auth.EmailPasswordRules
import io.github.ndrcb5210max.ansimlink.domain.auth.EmailProblem
import io.github.ndrcb5210max.ansimlink.domain.auth.PasswordProblem
import io.github.ndrcb5210max.ansimlink.domain.consent.LegalDocument
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsent
import io.github.ndrcb5210max.ansimlink.ui.common.ErrorCard
import io.github.ndrcb5210max.ansimlink.ui.common.ServerNotConfiguredCard
import io.github.ndrcb5210max.ansimlink.ui.consent.ConsentSection
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/** 로그인·가입 화면의 동작들. 화면은 상태와 이 콜백만 안다. */
data class AuthActions(
    val onModeChange: (AuthMode) -> Unit = {},
    val onEmailChange: (String) -> Unit = {},
    val onPasswordChange: (String) -> Unit = {},
    val onPasswordConfirmationChange: (String) -> Unit = {},
    val onDisplayNameChange: (String) -> Unit = {},
    val onToggleConsent: (SignUpConsent) -> Unit = {},
    val onToggleAllConsents: (Boolean) -> Unit = {},
    val onOpenLegalDocument: (LegalDocument) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onGoogleSignIn: () -> Unit = {},
    val onForgotPassword: () -> Unit = {},
)

@Composable
fun AuthScreen(
    state: AuthUiState,
    actions: AuthActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.auth_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!state.serverReady) {
            ServerNotConfiguredCard()
        }
        state.error?.let { ErrorCard(it) }
        state.notice?.let { notice ->
            Text(
                text = stringResource(
                    when (notice) {
                        AuthNotice.VerificationEmailSent -> R.string.auth_verification_sent
                        AuthNotice.PasswordResetSent -> R.string.auth_password_reset_sent
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        TabRow(selectedTabIndex = state.mode.ordinal) {
            AuthMode.entries.forEach { mode ->
                Tab(
                    selected = state.mode == mode,
                    onClick = { actions.onModeChange(mode) },
                    text = {
                        Text(
                            stringResource(
                                when (mode) {
                                    AuthMode.SignIn -> R.string.auth_mode_sign_in
                                    AuthMode.SignUp -> R.string.auth_mode_sign_up
                                },
                            ),
                        )
                    },
                )
            }
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = actions.onEmailChange,
            label = { Text(stringResource(R.string.auth_email_label)) },
            singleLine = true,
            isError = state.showValidation && state.emailProblem != null,
            supportingText = state.emailProblem
                ?.takeIf { state.showValidation }
                ?.let { problem -> { Text(stringResource(problem.messageRes())) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.mode == AuthMode.SignUp) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = actions.onDisplayNameChange,
                label = { Text(stringResource(R.string.auth_display_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = state.password,
            onValueChange = actions.onPasswordChange,
            label = { Text(stringResource(R.string.auth_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.showValidation && state.passwordProblem != null,
            supportingText = state.passwordProblem
                ?.takeIf { state.showValidation }
                ?.let { problem -> { Text(passwordProblemText(problem)) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.mode == AuthMode.SignUp) {
            OutlinedTextField(
                value = state.passwordConfirmation,
                onValueChange = actions.onPasswordConfirmationChange,
                label = { Text(stringResource(R.string.auth_password_confirm_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = state.showValidation && state.passwordConfirmationProblem != null,
                supportingText = state.passwordConfirmationProblem
                    ?.takeIf { state.showValidation }
                    ?.let { problem -> { Text(passwordProblemText(problem)) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.showConsents) {
            if (state.mode == AuthMode.SignIn) {
                Text(
                    text = stringResource(R.string.auth_google_needs_consent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConsentSection(
                state = state.consents,
                onToggle = actions.onToggleConsent,
                onToggleAll = actions.onToggleAllConsents,
                onOpenDocument = actions.onOpenLegalDocument,
            )
        }

        Button(
            onClick = actions.onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    when (state.mode) {
                        AuthMode.SignIn -> R.string.auth_sign_in_submit
                        AuthMode.SignUp -> R.string.auth_sign_up_submit
                    },
                ),
            )
        }

        if (state.googleSignInAvailable) {
            OutlinedButton(
                onClick = actions.onGoogleSignIn,
                enabled = state.canUseGoogle,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.auth_google_sign_in))
            }
        }

        if (state.mode == AuthMode.SignIn) {
            TextButton(onClick = actions.onForgotPassword) {
                Text(stringResource(R.string.auth_forgot_password))
            }
        }
    }
}

private fun EmailProblem.messageRes(): Int = when (this) {
    EmailProblem.Empty -> R.string.auth_email_error_empty
    EmailProblem.Malformed -> R.string.auth_email_error_malformed
}

@Composable
private fun passwordProblemText(problem: PasswordProblem): String = when (problem) {
    PasswordProblem.Empty -> stringResource(R.string.auth_password_error_empty)
    PasswordProblem.TooShort -> stringResource(
        R.string.auth_password_error_short,
        EmailPasswordRules.MIN_PASSWORD_LENGTH,
    )
    PasswordProblem.Mismatch -> stringResource(R.string.auth_password_error_mismatch)
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun AuthScreenSignUpPreview() {
    AnsimLinkTheme {
        AuthScreen(
            state = AuthUiState(mode = AuthMode.SignUp, googleSignInAvailable = true),
            actions = AuthActions(),
        )
    }
}
