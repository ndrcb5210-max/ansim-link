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
 *
 * @param plan 지금 요금제. 한도 숫자가 달라진다('그룹은 1개까지' ↔ '3개까지').
 *   요금제를 모르는 화면은 기본값([Plan.Free])을 쓴다 — 그 화면들은 한도 오류를
 *   낼 수 없는 화면이다(로그인, 계정).
 */
@Composable
fun AppError.message(plan: Plan = Plan.Free): String = when (this) {
    AppError.GroupLimitReached -> stringResource(
        R.string.group_limit_reached,
        PlanLimits.maxGroups(plan),
    )
    AppError.GroupFull -> stringResource(
        R.string.group_full,
        PlanLimits.maxMembers(plan),
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

/**
 * 화면 위쪽에 띄우는 오류 안내.
 *
 * @param plan 한도 오류의 숫자를 정한다([message] 참고).
 */
@Composable
fun ErrorCard(error: AppError, modifier: Modifier = Modifier, plan: Plan = Plan.Free) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = error.message(plan),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
