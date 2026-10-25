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
 * 2단계에서 '계정'과 '약관과 방침'이 눌린다. 나머지는 3~5단계에서 연결한다.
 */
private enum class SettingsEntry(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val enabled: Boolean = false,
) {
    DetectionPermission(
        R.string.settings_detection_permission_title,
        R.string.settings_detection_permission_description,
    ),
    Rules(
        R.string.settings_rules_title,
        R.string.settings_rules_description,
    ),
    SafetyMessage(
        R.string.settings_message_title,
        R.string.settings_message_description,
    ),
    Subscription(
        R.string.settings_subscription_title,
        R.string.settings_subscription_description,
    ),
    Account(
        R.string.settings_account_title,
        R.string.settings_account_description,
        enabled = true,
    ),
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenAccount: () -> Unit = {},
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
                onClick = if (entry == SettingsEntry.Account && entry.enabled) {
                    onOpenAccount
                } else {
                    null
                },
            )
        }

        HorizontalDivider()
        Text(
            text = stringResource(R.string.settings_legal_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        LegalDocument.entries.forEach { document ->
            SettingsRow(
                titleRes = document.titleRes(),
                descriptionRes = null,
                onClick = { onOpenLegalDocument(document) },
            )
        }
    }
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

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun SettingsScreenPreview() {
    AnsimLinkTheme {
        SettingsScreen()
    }
}
