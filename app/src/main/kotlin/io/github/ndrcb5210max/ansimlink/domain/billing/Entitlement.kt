package io.github.ndrcb5210max.ansimlink.domain.billing

import io.github.ndrcb5210max.ansimlink.domain.group.Plan

/**
 * 구독 상태. **서버가 쓰고 앱은 읽기만 한다.**
 *
 * 이름과 뜻은 Play Developer API 의 `SubscriptionState` 를 그대로 옮긴 것이다
 * (`purchases.subscriptionsv2.get` 의 `subscriptionState`). [Revoked] 만 예외로,
 * 그 enum 에는 없고 실시간 개발자 알림(RTDN)의 `SUBSCRIPTION_REVOKED`(12) 와
 * 환불 취소 알림에서 서버가 적는 값이다.
 *
 * ## [grantsPremium] 를 이렇게 정한 근거
 * Play 구독 수명 주기 문서가 상태별로 '접근을 줘야 하는지'를 정해 두었다.
 *
 * | 상태 | 접근 | 이유 |
 * | --- | --- | --- |
 * | [Active] | 준다 | 당연하다. |
 * | [Canceled] | **준다** | 해지를 눌렀어도 이미 낸 기간이 끝날 때까지는 쓸 수 있다. |
 * | [InGracePeriod] | **준다** | 결제 수단 문제를 고칠 시간이다. Play 가 `expiryTime` 을 늘려 준다. |
 * | [OnHold] | 안 준다 | 유예 기간이 지나도 결제가 안 된 상태다. 바로 막으라고 문서가 못박았다. |
 * | [Paused] | 안 준다 | 사용자가 일시정지한 동안은 돈을 내지 않고 쓰지도 못한다. |
 * | [Pending] | 안 준다 | 가입은 했지만 결제가 아직 안 끝났다. |
 * | [Expired] / [Revoked] / [None] / [Unknown] | 안 준다 | |
 *
 * 모르는 값이 오면 [Unknown] 으로 떨어지고 프리미엄을 주지 않는다. 새 상태가 생겼을 때
 * '모르니까 열어 준다'로 동작하면 안 된다.
 */
enum class EntitlementState(val key: String, val grantsPremium: Boolean) {

    /** 구독한 적이 없다. `users/{uid}.entitlement` 가 아직 없을 때의 값이다. */
    None("none", grantsPremium = false),

    Pending("pending", grantsPremium = false),

    Active("active", grantsPremium = true),

    /** 해지했지만 아직 기간이 남았다. */
    Canceled("canceled", grantsPremium = true),

    /** 결제 수단 문제. 고칠 시간을 준다. */
    InGracePeriod("in_grace_period", grantsPremium = true),

    /** 유예 기간이 지났는데도 결제되지 않았다. */
    OnHold("on_hold", grantsPremium = false),

    Paused("paused", grantsPremium = false),

    Expired("expired", grantsPremium = false),

    /** 환불·취소로 기간이 남았는데도 회수됐다. */
    Revoked("revoked", grantsPremium = false),

    /** 서버가 적은 값을 알아보지 못했다. */
    Unknown("unknown", grantsPremium = false),
    ;

    companion object {
        /** 모르는 값은 [Unknown] 이다(프리미엄을 주지 않는다). */
        fun fromKey(key: String?): EntitlementState =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: Unknown
    }
}

/**
 * `users/{uid}.entitlement` 한 건. 서버(`verifySubscription`·RTDN 함수)가 쓴 값이다.
 *
 * **앱은 이 문서만 보고 프리미엄을 연다.** 구매가 끝난 직후의 [com.android.billingclient]
 * 응답으로 열지 않는다(CLAUDE.md 3장: 앱 단독 판단으로 기능을 열지 않는다). 기기에서
 * 만들 수 있는 값으로 기능을 열면, APK 를 고친 사람이 결제 없이 프리미엄을 쓸 수 있다.
 *
 * @param productId 구독 상품 ID. 지금은 [SubscriptionCatalog.PREMIUM_MONTHLY] 하나뿐이다.
 * @param expiryTimeMillis 지금 결제 주기가 끝나는 시각. 서버가 Play 에서 받은 값이다.
 *   [EntitlementState.OnHold] 면 과거, [EntitlementState.InGracePeriod] 면 Play 가
 *   늘려 준 미래 시각이 온다.
 * @param autoRenewing 다음 주기에 자동으로 갱신되는지. 화면에 '해지 예약됨'을 보여 주는 근거다.
 * @param verifiedAtMillis 서버가 마지막으로 Play 에 물어본 시각. 화면에만 쓴다.
 */
data class Entitlement(
    val state: EntitlementState = EntitlementState.None,
    val productId: String? = null,
    val expiryTimeMillis: Long = 0L,
    val autoRenewing: Boolean = false,
    val verifiedAtMillis: Long = 0L,
) {

    /**
     * 이 시각에 적용할 요금제.
     *
     * 세 가지가 **모두** 맞아야 프리미엄이다.
     * 1. 상태가 접근을 주는 상태인지([EntitlementState.grantsPremium]).
     * 2. 우리가 아는 상품인지. 모르는 상품 ID 로 프리미엄이 열리면 안 된다.
     * 3. 아직 기간이 남았는지(`nowMillis < expiryTimeMillis`).
     *
     * 3번을 따로 보는 이유: 서버가 멈추면(배포를 안 했거나, RTDN 이 안 오거나, 함수가
     * 죽어 있으면) 상태 값이 영원히 `active` 로 남는다. 그때 기간만 보고 내려오지 않으면
     * 한 번 결제한 사람이 영원히 프리미엄이 된다. 기간을 같이 보면 **서버가 조용해질 때
     * 저절로 무료로 돌아간다.** 줄이는 방향으로 틀리는 쪽을 골랐다.
     *
     * 대가: 기기 시계를 과거로 돌리면 만료된 뒤에도 기간 검사를 통과한다. 상태 값이
     * `expired` 로 바뀌면(RTDN 13 또는 서버의 재확인) 1번에서 막히므로 영구적이지는 않다.
     * 기기 시계를 믿지 않으려면 신뢰할 수 있는 시간 출처가 필요한데 앱에는 없다.
     * 반대 방향(시계가 앞서 있음)은 프리미엄이 일찍 꺼지는 쪽이라 안전하고 저절로 낫는다.
     */
    fun planAt(nowMillis: Long): Plan {
        val known = productId != null && productId in SubscriptionCatalog.PREMIUM_PRODUCT_IDS
        return if (state.grantsPremium && known && nowMillis < expiryTimeMillis) {
            Plan.Premium
        } else {
            Plan.Free
        }
    }

    /** 해지를 눌러 두어 기간이 끝나면 멈추는 상태인지. 화면에 그렇게 적어 준다. */
    fun endsAfterCurrentPeriod(nowMillis: Long): Boolean =
        planAt(nowMillis) == Plan.Premium && !autoRenewing

    /** 결제가 막혀 조치가 필요한 상태인지(유예 기간·보류). 화면에 안내를 띄운다. */
    val needsPaymentFix: Boolean
        get() = state == EntitlementState.InGracePeriod || state == EntitlementState.OnHold

    companion object {
        /** 구독 기록이 없는 사람. */
        val none = Entitlement()
    }
}
