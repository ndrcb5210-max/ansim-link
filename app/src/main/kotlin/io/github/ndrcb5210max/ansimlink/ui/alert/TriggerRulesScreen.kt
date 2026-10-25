package io.github.ndrcb5210max.ansimlink.ui.alert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.alert.SeverityRule
import io.github.ndrcb5210max.ansimlink.domain.alert.TriggerEngine
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 발동 규칙 화면.
 *
 * 문자 종류 4행 × 재난 유형 12열을 표로 그리면 작은 화면에서 읽을 수 없다.
 * 그래서 **문자 종류마다 카드 하나**를 두고, 카드 안에 스위치 하나와 유형 칩 12개를 둔다.
 * 스위치를 끄면 칩이 흐려지지만 고른 내용은 그대로 남는다(다시 켜면 그대로 쓰인다).
 */
@Composable
fun TriggerRulesScreen(
    state: TriggerRulesUiState,
    onSeverityEnabled: (AlertSeverity, Boolean) -> Unit,
    onTypeEnabled: (AlertSeverity, DisasterType, Boolean) -> Unit,
    onSelectAllTypes: (AlertSeverity, Boolean) -> Unit,
    onReset: () -> Unit,
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
            text = stringResource(R.string.rules_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.rules_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AlertSeverity.configurableEntries.forEach { severity ->
            SeverityCard(
                severity = severity,
                rule = state.rules.rule(severity),
                onEnabledChange = { onSeverityEnabled(severity, it) },
                onTypeChange = { type, on -> onTypeEnabled(severity, type, on) },
                onSelectAll = { onSelectAllTypes(severity, it) },
            )
        }

        NoticeList()

        TextButton(onClick = onReset) {
            Text(stringResource(R.string.rules_reset))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeverityCard(
    severity: AlertSeverity,
    rule: SeverityRule,
    onEnabledChange: (Boolean) -> Unit,
    onTypeChange: (DisasterType, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectable = DisasterType.selectableEntries
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = severity.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
            }

            Text(
                text = stringResource(R.string.rules_types_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectable.forEach { type ->
                    FilterChip(
                        selected = type in rule.types,
                        onClick = { onTypeChange(type, type !in rule.types) },
                        enabled = rule.enabled,
                        label = { Text(type.label) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        R.string.rules_types_summary,
                        rule.types.size,
                        selectable.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSelectAll(rule.types.size < selectable.size) },
                    enabled = rule.enabled,
                ) {
                    Text(
                        stringResource(
                            if (rule.types.size < selectable.size) {
                                R.string.rules_select_all
                            } else {
                                R.string.rules_clear_all
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            stringResource(R.string.rules_notice_unknown_severity),
            stringResource(R.string.rules_notice_unknown_type),
            stringResource(
                R.string.rules_notice_dedupe,
                TriggerEngine.DEDUPE_WINDOW_MILLIS / 60_000,
            ),
        ).forEach { notice ->
            Text(
                text = "· " + notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun TriggerRulesScreenPreview() {
    AnsimLinkTheme {
        TriggerRulesScreen(
            state = TriggerRulesUiState(),
            onSeverityEnabled = { _, _ -> },
            onTypeEnabled = { _, _, _ -> },
            onSelectAllTypes = { _, _ -> },
            onReset = {},
            onBack = {},
        )
    }
}
