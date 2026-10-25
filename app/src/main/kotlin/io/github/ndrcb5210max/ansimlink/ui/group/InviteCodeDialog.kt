package io.github.ndrcb5210max.ansimlink.ui.group

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.model.InviteLookup
import io.github.ndrcb5210max.ansimlink.domain.group.InviteCode

/**
 * 방금 만든 초대 코드를 보여 준다.
 *
 * 코드는 24시간만 쓸 수 있으므로 남은 시간을 같이 적는다.
 */
@Composable
fun InviteCodeDialog(
    invite: InviteLookup,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val context = LocalContext.current
    val remaining = InviteCode.remainingMinutes(invite.expiresAtMillis, nowMillis)

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_invite_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = invite.code,
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.group_invite_valid, remaining),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.group_invite_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Android 13 이상에서는 시스템이 '복사했습니다' 안내를 직접 띄워 준다.
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("ansim-link-invite", invite.code))
                },
            ) {
                Text(stringResource(R.string.group_invite_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
    )
}
