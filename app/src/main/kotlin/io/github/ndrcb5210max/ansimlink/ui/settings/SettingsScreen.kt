package io.github.ndrcb5210max.ansimlink.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.consent.LegalDocument
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 설정 화면에 들어갈 항목.
 *
 * 4단계에서 '위치 공유'·'안심 메시지'·'내 위치 제공 기록'이 눌린다.
 * '구독'(5단계)은 아직 자리만 있다.
 *
 * 순서는 '무엇이 일어나는가 → 무엇을 보낼 것인가 → 무엇이 일어났는가' 로 두었다.
 * 감지 설정·발동 규칙(감지) → 위치 공유·안심 메시지(발동) → 발동 기록·제공 기록(확인).
 */
private enum class SettingsEntry(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    DetectionPermission(
        R.string.settings_detection_permission_title,
        R.string.settings_detection_permission_description,
    ),
    Rules(
        R.string.settings_rules_title,
        R.string.settings_rules_description,
    ),
    Share(
        R.string.settings_share_title,
        R.string.settings_share_description,
    ),
    SafetyMessage(
        R.string.settings_message_title,
        R.string.settings_message_description,
    ),
    AlertLog(
        R.string.settings_alert_log_title,
        R.string.settings_alert_log_description,
    ),
    ProvisionLog(
        R.string.settings_provision_title,
        R.string.settings_provision_description,
    ),
    Subscription(
        R.string.settings_subscription_title,
        R.string.settings_subscription_description,
    ),
    Account(
        R.string.settings_account_title,
        R.string.settings_account_description,
    ),
}

/** 디버그 빌드에서만 보이는 항목. */
private enum class DebugEntry(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
) {
    TestAlert(
        R.string.settings_debug_test_alert_title,
        R.string.settings_debug_test_alert_description,
    ),
    ObservedPackages(
        R.string.settings_debug_observed_title,
        R.string.settings_debug_observed_description,
    ),
}

/**
 * @param showDebugEntries 디버그 빌드인지. 화면이 `BuildConfig` 를 직접 보지 않게 해서
 *   스크린샷 테스트에서 둘 다 찍을 수 있게 한다.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    showDebugEntries: Boolean = false,
    onOpenDetectionSetup: () -> Unit = {},
    onOpenRules: () -> Unit = {},
    onOpenAlertLog: () -> Unit = {},
    onOpenShareSettings: () -> Unit = {},
    onOpenSafetyMessage: () -> Unit = {},
    onOpenProvisionLog: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenTestAlert: () -> Unit = {},
    onOpenObservedPackages: () -> Unit = {},
    onOpenLegalDocument: (LegalDocument) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsEntry.entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider()
            }
            SettingsRow(
                titleRes = entry.titleRes,
                descriptionRes = entry.descriptionRes,
                onClick = when (entry) {
                    SettingsEntry.DetectionPermission -> onOpenDetectionSetup
                    SettingsEntry.Rules -> onOpenRules
                    SettingsEntry.Share -> onOpenShareSettings
                    SettingsEntry.SafetyMessage -> onOpenSafetyMessage
                    SettingsEntry.AlertLog -> onOpenAlertLog
                    SettingsEntry.ProvisionLog -> onOpenProvisionLog
                    SettingsEntry.Account -> onOpenAccount
                    // 5단계에서 연결한다.
                    SettingsEntry.Subscription -> null
                },
            )
        }

        HorizontalDivider()
        SectionTitle(stringResource(R.string.settings_legal_title))
        LegalDocument.entries.forEach { document ->
            SettingsRow(
                titleRes = document.titleRes(),
                descriptionRes = null,
                onClick = { onOpenLegalDocument(document) },
            )
        }

        if (showDebugEntries) {
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_debug_title))
            DebugEntry.entries.forEach { entry ->
                SettingsRow(
                    titleRes = entry.titleRes,
                    descriptionRes = entry.descriptionRes,
                    onClick = when (entry) {
                        DebugEntry.TestAlert -> onOpenTestAlert
                        DebugEntry.ObservedPackages -> onOpenObservedPackages
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = descriptionRes?.let {
            {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
    )
}

@StringRes
private fun LegalDocument.titleRes(): Int = when (this) {
    LegalDocument.TermsOfService -> R.string.legal_terms_title
    LegalDocument.PrivacyPolicy -> R.string.legal_privacy_title
    LegalDocument.LocationTerms -> R.string.legal_location_title
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsScreenPreview() {
    AnsimLinkTheme {
        SettingsScreen()
    }
}
