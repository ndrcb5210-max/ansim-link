package io.github.ndrcb5210max.ansimlink.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits
import io.github.ndrcb5210max.ansimlink.ui.common.ErrorCard
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 그룹 만들기.
 *
 * 그룹을 만든 사람도 구성원이므로, 만드는 순간 위치 제공에 동의한 것으로 기록한다.
 * 그래서 무엇에 동의하는지 화면에 적어 둔다.
 */
@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
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
            text = stringResource(R.string.create_group_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        state.error?.let { ErrorCard(it) }

        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.create_group_name_label)) },
            placeholder = { Text(stringResource(R.string.create_group_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.create_group_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.group_member_count,
                1,
                PlanLimits.maxMembers(Plan.Free),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.create_group_submit))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateGroupScreenPreview() {
    AnsimLinkTheme {
        CreateGroupScreen(
            state = CreateGroupUiState(name = "우리 가족"),
            onNameChange = {},
            onSubmit = {},
            onBack = {},
        )
    }
}
