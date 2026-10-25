package io.github.ndrcb5210max.ansimlink.ui.billing

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.data.billing.BillingUnavailable
import io.github.ndrcb5210max.ansimlink.domain.billing.BillingPeriod
import io.github.ndrcb5210max.ansimlink.domain.billing.BillingPeriodUnit
import io.github.ndrcb5210max.ansimlink.domain.billing.OfferStep

// 결제 화면의 문구를 만든다.
//
// 금액을 직접 만들지 않는다. Play 가 준 OfferStep.formattedPrice 를 **그대로** 쓴다.
// 통화 기호 위치, 자리수 구분, 소수점 유무가 나라마다 다르고, 화면에 적은 금액과
// 실제 청구 금액이 어긋나면 그대로 정책 위반이다.
//
// 주기는 못 읽으면 원문을 보여 준다. BillingPeriod.known 이 false 면 Play 가 준
// ISO 8601 문자열을 그대로 적는다. 억지로 '매월'로 바꿔 적는 것이 가장 나쁘다 —
// 사용자가 매달 빠져나갈 돈을 그 문구로 판단한다.

/** 결제 주기를 '매월'·'3개월마다' 처럼 적는다. */
@Composable
fun BillingPeriod.recurrenceText(): String {
    // 못 읽은 형식은 Play 가 준 문자열을 그대로 보여 준다(위 메모 참고).
    val resolved = unit.takeIf { known } ?: return raw
    if (count == 1) return stringResource(resolved.everyOneRes())
    return stringResource(
        R.string.billing_period_every_n,
        count,
        stringResource(resolved.unitNameRes()),
    )
}

/** 기간을 '7일'·'1개월' 처럼 적는다(무료 체험 길이 등). */
@Composable
fun BillingPeriod.lengthText(): String {
    val resolved = unit.takeIf { known } ?: return raw
    return stringResource(
        R.string.billing_period_length,
        count,
        stringResource(resolved.unitNameRes()),
    )
}

@StringRes
private fun BillingPeriodUnit.unitNameRes(): Int = when (this) {
    BillingPeriodUnit.Day -> R.string.billing_period_unit_day
    BillingPeriodUnit.Week -> R.string.billing_period_unit_week
    BillingPeriodUnit.Month -> R.string.billing_period_unit_month
    BillingPeriodUnit.Year -> R.string.billing_period_unit_year
}

@StringRes
private fun BillingPeriodUnit.everyOneRes(): Int = when (this) {
    BillingPeriodUnit.Day -> R.string.billing_period_every_day
    BillingPeriodUnit.Week -> R.string.billing_period_every_week
    BillingPeriodUnit.Month -> R.string.billing_period_every_month
    BillingPeriodUnit.Year -> R.string.billing_period_every_year
}

/**
 * 가격 단계 한 줄을 적는다.
 *
 * 되풀이되는 단계는 "매월 ₩2,500", 무료 체험은 "7일 무료",
 * 소개 가격은 "첫 2개월 ₩1,000" 처럼 적는다.
 */
@Composable
fun OfferStep.lineText(): String = when {
    repeating -> stringResource(
        R.string.billing_step_repeating,
        period.recurrenceText(),
        formattedPrice,
    )

    free -> stringResource(R.string.billing_step_free_trial, period.lengthText())

    cycles > 1 -> stringResource(
        R.string.billing_step_intro_cycles,
        cycles,
        period.lengthText(),
        formattedPrice,
    )

    else -> stringResource(R.string.billing_step_intro, period.lengthText(), formattedPrice)
}

/**
 * 가격을 못 받은 이유를 사람 말로 적는다.
 *
 * 응답 코드를 그대로 보여 주지 않는다. `BILLING_UNAVAILABLE(3)` 을 적어 줘도 사용자가
 * 할 수 있는 일이 없다. **무엇을 해 볼 수 있는지**까지 문구에 담는다.
 */
@StringRes
fun BillingUnavailable.messageRes(): Int = when (this) {
    BillingUnavailable.NetworkError -> R.string.billing_unavailable_network
    BillingUnavailable.PlayStoreUnavailable -> R.string.billing_unavailable_play_store
    BillingUnavailable.SubscriptionsNotSupported -> R.string.billing_unavailable_subscriptions
    BillingUnavailable.ProductNotFound -> R.string.billing_unavailable_product
    BillingUnavailable.ServerNotConfigured -> R.string.billing_unavailable_server
    BillingUnavailable.Unknown -> R.string.billing_unavailable_unknown
}

/** 한 번 띄우는 안내의 문구. */
@StringRes
fun PaywallNotice.messageRes(): Int = when (this) {
    PaywallNotice.PremiumActivated -> R.string.billing_notice_activated
    PaywallNotice.VerificationPending -> R.string.billing_notice_verification_pending
    PaywallNotice.PurchasePending -> R.string.billing_notice_purchase_pending
    PaywallNotice.PurchaseCancelled -> R.string.billing_notice_cancelled
    PaywallNotice.PurchaseRejected -> R.string.billing_notice_rejected
    PaywallNotice.PurchaseFailed -> R.string.billing_notice_failed
    PaywallNotice.PurchaseRestored -> R.string.billing_notice_restored
}

/**
 * 안내를 오류 색으로 보여 줄지.
 *
 * 취소와 '진행 중'은 오류가 아니다. 빨간 글씨로 적으면 아무 문제가 없는데도 겁을 준다.
 */
val PaywallNotice.isError: Boolean
    get() = this == PaywallNotice.PurchaseRejected || this == PaywallNotice.PurchaseFailed
