package io.github.ndrcb5210max.ansimlink.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/** 설정 화면에 들어갈 항목. 1단계에서는 자리만 만든다. */
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
    ),
}

/**
 * 설정 화면. 각 항목을 누르면 열릴 화면은 3~6단계에서 하나씩 붙인다.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsEntry.entries.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider()
            }
            ListItem(
                headlineContent = { Text(stringResource(entry.titleRes)) },
                supportingContent = {
                    Text(
                        text = stringResource(entry.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    AnsimLinkTheme {
        SettingsScreen()
    }
}
