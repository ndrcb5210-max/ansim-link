package io.github.ndrcb5210max.ansimlink.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.model.SignedInUser
import io.github.ndrcb5210max.ansimlink.ui.common.ErrorCard
import io.github.ndrcb5210max.ansimlink.ui.common.ServerNotConfiguredCard
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/** 설정 > 계정. 로그인 정보, 로그아웃, 계정 삭제로 가는 입구. */
@Composable
fun AccountScreen(
    state: AccountUiState,
    onResendVerification: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back))
        }
        Text(
            text = stringResource(R.string.account_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!state.serverReady) {
            ServerNotConfiguredCard()
        }
        state.error?.let { ErrorCard(it) }

        state.user?.let { user ->
            Text(text = user.email.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    if (user.emailVerified) {
                        R.string.account_email_verified
                    } else {
                        R.string.account_email_unverified
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!user.emailVerified && !user.signedInWithGoogle) {
                OutlinedButton(onClick = onResendVerification, enabled = !state.busy) {
                    Text(stringResource(R.string.account_resend_verification))
                }
            }
            if (state.verificationEmailSent) {
                Text(
                    text = stringResource(R.string.auth_verification_sent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_sign_out))
        }

        Button(
            onClick = onDeleteAccount,
            enabled = !state.busy && state.user != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_delete))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountScreenPreview() {
    AnsimLinkTheme {
        AccountScreen(
            state = AccountUiState(
                user = SignedInUser(
                    uid = "u1",
                    email = "hong@example.com",
                    displayName = "민수",
                    emailVerified = false,
                    signedInWithGoogle = false,
                ),
            ),
            onResendVerification = {},
            onSignOut = {},
            onDeleteAccount = {},
            onBack = {},
        )
    }
}
