package io.github.ndrcb5210max.ansimlink.ui.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.alert.AlertEventRepository
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertConfidence
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertText
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerReason
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 발동 기록 화면. 최근 [AlertEventRepository.RECENT_LIMIT] 건을 보여 준다.
 *
 * 발동하지 않은 기록도 **이유와 함께** 보여 준다. '재난문자를 받았는데 아무 일도 없었다'를
 * 사용자가 스스로 확인할 수 있어야 하기 때문이다.
 */
@Composable
fun AlertLogScreen(
    state: AlertLogUiState,
    onClear: () -> Unit,
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
            text = stringResource(R.string.alert_log_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(
                R.string.alert_log_subtitle,
                AlertEventRepository.RECENT_LIMIT,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.loading -> Text(
                text = stringResource(R.string.group_loading),
                style = MaterialTheme.typography.bodyMedium,
            )

            state.items.isEmpty() -> EmptyCard()

            else -> state.items.forEach { item -> LogRow(item) }
        }

        Text(
            text = stringResource(
                R.string.alert_log_notice,
                AlertText.PREVIEW_LENGTH,
                AlertEventRepository.RETENTION_MILLIS / (24L * 60 * 60 * 1000),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.items.isNotEmpty()) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.alert_log_clear))
            }
        }
    }
}

@Composable
private fun EmptyCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.alert_log_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.alert_log_empty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogRow(item: AlertLogItem, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.severity.label + " · " + item.type.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TriggeredChip(item.triggered)
            }

            Text(
                text = stringResource(
                    R.string.alert_log_meta,
                    item.timeText,
                    stringResource(item.confidence.labelRes()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = item.bodyPreview.ifBlank {
                    stringResource(R.string.alert_log_no_body)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            item.categoryName
                ?.takeIf { it != item.type.label }
                ?.let { category ->
                    Text(
                        text = stringResource(R.string.alert_log_category, category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            Text(
                text = stringResource(item.reason.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TriggeredChip(triggered: Boolean, modifier: Modifier = Modifier) {
    AssistChip(
        modifier = modifier,
        onClick = {},
        enabled = false,
        label = {
            Text(
                stringResource(
                    if (triggered) {
                        R.string.alert_log_triggered
                    } else {
                        R.string.alert_log_not_triggered
                    },
                ),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = if (triggered) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    )
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun AlertLogScreenPreview() {
    AnsimLinkTheme {
        AlertLogScreen(
            state = AlertLogUiState(
                loading = false,
                items = listOf(
                    AlertLogItem(
                        id = 3,
                        timeText = "10월 25일 05:12",
                        severity = AlertSeverity.Emergency,
                        type = DisasterType.Earthquake,
                        categoryName = "지진",
                        confidence = AlertConfidence.High,
                        bodyPreview = "[기상청] 10-25 05:12 경북 경주시 남남서쪽 19km 지역 규모 4.2 지진발생/낙…",
                        triggered = true,
                        reason = TriggerReason.Triggered,
                    ),
                    AlertLogItem(
                        id = 2,
                        timeText = "10월 24일 14:03",
                        severity = AlertSeverity.PublicSafety,
                        type = DisasterType.HeatWave,
                        categoryName = "폭염",
                        confidence = AlertConfidence.High,
                        bodyPreview = "[○○시] 오늘 폭염경보 발효. 낮 시간 야외활동을 줄이고 물을 자주 드세요",
                        triggered = false,
                        reason = TriggerReason.SeverityDisabled,
                    ),
                ),
            ),
            onClear = {},
            onBack = {},
        )
    }
}
