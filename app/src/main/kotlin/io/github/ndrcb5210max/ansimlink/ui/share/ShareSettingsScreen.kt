package io.github.ndrcb5210max.ansimlink.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.share.AutoStartStep
import io.github.ndrcb5210max.ansimlink.domain.share.ShareDuration
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/** 위치 공유 설정 화면의 콜백 묶음. 인자가 많아 묶어 넘긴다(2단계와 같은 방식). */
data class ShareSettingsActions(
    val onShareMinutesChange: (Int) -> Unit = {},
    val onOpenLocationDisclosure: () -> Unit = {},
    val onRequestLocationPermission: () -> Unit = {},
    val onOpenAppLocationSettings: () -> Unit = {},
    val onAcceptAutoStartDisclosure: () -> Unit = {},
    val onAutoStartChange: (Boolean) -> Unit = {},
    val onRequestNotificationPermission: () -> Unit = {},
    val onOpenProvisionLog: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * 위치 공유 설정.
 *
 * 두 덩어리다.
 * 1. **공유 시간** — 기본 60분. 무료는 60분까지, 프리미엄은 180분까지.
 * 2. **자동 시작** — 선택 기능. 켜는 순서를 반드시 지킨다
 *    (명시적 공개 → 위치 권한 → 배경 위치 권한). 순서는
 *    [io.github.ndrcb5210max.ansimlink.domain.share.AutoStartFlow] 가 정하고
 *    이 화면은 그 단계에 맞는 것 **하나만** 보여 준다. 그래야 사용자가 공개 화면을
 *    건너뛰고 권한 버튼을 먼저 누르는 일이 생기지 않는다.
 *
 * 끄는 쪽은 언제나 한 번에 된다(스위치를 내리면 끝).
 */
@Composable
fun ShareSettingsScreen(
    state: ShareSettingsUiState,
    actions: ShareSettingsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = actions.onBack) {
            Text(stringResource(R.string.common_back))
        }
        Text(
            text = stringResource(R.string.settings_share_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        DurationSection(state = state, onChange = actions.onShareMinutesChange)

        HorizontalDivider()

        LocationPermissionSection(
            granted = state.locationPermissionGranted,
            onOpenDisclosure = actions.onOpenLocationDisclosure,
        )

        HorizontalDivider()

        AutoStartSection(state = state, actions = actions)

        if (!state.notificationsEnabled) {
            HorizontalDivider()
            NotificationSection(onRequest = actions.onRequestNotificationPermission)
        }

        HorizontalDivider()

        // 제공 기록으로 가는 길을 설정 화면과 여기 둘 다 둔다. 위치정보법 제19조③의
        // '기록을 볼 수 있게 한다'는 기능이라 찾기 쉬워야 한다.
        OutlinedButton(onClick = actions.onOpenProvisionLog, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.provision_title))
        }
    }
}

@Composable
private fun DurationSection(
    state: ShareSettingsUiState,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.share_duration_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.share_duration_summary, state.shareMinutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShareDuration.selectableMinutes.forEach { minutes ->
                val allowed = minutes <= state.maxAllowedMinutes
                FilterChip(
                    selected = state.shareMinutes == minutes,
                    // 프리미엄 전용 값도 **보여 주되** 고를 수 없게 한다.
                    // 숨기면 '프리미엄에 무엇이 있는지' 알 수 없다.
                    enabled = allowed,
                    onClick = { onChange(minutes) },
                    label = { Text(stringResource(R.string.share_duration_minutes, minutes)) },
                )
            }
        }
        if (state.maxAllowedMinutes < ShareDuration.MAX_MINUTES) {
            Text(
                text = stringResource(
                    R.string.share_duration_premium_note,
                    state.maxAllowedMinutes,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LocationPermissionSection(
    granted: Boolean,
    onOpenDisclosure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.location_disclosure_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (granted) {
            Text(
                text = stringResource(R.string.location_disclosure_granted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = stringResource(R.string.location_disclosure_what_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 권한을 바로 요청하지 않는다. 설명 화면을 먼저 거친다(CLAUDE.md 작업 규칙).
            OutlinedButton(onClick = onOpenDisclosure, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.location_disclosure_allow))
            }
        }
    }
}

@Composable
private fun AutoStartSection(
    state: ShareSettingsUiState,
    actions: ShareSettingsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auto_start_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        if (state.autoStartEnabled) {
                            R.string.auto_start_summary_on
                        } else {
                            R.string.auto_start_summary_off
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.autoStartEnabled,
                // 켤 수 있는 것은 세 단계를 다 거친 뒤다. 끄는 것은 언제나 된다.
                enabled = state.autoStartEnabled || state.autoStartStep == AutoStartStep.Ready,
                onCheckedChange = actions.onAutoStartChange,
            )
        }

        // 아직 못 켜는 상태면 '지금 해야 할 한 단계'만 보여 준다.
        if (!state.autoStartEnabled) {
            when (state.autoStartStep) {
                AutoStartStep.Disclosure -> AutoStartDisclosure(
                    onAccept = actions.onAcceptAutoStartDisclosure,
                )

                AutoStartStep.LocationPermission -> AutoStartStepCard(
                    titleRes = R.string.auto_start_step_location_title,
                    bodyRes = R.string.auto_start_step_location_body,
                    buttonRes = R.string.location_disclosure_allow,
                    onClick = actions.onRequestLocationPermission,
                )

                AutoStartStep.BackgroundPermission -> AutoStartStepCard(
                    titleRes = R.string.auto_start_step_background_title,
                    bodyRes = R.string.auto_start_step_background_body,
                    buttonRes = R.string.auto_start_step_background_button,
                    onClick = actions.onOpenAppLocationSettings,
                )

                AutoStartStep.Ready -> Text(
                    text = stringResource(R.string.auto_start_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text(
            text = stringResource(R.string.auto_start_fallback_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 자동 시작의 명시적 공개(prominent disclosure).
 *
 * Google Play 배경 위치 정책이 요구하는 것을 그대로 담는다.
 * ① 앱이 닫혀 있을 때도 수집한다는 사실, ② 그 위치를 쓰는 기능 목록,
 * ③ 다른 데 쓰지 않는다는 사실. 사용자가 **버튼을 눌러야** 다음으로 간다
 * (뒤로 가기는 동의가 아니다 — CLAUDE.md 원칙 8과 같은 생각).
 */
@Composable
private fun AutoStartDisclosure(onAccept: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.auto_start_disclosure_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.auto_start_disclosure_body),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.auto_start_disclosure_uses_title),
                style = MaterialTheme.typography.titleSmall,
            )
            listOf(
                R.string.auto_start_disclosure_use_share,
                R.string.auto_start_disclosure_use_distance,
            ).forEach { bulletRes ->
                Text(
                    text = "· " + stringResource(bulletRes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.auto_start_disclosure_use_none),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.auto_start_disclosure_optional),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.auto_start_disclosure_accept))
            }
        }
    }
}

@Composable
private fun AutoStartStepCard(
    titleRes: Int,
    bodyRes: Int,
    buttonRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(buttonRes))
            }
        }
    }
}

@Composable
private fun NotificationSection(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.notifications_required_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.notifications_required_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.detection_post_notifications_button))
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun ShareSettingsScreenPreview() {
    AnsimLinkTheme {
        ShareSettingsScreen(
            state = ShareSettingsUiState(notificationsEnabled = true),
            actions = ShareSettingsActions(),
        )
    }
}
