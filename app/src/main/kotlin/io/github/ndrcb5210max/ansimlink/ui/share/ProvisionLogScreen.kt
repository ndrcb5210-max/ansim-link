package io.github.ndrcb5210max.ansimlink.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose
import io.github.ndrcb5210max.ansimlink.data.share.ProvisionRecordRepository
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 내 위치 제공 기록.
 *
 * 위치정보법 제19조③은 위치정보를 제3자에게 제공할 때마다 '누구에게·언제·무슨 목적으로'
 * 제공했는지 즉시 알리고 그 내역을 확인할 수 있게 하라고 한다. 이 화면이 그 '확인'이다.
 *
 * **좌표는 보여 주지 않는다. 애초에 저장하지 않는다.** 제공 내역은 약관에서 정한 기간
 * (180일) 동안 남아야 하는데 좌표는 24시간 뒤 지워야 하므로(CLAUDE.md 원칙 4) 같은 자리에
 * 둘 수 없다. 그 사실을 화면 머리말에 적어 둔다.
 */
@Composable
fun ProvisionLogScreen(
    state: ProvisionLogUiState,
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
            text = stringResource(R.string.provision_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.provision_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.provision_retention, RETENTION_DAYS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.loading -> Text(
                text = stringResource(R.string.group_loading),
                style = MaterialTheme.typography.bodyMedium,
            )

            state.items.isEmpty() -> EmptyCard()

            else -> state.items.forEach { item -> LogCard(item) }
        }
    }
}

// 보관 기간을 '일' 단위로 보여 준다. 숫자는 저장소 상수 하나에서만 온다
// (계정 삭제 화면도 같은 상수를 쓴다).
private val RETENTION_DAYS: Int =
    (ProvisionRecordRepository.RETENTION_MILLIS / (24L * 60 * 60 * 1000)).toInt()

@Composable
private fun EmptyCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.provision_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.provision_empty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogCard(item: ProvisionLogItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.ongoing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = item.groupName, style = MaterialTheme.typography.titleMedium)

            Text(
                text = if (item.ongoing) {
                    stringResource(R.string.provision_ongoing, item.plannedEndText)
                } else {
                    item.periodText
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            // 목적을 칩으로 따로 보여 준다. 제19조③의 '목적'에 해당하는 값이라
                // 다른 줄과 섞이지 않게 둔다.
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(item.purpose.labelRes())) },
            )

            Text(
                text = if (item.receiverNames.isEmpty()) {
                    stringResource(R.string.provision_receivers_none)
                } else {
                    stringResource(
                        R.string.provision_receivers,
                        item.receiverNames.joinToString(", "),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
            )

            if (!item.ongoing) {
                Text(
                    text = stringResource(R.string.provision_write_count, item.writeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun ProvisionLogScreenPreview() {
    AnsimLinkTheme {
        ProvisionLogScreen(
            state = ProvisionLogUiState(
                loading = false,
                items = listOf(
                    ProvisionLogItem(
                        id = 2,
                        groupName = "우리 가족",
                        purpose = ProvisionPurpose.AlertPrompt,
                        periodText = "10월 25일 18:05 ~ 19:05",
                        ongoing = false,
                        plannedEndText = "19:05",
                        receiverNames = listOf("지은", "하늘"),
                        writeCount = 14,
                    ),
                ),
            ),
            onBack = {},
        )
    }
}
