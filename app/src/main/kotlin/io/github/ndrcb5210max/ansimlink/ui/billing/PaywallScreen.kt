package io.github.ndrcb5210max.ansimlink.ui.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.billing.OfferPresentation
import io.github.ndrcb5210max.ansimlink.domain.consent.LegalDocument
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits
import io.github.ndrcb5210max.ansimlink.ui.common.ServerNotConfiguredCard
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/** 결제 화면의 콜백 묶음. */
data class PaywallActions(
    val onSubscribe: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRecheck: () -> Unit = {},
    val onOpenManageSubscription: () -> Unit = {},
    val onOpenLegalDocument: (LegalDocument) -> Unit = {},
    val onDismissNotice: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * 결제 화면(paywall).
 *
 * ## 반드시 보여 주는 것
 * Google Play 구독 정책이 요구하는 것을 한 화면에 모았다.
 * 1. **가격** — Play 에서 받은 `formattedPrice` 를 그대로. 앱이 금액을 만들지 않는다.
 * 2. **결제 주기** — '매월'.
 * 3. **자동 갱신** — '매월 자동 갱신'이라고 분명히 적는다.
 * 4. **무료 체험이 있으면 전환 시점과 금액** — '7일 무료, 그 뒤부터 매월 ₩2,500'.
 * 5. **해지 방법** — [구독 관리]로 Google Play 구독 화면을 연다.
 * 6. 이용약관·개인정보 처리방침 링크.
 *
 * ## 가격을 못 받으면 버튼을 끈다
 * 금액을 모르는 채로 결제 흐름을 띄우면 사용자가 얼마를 내는지 모르고 누르게 된다.
 * 버튼을 끄고 **이유와 할 수 있는 일**을 적는다([BillingUnavailable][io.github.ndrcb5210max.ansimlink.data.billing.BillingUnavailable]).
 *
 * ## 비교표에서 무료를 깎지 않는다
 * 무료로 할 수 있는 것을 먼저 적고 프리미엄을 그 옆에 둔다. 무료가 모자라 보이게
 * 만드는 문구('겨우 3명')를 쓰지 않는다 — 이 앱은 재난 상황에 쓰는 앱이고,
 * 돈을 내지 않은 사람도 가족 3명과 60분 공유는 되어야 한다.
 */
@Composable
fun PaywallScreen(
    state: PaywallUiState,
    actions: PaywallActions,
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
            text = stringResource(R.string.paywall_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.paywall_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!state.serverReady) ServerNotConfiguredCard()

        state.notice?.let { notice ->
            NoticeCard(notice = notice, onDismiss = actions.onDismissNotice)
        }

        if (state.premium) {
            CurrentSubscriptionCard(state = state, onManage = actions.onOpenManageSubscription)
        }

        PlanComparison()

        HorizontalDivider()

        PriceSection(state = state, actions = actions)

        HorizontalDivider()

        LegalSection(onOpenLegalDocument = actions.onOpenLegalDocument)
    }
}

/**
 * 무료 / 프리미엄 비교표.
 *
 * 숫자는 [PlanLimits] 에서 가져온다. 화면에 직접 적으면 한도를 고칠 때 화면 문구가
 * 남는다 — 그러면 '화면에는 10명이라는데 9명에서 막힌다'가 된다.
 */
