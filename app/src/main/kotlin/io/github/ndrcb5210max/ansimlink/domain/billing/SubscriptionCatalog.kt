package io.github.ndrcb5210max.ansimlink.domain.billing

/**
 * 구독 상품과 Play 링크.
 *
 * 상품 ID 는 Play Console 의 '구독' 상품 ID 와 글자 하나까지 같아야 한다.
 * 다르면 `queryProductDetailsAsync` 가 가격을 못 받아 오고, 결제 화면은 버튼을 끈 채
 * 이유를 보여 준다([io.github.ndrcb5210max.ansimlink.ui.billing.PaywallUiState]).
 */
object SubscriptionCatalog {

    /** CLAUDE.md 3장에 적힌 상품 ID. */
    const val PREMIUM_MONTHLY = "premium_monthly"

    /**
     * 프리미엄을 주는 상품 목록.
     *
     * 하나뿐인데 집합으로 둔 이유: [Entitlement.planAt] 이 '아는 상품인지'를 확인하는데,
     * 나중에 연간 상품을 더할 때 그 판정을 고칠 자리가 여기 한 곳이어야 한다.
     */
    val PREMIUM_PRODUCT_IDS: Set<String> = setOf(PREMIUM_MONTHLY)
}

/** Google Play 로 보내는 링크. */
object PlayStoreLinks {

    private const val SUBSCRIPTIONS = "https://play.google.com/store/account/subscriptions"

    /**
     * 구독 관리(해지) 화면.
     *
     * 상품과 패키지를 같이 넘기면 그 구독의 상세 화면이 바로 열린다. 넘기지 않으면
     * 구독 목록이 열린다(공식 문서의 권고 형식이다).
     *
     * 왜 앱 안에 해지 화면을 만들지 않나: 해지는 Play 가 처리한다. 앱이 할 수 있는 일은
     * Play 화면을 열어 주는 것뿐이고, 앱 안에 '해지' 버튼만 두고 아무 데도 보내지 않으면
     * 사용자가 해지 방법을 못 찾는다. 그래서 **해지 경로를 결제 화면과 설정 > 구독,
     * 계정 삭제 화면 세 군데에 모두 둔다.**
     *
     * @param productId 구독 상품 ID. 아직 구독한 적이 없으면 null 을 넘긴다.
     * @param packageName 앱의 applicationId.
     */
    fun manageSubscription(productId: String?, packageName: String): String {
        if (productId.isNullOrBlank() || packageName.isBlank()) return SUBSCRIPTIONS
        return SUBSCRIPTIONS + "?sku=" + productId + "&package=" + packageName
    }
}
