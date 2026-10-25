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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 디버그 빌드 전용 '받은 알림 패키지 기록'.
 *
 * 왜 필요한가: 삼성 기기의 재난문자 앱 패키지 이름을 아직 모른다
 * ([io.github.ndrcb5210max.ansimlink.domain.alert.AlertPackages] 의 TODO).
 * 실기기에서 이 화면으로 확인한 뒤 허용 목록에 넣는다.
 *
 * **패키지 이름과 횟수·시각만** 쌓는다. 허용 목록 밖 알림의 제목·본문은 읽지 않는다.
 */
@Composable
fun ObservedPackagesScreen(
    state: ObservedPackagesUiState,
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
            text = stringResource(R.string.observed_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.observed_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            state.loading -> Text(
                text = stringResource(R.string.group_loading),
                style = MaterialTheme.typography.bodyMedium,
            )

            state.items.isEmpty() -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.observed_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }

            else -> Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    state.items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider()
                        ListItem(
                            headlineContent = { Text(item.packageName) },
                            supportingContent = {
                                Text(
                                    text = stringResource(
                                        R.string.observed_count,
                                        item.count,
                                        item.lastSeenText,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            trailingContent = if (item.known) {
                                {
                                    AssistChip(
                                        onClick = {},
                                        enabled = false,
                                        label = {
                                            Text(stringResource(R.string.observed_known_badge))
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        if (state.items.isNotEmpty()) {
            Row {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.observed_clear))
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ObservedPackagesScreenPreview() {
    AnsimLinkTheme {
        ObservedPackagesScreen(
            state = ObservedPackagesUiState(
                loading = false,
                items = listOf(
                    ObservedPackageItem(
                        packageName = "com.samsung.android.messaging",
                        count = 12,
                        lastSeenText = "10월 25일 09:41",
                        known = false,
                    ),
                    ObservedPackageItem(
                        packageName = "com.android.cellbroadcastreceiver",
                        count = 2,
                        lastSeenText = "10월 25일 05:12",
                        known = true,
                    ),
                ),
            ),
            onClear = {},
            onBack = {},
        )
    }
}
