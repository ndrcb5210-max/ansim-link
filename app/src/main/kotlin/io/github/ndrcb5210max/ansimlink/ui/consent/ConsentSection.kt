package io.github.ndrcb5210max.ansimlink.ui.consent

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.consent.LegalDocument
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsent
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsentState

@StringRes
private fun SignUpConsent.labelRes(): Int = when (this) {
    SignUpConsent.AgeOver14 -> R.string.consent_age_over_14
    SignUpConsent.TermsOfService -> R.string.consent_terms_of_service
    SignUpConsent.PrivacyPolicy -> R.string.consent_privacy_policy
    SignUpConsent.LocationTerms -> R.string.consent_location_terms
}

/**
 * 가입 동의 묶음.
 *
 * '전체 동의'가 있어도 항목별 체크를 항상 같이 보여 준다(CLAUDE.md 원칙 8).
 * 체크박스를 직접 눌러야만 동의가 되고, 화면을 닫거나 뒤로 가는 것은 동의가 아니다.
 */
@Composable
fun ConsentSection(
    state: SignUpConsentState,
    onToggle: (SignUpConsent) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onOpenDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.consent_section_title),
            style = MaterialTheme.typography.titleMedium,
        )

        ConsentRow(
            checked = state.allAccepted,
            label = stringResource(R.string.consent_accept_all),
            emphasize = true,
            onCheckedChange = onToggleAll,
        )
        HorizontalDivider()

        SignUpConsent.entries.forEach { item ->
            ConsentRow(
                checked = state.isAccepted(item),
                label = stringResource(R.string.consent_required_mark) + " " +
                    stringResource(item.labelRes()),
                onCheckedChange = { onToggle(item) },
                trailing = item.document?.let { document ->
                    {
                        TextButton(onClick = { onOpenDocument(document) }) {
                            Text(stringResource(R.string.consent_view_document))
                        }
                    }
                },
            )
        }

        if (!state.allAccepted) {
            Text(
                text = stringResource(R.string.consent_missing_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 체크박스뿐 아니라 줄 전체를 눌러도 되게 한다. 뒤로 가기로는 절대 바뀌지 않는다.
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = if (emphasize) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodyMedium
            },
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        trailing?.invoke()
    }
}
