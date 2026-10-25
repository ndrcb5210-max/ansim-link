package io.github.ndrcb5210max.ansimlink.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.ui.share.ShareBlockMessages
import io.github.ndrcb5210max.ansimlink.ui.share.ShareUiState
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 홈 화면.
 *
 * 카드 두 개. '재난문자 감지'(3단계)와 '위치 공유'(4단계).
 *
 * 공유 중일 때 이 카드가 **앱 안 배너** 역할을 한다(CLAUDE.md 원칙 2: 공유하는 동안에는
 * 상시 알림과 앱 안 배너를 항상 보여 준다). 상시 알림과 같은 내용을 적는다 —
 * 어느 그룹, 몇 명, 언제 시작, 언제 끝. 그리고 [지금 중지]를 둔다(원칙 3).
 *
 * @param share 공유 상태. 시각은 미리 문자열로 받는다(스크린샷을 고정하려는 것).
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    detecting: Boolean = false,
    share: ShareUiState = ShareUiState(loading = false),
    onOpenDetectionSetup: () -> Unit = {},
    onStartSharing: () -> Unit = {},
    onStopSharing: () -> Unit = {},
    onOpenFamilyLocation: () -> Unit = {},
    onOpenShareSettings: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.home_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(
            title = stringResource(R.string.home_detection_title),
            state = stringResource(
                if (detecting) R.string.home_detection_state_on else R.string.home_detection_state_off,
            ),
            description = stringResource(
                if (detecting) {
                    R.string.home_detection_description_on
                } else {
                    R.string.home_detection_description
                },
            ),
            actionLabel = stringResource(R.string.home_detection_open),
            onAction = onOpenDetectionSetup,
        )

        ShareCard(
            share = share,
            onStartSharing = onStartSharing,
            onStopSharing = onStopSharing,
            onOpenFamilyLocation = onOpenFamilyLocation,
            onOpenShareSettings = onOpenShareSettings,
        )
    }
}

/**
 * 위치 공유 카드.
 *
 * 공유 중이면 눈에 띄는 색(primaryContainer)으로 바꾼다. 홈 화면을 열었을 때
 * '지금 내 위치가 나가고 있다'를 한눈에 알아야 한다.
 */
@Composable
private fun ShareCard(
    share: ShareUiState,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onOpenFamilyLocation: () -> Unit,
    onOpenShareSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (share.sharing) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.home_sharing_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (share.sharing) {
                        R.string.home_sharing_state_active
                    } else {
                        R.string.home_sharing_state_idle
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = if (share.sharing) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )

            if (share.sharing) {
                // 상시 알림과 같은 내용을 적는다. 둘이 다르면 어느 쪽을 믿어야 할지 모른다.
                Text(
                    text = stringResource(
                        R.string.home_sharing_active_description,
                        share.groupLabel,
                        share.endsText,
                        share.remainingMinutes,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.home_sharing_write_count, share.writeCount),
                    style = MaterialTheme.typography.bodySmall,
                )
                // '지금 중지'를 가장 누르기 쉬운 자리에 둔다(원칙 3).
                Button(onClick = onStopSharing, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_sharing_stop))
                }
            } else {
                Text(
                    text = stringResource(R.string.home_sharing_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 못 시작하는 이유가 있으면 버튼을 누르기 전에 보여 준다.
                share.blockReason?.let { reason ->
                    Text(
                        text = stringResource(ShareBlockMessages.messageRes(reason)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    onClick = onStartSharing,
                    enabled = share.canStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.home_sharing_start))
                }
            }

            TextButton(onClick = onOpenFamilyLocation) {
                Text(stringResource(R.string.home_sharing_open_family))
            }
            TextButton(onClick = onOpenShareSettings) {
                Text(stringResource(R.string.settings_share_title))
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    state: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = state,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AnsimLinkTheme {
        HomeScreen()
    }
}
