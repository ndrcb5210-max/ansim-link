package io.github.ndrcb5210max.ansimlink.ui.billing

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
import androidx.compose.material3.HorizontalDivider
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
import io.github.ndrcb5210max.ansimlink.domain.billing.EntitlementState
import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/** 설정 > 구독 화면의 콜백 묶음. */
data class SubscriptionActions(
    val onOpenPaywall: () -> Unit = {},
    val onOpenManageSubscription: () -> Unit = {},
    val onRecheck: () -> Unit = {},
    val onDismissNotice: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * 설정 > 구독.
 *
 * 지금 상태·갱신일·해지 경로를 보여 준다. 결제 화면과 나눈 이유: 이 화면은 **이미
 * 구독 중인 사람이 확인하러 오는 곳**이다. 결제 화면(비교표·가격·결제 버튼)을
 * 여기에 그대로 두면 구독 중인 사람에게도 영업 문구가 계속 보인다.
 *
 * 결제하지 않은 사람에게는 '프리미엄 보기'로 결제 화면으로 가는 길을 둔다.
 */
@Composable
fun SubscriptionScreen(
    state: PaywallUiState,
    actions: SubscriptionActions,
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
            text = stringResource(R.string.settings_subscription_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        state.notice?.let { notice ->
            NoticeCard(notice = notice, onDismiss = actions.onDismissNotice)
        }

        StatusCard(state = state)

        // 결제가 막힌 상태(유예 기간·보류)면 무엇을 해야 하는지 적는다.
        // 이 두 상태는 Play 에서 결제 수단을 고쳐야 풀린다 — 앱이 할 수 있는 일이 없다.
        if (state.entitlement.needsPaymentFix) {
            PaymentFixCard(
                onManage = actions.onOpenManageSubscription,
                onHold = state.entitlement.state == EntitlementState.OnHold,
            )
        }

        HorizontalDivider()

        CurrentLimits(plan = state.plan)

        HorizontalDivider()

        if (state.premium) {
            Text(
                text = stringResource(R.string.subscription_manage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = actions.onOpenManageSubscription,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.billing_manage))
            }
        } else {
            Button(onClick = actions.onOpenPaywall, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.subscription_see_premium))
            }
            // 구독한 적이 있는데 상태가 안 보이는 경우를 위한 길. 환불·기기 교체 뒤
            // Play 에는 구독이 살아 있는데 우리 서버 기록이 비어 있을 수 있다.
            OutlinedButton(onClick = actions.onRecheck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.subscription_recheck))
            }
        }

        Text(
            text = stringResource(R.string.subscription_delete_account_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 지금 상태 요약. */
@Composable
private fun StatusCard(state: PaywallUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (state.premium) {
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
                text = stringResource(
                    if (state.premium) {
                        R.string.billing_current_premium
                    } else {
                        R.string.billing_current_free
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            SubscriptionStatusLines(state = state)
        }
    }
}

/**
 * 상태를 줄 단위로 적는다. 결제 화면과 설정 > 구독이 같이 쓴다.
 *
 * 적는 것: 상태 설명, 갱신일(또는 종료 예정일), 서버가 마지막으로 확인한 시각.
 *
 * '서버가 확인한 시각'을 보여 주는 이유: 이 값이 오래됐으면 서버가 멈춘 것이다.
 * 사용자가 '프리미엄인데 왜 180분이 안 되지'를 물을 때 이 날짜가 단서가 된다.
 */
@Composable
internal fun SubscriptionStatusLines(state: PaywallUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(state.entitlement.state.descriptionRes()),
            style = MaterialTheme.typography.bodyMedium,
        )

        state.renewalDateText?.let { dateText ->
            Text(
                text = stringResource(
                    // 자동 갱신이 켜져 있으면 '다음 결제일', 해지해 두었으면 '종료 예정일'.
                    // 두 경우에 같은 문구를 쓰면 해지한 사람이 또 결제되는 줄 안다.
                    if (state.entitlement.autoRenewing) {
                        R.string.billing_next_renewal
                    } else {
                        R.string.billing_ends_at
                    },
                    dateText,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        state.verifiedAtText?.let { dateText ->
            Text(
                text = stringResource(R.string.billing_verified_at, dateText),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** 지금 요금제의 한도. 숫자는 [PlanLimits] 에서 가져온다. */
@Composable
private fun CurrentLimits(plan: Plan, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.subscription_current_limits_title),
            style = MaterialTheme.typography.titleMedium,
        )
        listOf(
            stringResource(R.string.paywall_feature_groups) + ": " +
                stringResource(R.string.paywall_count_groups, PlanLimits.maxGroups(plan)),
            stringResource(R.string.paywall_feature_members) + ": " +
                stringResource(R.string.paywall_count_members, PlanLimits.maxMembers(plan)),
            stringResource(R.string.paywall_feature_duration) + ": " +
                stringResource(R.string.paywall_count_minutes, PlanLimits.maxShareMinutes(plan)),
            stringResource(R.string.paywall_feature_messages) + ": " +
                stringResource(
                    if (plan == Plan.Premium) {
                        R.string.paywall_messages_premium
                    } else {
                        R.string.paywall_messages_free
                    },
                ),
        ).forEach { line ->
            Text(text = "· $line", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** 결제가 막혔을 때의 안내. */
@Composable
private fun PaymentFixCard(
    onManage: () -> Unit,
    onHold: Boolean,
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
                text = stringResource(R.string.billing_payment_fix_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    // 보류는 이미 프리미엄이 꺼진 상태다. 유예 기간은 아직 켜져 있다.
                    // 같은 문구를 쓰면 '지금 쓸 수 있나'를 알 수 없다.
                    if (onHold) {
                        R.string.billing_payment_fix_on_hold
                    } else {
                        R.string.billing_payment_fix_grace
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.billing_manage))
            }
        }
    }
}

/** 상태 설명 문구. */
private fun EntitlementState.descriptionRes(): Int = when (this) {
    EntitlementState.None -> R.string.billing_state_none
    EntitlementState.Pending -> R.string.billing_state_pending
    EntitlementState.Active -> R.string.billing_state_active
    EntitlementState.Canceled -> R.string.billing_state_canceled
    EntitlementState.InGracePeriod -> R.string.billing_state_grace
    EntitlementState.OnHold -> R.string.billing_state_on_hold
    EntitlementState.Paused -> R.string.billing_state_paused
    EntitlementState.Expired -> R.string.billing_state_expired
    EntitlementState.Revoked -> R.string.billing_state_revoked
    EntitlementState.Unknown -> R.string.billing_state_unknown
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun SubscriptionScreenPreview() {
    AnsimLinkTheme {
        SubscriptionScreen(
            state = PaywallUiState(loading = false),
            actions = SubscriptionActions(),
        )
    }
}
