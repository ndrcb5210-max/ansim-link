package io.github.ndrcb5210max.ansimlink.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
 * 홈 화면.
 *
 * 3단계에서 '재난문자 감지' 카드가 실제 상태를 보여 준다. '위치 공유' 카드는 4단계에서 연결한다.
 *
 * @param detecting 알림 접근이 켜져 있고 리스너가 연결돼 있는지.
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    detecting: Boolean = false,
    onOpenDetectionSetup: () -> Unit = {},
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
        StatusCard(
            title = stringResource(R.string.home_sharing_title),
            state = stringResource(R.string.home_sharing_state_idle),
            description = stringResource(R.string.home_sharing_description),
        )
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
