package io.github.ndrcb5210max.ansimlink.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.model.InviteLookup
import io.github.ndrcb5210max.ansimlink.domain.group.InviteCode
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits
import io.github.ndrcb5210max.ansimlink.ui.common.ErrorCard
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 초대 코드로 참여하기.
 *
 * 코드를 확인하면 **위치 제공 동의 화면**으로 바뀐다. 위치정보법 제19조② 가 요구하는
 * '제공받는 자'와 '제공 목적'을 화면에 보여 주고, 체크박스를 직접 눌러야만 참여된다.
 * 뒤로 가기는 동의가 아니다.
 */
@Composable
fun JoinGroupScreen(
    state: JoinGroupUiState,
    onCodeChange: (String) -> Unit,
    onLookup: () -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onJoin: () -> Unit,
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
            text = stringResource(R.string.join_group_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        state.error?.let { ErrorCard(it, plan = state.plan) }

        OutlinedTextField(
            value = state.code,
            onValueChange = { onCodeChange(InviteCode.normalize(it).take(InviteCode.LENGTH)) },
            label = { Text(stringResource(R.string.join_group_code_label)) },
            singleLine = true,
            enabled = state.invite == null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.invite == null) {
            Button(
                onClick = onLookup,
                enabled = state.canLookup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.join_group_lookup))
            }
        } else {
            LocationConsentCard(
                invite = state.invite,
                plan = state.plan,
                checked = state.consentChecked,
                onCheckedChange = onConsentChange,
            )
            Button(
                onClick = onJoin,
                enabled = state.canJoin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.join_group_submit))
            }
        }
    }
}

@Composable
private fun LocationConsentCard(
    invite: InviteLookup,
    plan: Plan,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.join_group_consent_title),
                style = MaterialTheme.typography.titleMedium,
            )
            // 제공받는 자 · 제공 목적 · 제공 기간을 한 문장으로 보여 준다(위치정보법 제19조②).
            Text(
                text = stringResource(
                    R.string.join_group_consent_summary,
                    invite.groupName,
                    PlanLimits.maxShareMinutes(plan),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                text = stringResource(R.string.join_group_receivers_title),
                style = MaterialTheme.typography.titleSmall,
            )
            invite.memberNames.forEach { name ->
                Text(text = "· $name", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(R.string.join_group_receivers_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = onCheckedChange,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Text(
                    text = stringResource(R.string.join_group_consent_checkbox),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(
                text = stringResource(R.string.join_group_consent_withdrawable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun JoinGroupConsentPreview() {
    AnsimLinkTheme {
        JoinGroupScreen(
            state = JoinGroupUiState(
                code = "KH3M7P",
                invite = InviteLookup(
                    code = "KH3M7P",
                    groupId = "g1",
                    groupName = "우리 가족",
                    memberNames = listOf("민수", "지은"),
                    expiresAtMillis = 0,
                ),
            ),
            onCodeChange = {},
            onLookup = {},
            onConsentChange = {},
            onJoin = {},
            onBack = {},
        )
    }
}
