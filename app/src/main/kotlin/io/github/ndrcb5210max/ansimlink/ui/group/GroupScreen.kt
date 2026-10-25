package io.github.ndrcb5210max.ansimlink.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.model.AnsimGroup
import io.github.ndrcb5210max.ansimlink.data.model.GroupMember
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits
import io.github.ndrcb5210max.ansimlink.domain.group.ShareState
import io.github.ndrcb5210max.ansimlink.ui.common.ErrorCard
import io.github.ndrcb5210max.ansimlink.ui.common.ServerNotConfiguredCard
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/** 그룹 탭에서 쓰는 동작들. */
data class GroupActions(
    val onCreateGroup: () -> Unit = {},
    val onJoinGroup: () -> Unit = {},
    val onCreateInvite: (groupId: String) -> Unit = {},
    val onSetPaused: (groupId: String, paused: Boolean) -> Unit = { _, _ -> },
    val onWithdraw: (groupId: String) -> Unit = {},
    val onDismissInvite: () -> Unit = {},
)

/**
 * 그룹 탭. 내가 속한 그룹과 구성원, 각자의 공유 상태를 모두에게 보여 준다(CLAUDE.md 원칙 10).
 */
@Composable
fun GroupScreen(
    state: GroupUiState,
    actions: GroupActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!state.serverReady) {
            ServerNotConfiguredCard()
        }
        state.error?.let { ErrorCard(it) }

        when {
            state.loading -> Text(
                text = stringResource(R.string.group_loading),
                style = MaterialTheme.typography.bodyMedium,
            )

            state.isEmpty -> EmptyGroups(state, actions)

            else -> {
                state.groups.forEach { entry ->
                    GroupCard(
                        entry = entry,
                        myUid = state.myUid,
                        maxMembers = PlanLimits.maxMembers(state.plan),
                        actions = actions,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = actions.onCreateGroup,
                        enabled = state.canCreateGroup,
                    ) {
                        Text(stringResource(R.string.group_create))
                    }
                    OutlinedButton(onClick = actions.onJoinGroup, enabled = !state.busy) {
                        Text(stringResource(R.string.group_join))
                    }
                }
                if (!state.canCreateGroup && state.serverReady) {
                    Text(
                        text = stringResource(
                            R.string.group_limit_reached,
                            PlanLimits.maxGroups(state.plan),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    state.freshInvite?.let { invite ->
        InviteCodeDialog(invite = invite, onDismiss = actions.onDismissInvite)
    }
}

@Composable
private fun EmptyGroups(state: GroupUiState, actions: GroupActions) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.group_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.group_empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = actions.onCreateGroup, enabled = state.canCreateGroup) {
            Text(stringResource(R.string.group_create))
        }
        OutlinedButton(onClick = actions.onJoinGroup, enabled = !state.busy) {
            Text(stringResource(R.string.group_join))
        }
    }
}

@Composable
private fun GroupCard(
    entry: GroupWithMembers,
    myUid: String,
    maxMembers: Int,
    actions: GroupActions,
    modifier: Modifier = Modifier,
) {
    val me = entry.memberOf(myUid)
    val isOwner = entry.group.isOwnedBy(myUid)
    var confirmWithdraw by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = entry.group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.group_member_count,
                    entry.group.memberCount,
                    maxMembers,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isOwner) {
                Text(
                    text = stringResource(R.string.group_owner_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()
            Text(
                text = stringResource(R.string.group_members_title),
                style = MaterialTheme.typography.titleSmall,
            )
            entry.members.forEach { member ->
                MemberRow(member = member, isMe = member.uid == myUid)
            }

            HorizontalDivider()
            Text(
                text = stringResource(R.string.group_my_share_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.group_my_share_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        actions.onSetPaused(entry.group.id, me?.paused != true)
                    },
                    enabled = me != null && me.locationConsent,
                ) {
                    Text(
                        stringResource(
                            if (me?.paused == true) {
                                R.string.group_action_resume
                            } else {
                                R.string.group_action_pause
                            },
                        ),
                    )
                }
                TextButton(
                    onClick = { confirmWithdraw = true },
                    enabled = me != null && !isOwner,
                ) {
                    Text(stringResource(R.string.group_action_withdraw))
                }
            }
            if (isOwner) {
                Text(
                    text = stringResource(R.string.group_owner_cannot_withdraw),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isOwner) {
                HorizontalDivider()
                OutlinedButton(onClick = { actions.onCreateInvite(entry.group.id) }) {
                    Text(stringResource(R.string.group_invite_create))
                }
            }
        }
    }

    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text(stringResource(R.string.group_withdraw_confirm_title)) },
            text = { Text(stringResource(R.string.group_withdraw_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWithdraw = false
                        actions.onWithdraw(entry.group.id)
                    },
                ) {
                    Text(stringResource(R.string.group_action_withdraw))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdraw = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun MemberRow(member: GroupMember, isMe: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isMe) "${member.displayName} (나)" else member.displayName,
            style = MaterialTheme.typography.bodyMedium,
        )
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(stringResource(member.shareState.labelRes())) },
        )
    }
}

private fun ShareState.labelRes(): Int = when (this) {
    ShareState.Consented -> R.string.group_share_state_consented
    ShareState.Paused -> R.string.group_share_state_paused
    ShareState.Withdrawn -> R.string.group_share_state_withdrawn
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun GroupScreenPreview() {
    AnsimLinkTheme {
        GroupScreen(
            state = GroupUiState(
                loading = false,
                myUid = "me",
                groups = listOf(
                    GroupWithMembers(
                        group = AnsimGroup("g1", "우리 가족", "me", listOf("me", "a", "b")),
                        members = listOf(
                            GroupMember("me", "민수", true, false, "2026-10-24", 0),
                            GroupMember("a", "지은", true, true, "2026-10-24", 0),
                            GroupMember("b", "하늘", false, false, "2026-10-24", 0),
                        ),
                    ),
                ),
            ),
            actions = GroupActions(),
        )
    }
}