@Composable
private fun PlanComparison(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderCell(stringResource(R.string.paywall_table_feature), weight = 1.4f)
                HeaderCell(stringResource(R.string.paywall_table_free), weight = 1f)
                HeaderCell(stringResource(R.string.paywall_table_premium), weight = 1f)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            ComparisonRow(
                label = stringResource(R.string.paywall_feature_groups),
                free = stringResource(R.string.paywall_count_groups, PlanLimits.maxGroups(Plan.Free)),
                premium = stringResource(
                    R.string.paywall_count_groups,
                    PlanLimits.maxGroups(Plan.Premium),
                ),
            )
            ComparisonRow(
                label = stringResource(R.string.paywall_feature_members),
                free = stringResource(
                    R.string.paywall_count_members,
                    PlanLimits.maxMembers(Plan.Free),
                ),
                premium = stringResource(
                    R.string.paywall_count_members,
                    PlanLimits.maxMembers(Plan.Premium),
                ),
            )
            ComparisonRow(
                label = stringResource(R.string.paywall_feature_duration),
                free = stringResource(
                    R.string.paywall_count_minutes,
                    PlanLimits.maxShareMinutes(Plan.Free),
                ),
                premium = stringResource(
                    R.string.paywall_count_minutes,
                    PlanLimits.maxShareMinutes(Plan.Premium),
                ),
            )
            ComparisonRow(
                label = stringResource(R.string.paywall_feature_messages),
                free = stringResource(R.string.paywall_messages_free),
                premium = stringResource(R.string.paywall_messages_premium),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                // 재난문자 감지와 위치 공유 자체는 무료에서도 똑같이 된다.
                // 프리미엄이 '안전을 더 사는 것'처럼 읽히면 안 된다.
                text = stringResource(R.string.paywall_table_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun ComparisonRow(
    label: String,
    free: String,
    premium: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.4f),
        )
        Text(
            text = free,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = premium,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 가격과 결제 버튼. 가격을 못 받으면 버튼을 끄고 이유를 적는다. */
@Composable
private fun PriceSection(
    state: PaywallUiState,
    actions: PaywallActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.loading -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            state.priceMissing -> PriceUnavailableCard(state = state, onRetry = actions.onRetry)

            state.offer != null -> OfferDetails(offer = state.offer)
        }

        if (state.premium) {
            // 이미 구독 중이면 결제 버튼을 두지 않는다. 눌러도 ITEM_ALREADY_OWNED 가 나고,
            // 그 전에 '또 결제되나?' 하고 불안하게 만든다.
            OutlinedButton(
                onClick = actions.onOpenManageSubscription,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.billing_manage))
            }
        } else {
            Button(
                onClick = actions.onSubscribe,
                enabled = state.canSubscribe,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.offer?.freeTrial != null) {
                            R.string.paywall_subscribe_with_trial
                        } else {
                            R.string.paywall_subscribe
                        },
                    ),
                )
            }

            if (state.purchaseInProgress || state.verifying) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(
                            if (state.verifying) {
                                R.string.billing_verifying
                            } else {
                                R.string.billing_purchase_in_progress
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 해지 방법은 **결제하기 전에** 보여 준다. 결제한 뒤에만 보여 주면
            // '어떻게 그만두는지 모르고 결제하는' 상태가 된다.
            Text(
                text = stringResource(R.string.paywall_auto_renew_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = actions.onOpenManageSubscription) {
                Text(stringResource(R.string.billing_manage))
            }
        }
    }
}

/** 가격 단계를 위에서 아래로 적는다. 마지막 줄이 계속 빠져나가는 금액이다. */
@Composable
private fun OfferDetails(offer: OfferPresentation, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            offer.renewal?.let { renewal ->
                Text(
                    text = renewal.lineText(),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            // 무료 체험·소개 가격이 있으면 '언제부터 얼마'를 같이 적는다.
            offer.beforeRenewal.forEach { step ->
                Text(text = step.lineText(), style = MaterialTheme.typography.bodyMedium)
            }

            offer.freeTrial?.let { trial ->
                Text(
                    text = stringResource(
                        R.string.paywall_trial_conversion,
                        trial.period.lengthText(),
                        offer.renewal?.formattedPrice.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = stringResource(R.string.paywall_price_source),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 가격을 못 받았을 때. 이유와 할 수 있는 일을 적는다. */
@Composable
private fun PriceUnavailableCard(
    state: PaywallUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.billing_price_unavailable_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    state.unavailable?.messageRes() ?: R.string.billing_unavailable_unknown,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.retryable) {
                OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.billing_retry))
                }
            }
        }
    }
}

/** 지금 구독 중일 때의 요약. 설정 > 구독과 같은 내용을 보여 준다. */
@Composable
private fun CurrentSubscriptionCard(
    state: PaywallUiState,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.billing_current_premium),
                style = MaterialTheme.typography.titleMedium,
            )
            SubscriptionStatusLines(state = state)
            OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.billing_manage))
            }
        }
    }
}

@Composable
private fun LegalSection(
    onOpenLegalDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.paywall_legal_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(onClick = { onOpenLegalDocument(LegalDocument.TermsOfService) }) {
                Text(stringResource(R.string.legal_terms_title))
            }
            TextButton(onClick = { onOpenLegalDocument(LegalDocument.PrivacyPolicy) }) {
                Text(stringResource(R.string.legal_privacy_title))
            }
        }
    }
}

/** 한 번 띄우는 안내. 취소는 오류 색을 쓰지 않는다. */
@Composable
internal fun NoticeCard(
    notice: PaywallNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (notice.isError) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
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
                text = stringResource(notice.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_confirm))
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun PaywallScreenPreview() {
    AnsimLinkTheme {
        PaywallScreen(state = PaywallUiState(loading = false), actions = PaywallActions())
    }
}
