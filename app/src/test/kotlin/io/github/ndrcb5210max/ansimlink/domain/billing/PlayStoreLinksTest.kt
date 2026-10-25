package io.github.ndrcb5210max.ansimlink.domain.billing

import org.junit.Assert.assertEquals
import org.junit.Test

/** 구독 관리(해지) 링크. 앱 안 어디서 눌러도 같은 곳으로 가야 한다. */
class PlayStoreLinksTest {

    private val packageName = "io.github.ndrcb5210max.ansimlink"

    @Test
    fun `상품과 패키지를 넘기면 그 구독 화면으로 보낸다`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions" +
                "?sku=premium_monthly&package=io.github.ndrcb5210max.ansimlink",
            PlayStoreLinks.manageSubscription(SubscriptionCatalog.PREMIUM_MONTHLY, packageName),
        )
    }

    @Test
    fun `구독한 적이 없으면 구독 목록으로 보낸다`() {
        // 계정 삭제 화면에서는 구독 상태를 모르는 채로 링크를 띄울 수 있다.
        val expected = "https://play.google.com/store/account/subscriptions"

        assertEquals(expected, PlayStoreLinks.manageSubscription(null, packageName))
        assertEquals(expected, PlayStoreLinks.manageSubscription("", packageName))
        assertEquals(expected, PlayStoreLinks.manageSubscription("  ", packageName))
        assertEquals(expected, PlayStoreLinks.manageSubscription(SubscriptionCatalog.PREMIUM_MONTHLY, ""))
    }
}
