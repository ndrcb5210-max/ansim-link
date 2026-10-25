package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ndrcb5210max.ansimlink.ui.billing.PaywallActions
import io.github.ndrcb5210max.ansimlink.ui.billing.PaywallScreen
import io.github.ndrcb5210max.ansimlink.ui.billing.SubscriptionActions
import io.github.ndrcb5210max.ansimlink.ui.billing.SubscriptionScreen
import io.github.ndrcb5210max.ansimlink.ui.share.ShareSettingsActions
import io.github.ndrcb5210max.ansimlink.ui.share.ShareSettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 5단계의 긴 화면 스크린샷 → docs/screenshots/05/
 *
 * 하단 탭 없이 화면 본문만 찍는다(2단계 '막힌 점' 5번: 기본 높이가 아닌 화면에서
 * Scaffold 를 같이 그리면 하단 탭이 위쪽에 한 번 더 그려진다).
 *
 * 화면 높이는 클래스마다 `@Config` 로 고정하고, 내용 길이가 비슷한 화면끼리 클래스를
 * 나눈다(4단계와 같은 방식).
 *
 * 높이는 **그 클래스에서 가장 긴 그림**에 맞춘다. 처음에 900dp 로 뒀다가 안내 카드가
 * 붙는 그림('서버 확인이 늦은 상태')에서 아래쪽 약관 링크가 잘렸다. 짧은 그림에 여백이
 * 남는 것보다 긴 그림이 잘리는 쪽이 나쁘다 — 잘린 부분이 하필 '구독하면 적용되는 약관'
 * 이면 그림만 보고 '링크가 없네'로 읽는다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h1020dp-xhdpi")
class BillingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `결제 화면 - 무료 체험 있음`() {
        // 구독 정책이 요구하는 것이 한 화면에 다 들어가는 그림이다.
        // 가격 · 결제 주기 · 자동 갱신 · 무료 체험 전환 시점과 금액 · 해지 방법.
        composeTestRule.capture("05/01-paywall-trial.png") {
            PaywallScreen(state = paywallWithTrial, actions = PaywallActions())
        }
    }

    @Test
    fun `결제 화면 - 무료 체험 없음`() {
        composeTestRule.capture("05/02-paywall-monthly.png") {
            PaywallScreen(state = paywallMonthlyOnly, actions = PaywallActions())
        }
    }

    @Test
    fun `결제 화면 - 가격을 못 받아 버튼이 꺼진 상태`() {
        // 이 단계에서 가장 보여 주고 싶은 그림이다. 금액을 모르는 채로 결제 흐름을
        // 띄우지 않고, 끄는 것으로 끝내지 않고 이유를 적는다.
        composeTestRule.capture("05/03-paywall-price-unavailable.png") {
            PaywallScreen(state = paywallPriceUnavailable, actions = PaywallActions())
        }
    }

    @Test
    fun `결제 화면 - 네트워크 오류는 다시 시도할 수 있다`() {
        composeTestRule.capture("05/04-paywall-network-error.png") {
            PaywallScreen(state = paywallNetworkError, actions = PaywallActions())
        }
    }

    @Test
    fun `결제 화면 - 서버 확인이 늦은 상태`() {
        // 결제는 됐지만 entitlement 가 아직 안 왔다. 열어 주지 않고 안내만 한다.
        composeTestRule.capture("05/05-paywall-verification-pending.png") {
            PaywallScreen(state = paywallVerificationPending, actions = PaywallActions())
        }
    }

    @Test
    fun `공유 시간 설정 - 프리미엄`() {
        // 180분 칩이 켜지고 '구독하면 더' 안내가 사라진다.
        composeTestRule.capture("05/10-share-settings-premium.png") {
            ShareSettingsScreen(state = shareSettingsPremium, actions = ShareSettingsActions())
        }
    }
}

/** 5단계의 짧은 화면 스크린샷. 위 클래스와 높이만 다르다. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h820dp-xhdpi")
class SubscriptionScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `설정 - 구독 - 무료 요금제`() {
        composeTestRule.capture("05/06-subscription-free.png") {
            SubscriptionScreen(state = subscriptionFree, actions = SubscriptionActions())
        }
    }

    @Test
    fun `설정 - 구독 - 구독 중`() {
        composeTestRule.capture("05/07-subscription-active.png") {
            SubscriptionScreen(state = subscriptionActive, actions = SubscriptionActions())
        }
    }

    @Test
    fun `설정 - 구독 - 해지 예약됨`() {
        // '다음 결제일'과 '해지 예약됨 · 언제까지'는 전혀 다른 정보다.
        // 같은 문구를 쓰면 해지한 사람이 또 결제되는 줄 안다.
        composeTestRule.capture("05/08-subscription-cancelled.png") {
            SubscriptionScreen(state = subscriptionCancelled, actions = SubscriptionActions())
        }
    }

    @Test
    fun `설정 - 구독 - 결제 보류`() {
        // 프리미엄이 꺼진 상태다. 유예 기간과 문구가 달라야 한다.
        composeTestRule.capture("05/09-subscription-on-hold.png") {
            SubscriptionScreen(state = subscriptionOnHold, actions = SubscriptionActions())
        }
    }

    @Test
    fun `설정 - 구독 - 유예 기간`() {
        // 프리미엄은 아직 켜져 있고 결제 수단을 고칠 시간이 있다.
        composeTestRule.capture("05/11-subscription-grace-period.png") {
            SubscriptionScreen(state = subscriptionGracePeriod, actions = SubscriptionActions())
        }
    }
}
