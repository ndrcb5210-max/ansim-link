package io.github.ndrcb5210max.ansimlink.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits

/**
 * [AppError] 를 사용자에게 보여 줄 문구로 바꾼다.
 *
 * 한도 관련 오류는 숫자를 끼워 넣어야 해서 여기서 같이 만든다.
 * 2단계에는 결제가 없으므로 한도는 항상 무료 요금제 값이다.
 */
@Composable
fun AppError.message(): String = when (this) {
    AppError.GroupLimitReached -> stringResource(
        R.string.group_limit_reached,
        PlanLimits.maxGroups(Plan.Free),
    )
    AppError.GroupFull -> stringResource(
        R.string.group_full,
        PlanLimits.maxMembers(Plan.Free),
    )
    else -> stringResource(simpleMessageRes())
}

@StringRes
private fun AppError.simpleMessageRes(): Int = when (this) {
    AppError.ServerNotConfigured -> R.string.server_not_configured_body
    AppError.Network -> R.string.error_network
    AppError.EmailAlreadyInUse -> R.string.error_email_already_in_use
    AppError.InvalidCredentials -> R.string.error_invalid_credentials
    AppError.WeakPassword -> R.string.error_weak_password
    AppError.RecentLoginRequired -> R.string.error_recent_login_required
    AppError.PermissionDenied -> R.string.error_permission_denied
    AppError.InviteNotFound -> R.string.error_invite_not_found
    AppError.InviteExpired -> R.string.error_invite_expired
    AppError.AlreadyMember -> R.string.error_already_member
    AppError.GoogleSignInUnavailable -> R.string.error_google_unavailable
    AppError.GoogleSignInCancelled -> R.string.error_google_cancelled
    AppError.GroupFull, AppError.GroupLimitReached, AppError.Unknown -> R.string.error_unknown
}

/**
 * Firebase 설정이 없거나 가짜일 때 보여 주는 안내.
 *
 * 앱이 죽지 않고 '무엇이 없어서 안 되는지'를 알려 주는 것이 목적이다.
 */
@Composable
fun ServerNotConfiguredCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.server_not_configured_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.server_not_configured_body),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 화면 위쪽에 띄우는 오류 안내. */
@Composable
fun ErrorCard(error: AppError, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = error.message(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
