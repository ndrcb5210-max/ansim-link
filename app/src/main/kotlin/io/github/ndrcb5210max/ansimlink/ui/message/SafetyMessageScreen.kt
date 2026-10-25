package io.github.ndrcb5210max.ansimlink.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.message.SafetyMessages
import io.github.ndrcb5210max.ansimlink.ui.share.SafetyMessageUiState
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 안심 메시지 편집.
 *
 * 무료는 공통 메시지 하나, 프리미엄은 재난 유형별 메시지까지 쓸 수 있다.
 * 무료에서도 유형별 입력란을 **보여 주되** 쓸 수 없게 한다. 숨기면 프리미엄에 무엇이
 * 있는지 알 수 없고, 열어 두면 적어 놓고 안 가는 혼란이 생긴다.
 *
 * '보내지는 모습' 미리보기를 둔 이유: 이 문구는 받는 쪽 알림 한 줄로 보인다.
 * 쓰는 동안에는 길게 쓰기 쉬운데, 알림에서 잘려 보이면 쓸모가 없다.
 *
 * 문구 아래에 'SMS 로 보내지 않는다'를 적는다. '메시지를 보낸다'는 말을 보면
 * 문자 요금을 걱정하는 사람이 있다(CLAUDE.md 원칙 5: SMS 를 쓰지 않는다).
 */
@Composable
fun SafetyMessageScreen(
    state: SafetyMessageUiState,
    onCommonChange: (String) -> Unit,
    onTypeChange: (DisasterType, String) -> Unit,
    onReset: () -> Unit,
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
            text = stringResource(R.string.message_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.message_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        MessageField(
            labelRes = R.string.message_common_label,
            hintRes = R.string.message_common_hint,
            value = state.common,
            onChange = onCommonChange,
            enabled = true,
        )
        TextButton(onClick = onReset) {
            Text(stringResource(R.string.message_reset))
        }

        PreviewCard(senderName = state.senderName, message = state.common)

        HorizontalDivider()

        Text(
            text = stringResource(R.string.message_per_type_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!state.premium) {
            Text(
                text = stringResource(R.string.message_per_type_premium),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.message_per_type_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DisasterType.selectableEntries.forEach { type ->
            OutlinedTextField(
                value = state.byType[type].orEmpty(),
                onValueChange = { onTypeChange(type, it) },
                enabled = state.premium,
                label = { Text(type.label) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.message_length,
                            state.byType[type]?.length ?: 0,
                            SafetyMessages.MAX_LENGTH,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MessageField(
    labelRes: Int,
    hintRes: Int,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(stringResource(labelRes)) },
        placeholder = { Text(stringResource(hintRes)) },
        supportingText = {
            Text(stringResource(R.string.message_length, value.length, SafetyMessages.MAX_LENGTH))
        },
        minLines = 2,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 받는 쪽 알림이 어떻게 보이는지. 긴 문구가 잘리는 것을 눈으로 확인할 수 있다. */
@Composable
private fun PreviewCard(
    senderName: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.message_preview_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = senderName.ifBlank { stringResource(R.string.app_name) },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = message.ifBlank { SafetyMessages.DEFAULT },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun SafetyMessageScreenPreview() {
    AnsimLinkTheme {
        SafetyMessageScreen(
            state = SafetyMessageUiState(
                loading = false,
                common = SafetyMessages.DEFAULT,
                senderName = "민수",
            ),
            onCommonChange = {},
            onTypeChange = { _, _ -> },
            onReset = {},
            onBack = {},
        )
    }
}
