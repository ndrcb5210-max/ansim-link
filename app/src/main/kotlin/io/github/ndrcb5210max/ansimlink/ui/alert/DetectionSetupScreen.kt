package io.github.ndrcb5210max.ansimlink.ui.alert

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
import io.github.ndrcb5210max.ansimlink.data.alert.AlertEventRepository
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertPackages
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertText
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 재난문자 감지를 켜기 전에 보여 주는 설명 화면(명시적 공개).
 *
 * CLAUDE.md 작업 규칙: 권한을 요청하기 전에 왜 필요한지 먼저 설명한다.
 * 알림 접근은 **휴대폰의 모든 알림**을 볼 수 있는 센 권한이므로, 설정 화면으로 보내기 전에
 * ① 왜 필요한지 ② 무엇을 읽는지 ③ 무엇을 저장하지 않는지 ④ 보장할 수 없는 점을 먼저 적는다.
 *
 * 순서가 중요하다. 설명을 읽기 전에는 '설정 열기' 버튼에 닿지 않도록 설명을 위에 둔다.
 */
@Composable
fun DetectionSetupScreen(
    state: DetectionUiState,
    onOpenNotificationAccessSettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
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
            text = stringResource(R.string.detection_setup_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        StateCard(state)

        Section(
            title = stringResource(R.string.detection_why_title),
            body = stringResource(R.string.detection_why_body),
        )

        BulletCard(
            title = stringResource(R.string.detection_read_title),
            bullets = listOf(
                stringResource(R.string.detection_read_item_apps),
                stringResource(R.string.detection_read_item_fields),
            ),
        )

        BulletCard(
            title = stringResource(R.string.detection_not_stored_title),
            bullets = listOf(
                stringResource(R.string.detection_not_stored_item_others),
                stringResource(
                    R.string.detection_not_stored_item_body,
                    AlertText.PREVIEW_LENGTH,
                    RETENTION_DAYS,
                ),
                stringResource(R.string.detection_not_stored_item_server),
            ),
        )

        BulletCard(
            title = stringResource(R.string.detection_target_packages_title),
            bullets = AlertPackages.known.toList(),
        )

        // 과장 금지(CLAUDE.md): '놓치지 않습니다' 같은 보장 표현을 쓰지 않고,
        // 감지되지 않을 수 있는 이유를 그대로 적는다.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.detection_no_guarantee_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.detection_no_guarantee_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Button(
            onClick = onOpenNotificationAccessSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.detection_open_settings))
        }
        if (state.settingsNotFound) {
            Text(
                text = stringResource(R.string.detection_settings_not_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        NotificationPermissionSection(
            granted = state.notificationsEnabled,
            onRequest = onRequestNotificationPermission,
        )

        // 사이드로드(APK 설치) 안내. Android 13+ 의 '제한된 설정' 때문에 설정 화면에서
        // 토글이 회색으로 막혀 있는 경우가 있다.
        Section(
            title = stringResource(R.string.detection_restricted_title),
            body = stringResource(R.string.detection_restricted_body),
        )
        OutlinedButton(onClick = onOpenAppDetails, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.detection_open_app_details))
        }
    }
}

/** 보관 기간을 '일' 단위로 보여 준다. */
private val RETENTION_DAYS: Int =
    (AlertEventRepository.RETENTION_MILLIS / (24L * 60 * 60 * 1000)).toInt()

@Composable
private fun StateCard(state: DetectionUiState, modifier: Modifier = Modifier) {
    val stateText = when {
        state.detecting -> stringResource(R.string.detection_state_on)
        state.accessGranted -> stringResource(R.string.detection_state_granted_not_connected)
        else -> stringResource(R.string.detection_state_off)
    }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.home_detection_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stateText,
                style = MaterialTheme.typography.headlineSmall,
                color = if (state.detecting) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun NotificationPermissionSection(
    granted: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.detection_post_notifications_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.detection_post_notifications_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (granted) {
            Text(
                text = stringResource(R.string.detection_post_notifications_granted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            OutlinedButton(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.detection_post_notifications_button))
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BulletCard(
    title: String,
    bullets: List<String>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            bullets.forEach { bullet ->
                Text(
                    text = "· " + bullet,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun DetectionSetupScreenPreview() {
    AnsimLinkTheme {
        DetectionSetupScreen(
            state = DetectionUiState(),
            onOpenNotificationAccessSettings = {},
            onOpenAppDetails = {},
            onRequestNotificationPermission = {},
            onBack = {},
        )
    }
}
