package io.github.ndrcb5210max.ansimlink.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.account.DeletionPlan
import io.github.ndrcb5210max.ansimlink.data.account.OwnedGroupChoice
import io.github.ndrcb5210max.ansimlink.data.model.AnsimGroup
import io.github.ndrcb5210max.ansimlink.data.model.GroupMember
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.SignedInUser
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionRecordRepository
import io.github.ndrcb5210max.ansimlink.ui.common.ErrorCard
import io.github.ndrcb5210max.ansimlink.ui.common.ServerNotConfiguredCard
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 계정 삭제. 구글 플레이 계정 삭제 정책에 맞춰 앱 안에서 끝까지 할 수 있게 한다.
 *
 * 순서: 안내 → 구독 안내 → 본인 확인(재인증) → 내가 만든 그룹 처리 선택 → 영구 삭제.
 */
@Composable
fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onPasswordChange: (String) -> Unit,
    onReauthenticate: () -> Unit,
    onChoiceChange: (groupId: String, choice: OwnedGroupChoice) -> Unit,
    onOpenSubscriptions: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.common_back))
        }
        Text(
            text = stringResource(R.string.account_delete_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!state.serverReady) {
            ServerNotConfiguredCard()
        }
        state.error?.let { ErrorCard(it) }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.account_delete_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.account_delete_retained, RETENTION_DAYS),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // 결제 코드는 5단계에서 붙인다. 지금은 안내와 링크만 둔다.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.account_delete_subscription_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.account_delete_subscription_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenSubscriptions) {
                Text(stringResource(R.string.account_delete_subscription_link))
            }
        }

        ReauthenticationSection(
            state = state,
            onPasswordChange = onPasswordChange,
            onReauthenticate = onReauthenticate,
        )

        val ownedWithOthers = state.plan?.ownedGroupsWithOthers.orEmpty()
        if (ownedWithOthers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.account_delete_owned_groups_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.account_delete_owned_groups_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ownedWithOthers.forEach { entry ->
                    OwnedGroupChoiceCard(
                        entry = entry,
                        myUid = state.user?.uid.orEmpty(),
                        choice = state.choices[entry.group.id],
                        onChoiceChange = { onChoiceChange(entry.group.id, it) },
                    )
                }
            }
        }

        Button(
            onClick = onDelete,
            enabled = state.canDelete,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_delete_submit))
        }

        if (state.deleted) {
            Text(
                text = stringResource(R.string.account_delete_done),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// 확인자료 보유 기간을 '일' 단위로 보여 준다. 숫자는 저장소 상수 하나에서만 온다.
// 이 화면·제공 기록 화면·약관·처리방침이 같은 기간을 말해야 하기 때문이다
// (ProvisionLogScreen 의 같은 상수, docs/legal/location-terms.md 제6조).
private val RETENTION_DAYS: Int =
    (ProvisionRecordRepository.RETENTION_MILLIS / (24L * 60 * 60 * 1000)).toInt()

@Composable
private fun ReauthenticationSection(
    state: DeleteAccountUiState,
    onPasswordChange: (String) -> Unit,
    onReauthenticate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.account_delete_reauth_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(
                if (state.usesGoogleReauth) {
                    R.string.account_delete_reauth_google
                } else {
                    R.string.account_delete_reauth_password
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.usesGoogleReauth) {
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.auth_password_label)) },
                singleLine = true,
                enabled = !state.reauthenticated,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedButton(
            onClick = onReauthenticate,
            enabled = state.canReauthenticate && !state.reauthenticated,
        ) {
            Text(stringResource(R.string.account_delete_reauth_submit))
        }
    }
}

@Composable
private fun OwnedGroupChoiceCard(
    entry: GroupWithMembers,
    myUid: String,
    choice: OwnedGroupChoice?,
    onChoiceChange: (OwnedGroupChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = entry.group.name, style = MaterialTheme.typography.titleSmall)

            ChoiceRow(
                selected = choice is OwnedGroupChoice.Disband,
                label = stringResource(R.string.account_delete_choice_disband),
                onSelect = { onChoiceChange(OwnedGroupChoice.Disband) },
            )
            entry.members
                .filter { it.uid != myUid }
                .forEach { member ->
                    ChoiceRow(
                        selected = (choice as? OwnedGroupChoice.Transfer)?.newOwnerUid == member.uid,
                        label = stringResource(R.string.account_delete_choice_transfer) +
                            " — ${member.displayName}",
                        onSelect = { onChoiceChange(OwnedGroupChoice.Transfer(member.uid)) },
                    )
                }
        }
    }
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun DeleteAccountScreenPreview() {
    AnsimLinkTheme {
        DeleteAccountScreen(
            state = DeleteAccountUiState(
                user = SignedInUser("me", "hong@example.com", "민수", true, false),
                plan = DeletionPlan(
                    ownedGroupsWithOthers = listOf(
                        GroupWithMembers(
                            group = AnsimGroup("g1", "우리 가족", "me", listOf("me", "a")),
                            members = listOf(
                                GroupMember("me", "민수", true, false, "2026-10-24", 0),
                                GroupMember("a", "지은", true, false, "2026-10-24", 0),
                            ),
                        ),
                    ),
                    ownedGroupsAlone = emptyList(),
                    joinedGroups = emptyList(),
                ),
            ),
            onPasswordChange = {},
            onReauthenticate = {},
            onChoiceChange = { _, _ -> },
            onOpenSubscriptions = {},
            onDelete = {},
            onBack = {},
        )
    }
}
