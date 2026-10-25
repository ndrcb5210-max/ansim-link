package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ndrcb5210max.ansimlink.ui.message.SafetyMessageScreen
import io.github.ndrcb5210max.ansimlink.ui.share.FamilyLocationScreen
import io.github.ndrcb5210max.ansimlink.ui.share.LocationDisclosureScreen
import io.github.ndrcb5210max.ansimlink.ui.share.ProvisionLogScreen
import io.github.ndrcb5210max.ansimlink.ui.share.ShareSettingsActions
import io.github.ndrcb5210max.ansimlink.ui.share.ShareSettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 4단계의 긴 화면 스크린샷 → docs/screenshots/04/
 *
 * 하단 탭 없이 화면 본문만 찍는다. 기본 높이가 아닌 화면에서 Scaffold 를 같이 그리면
 * 하단 탭이 위쪽에 한 번 더 그려지기 때문이다(2단계 '막힌 점' 5번).
 * 하단 탭이 같이 보이는 그림은 [ShareTabScreenshotTest] 가 기본 크기로 찍는다.
 *
 * 화면 높이는 클래스마다 `@Config` 로 고정한다. 테스트 안에서 바꾸면 결과가 깨지므로
 * 내용 길이가 비슷한 화면끼리 클래스를 나눈다.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h1500dp-xhdpi")
class ShareScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `위치 권한 설명 - 요청 전`() {
        composeTestRule.capture("04/01-location-disclosure.png") {
            LocationDisclosureScreen(granted = false, onRequestPermission = {}, onBack = {})
        }
    }

    @Test
    fun `위치 공유 설정 - 자동 시작 명시적 공개`() {
        // 자동 시작을 켤 때 가장 먼저 보이는 화면이다. Google Play 배경 위치 정책이
        // 요구하는 세 가지(앱이 닫혀 있을 때도 수집 / 쓰는 기능 목록 / 다른 데 안 씀)를
        // 한 그림에 담으려고 이 상태를 골랐다.
        composeTestRule.capture("04/02-share-settings-disclosure.png") {
            ShareSettingsScreen(
                state = shareSettingsBeforeAutoStart,
                actions = ShareSettingsActions(),
            )
        }
    }

    @Test
    fun `안심 메시지 - 무료 요금제`() {
        composeTestRule.capture("04/05-safety-message.png") {
            SafetyMessageScreen(
                state = sampleMessages,
                onCommonChange = {},
                onTypeChange = { _, _ -> },
                onReset = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `안심 메시지 - 프리미엄 유형별`() {
        composeTestRule.capture("04/06-safety-message-premium.png") {
            SafetyMessageScreen(
                state = samplePremiumMessages,
                onCommonChange = {},
                onTypeChange = { _, _ -> },
                onReset = {},
                onBack = {},
            )
        }
    }
}

/** 4단계의 짧은 화면 스크린샷. 위 클래스와 높이만 다르다. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h1150dp-xhdpi")
class ShareShortScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `위치 공유 설정 - 배경 위치 권한 단계`() {
        composeTestRule.capture("04/03-share-settings-background.png") {
            ShareSettingsScreen(
                state = shareSettingsBackgroundStep,
                actions = ShareSettingsActions(),
            )
        }
    }

    @Test
    fun `가족 위치 보기`() {
        composeTestRule.capture("04/04-family-location.png") {
            FamilyLocationScreen(
                state = sampleFamilyLocations,
                onSelectGroup = {},
                onOpenMap = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `내 위치 제공 기록`() {
        composeTestRule.capture("04/07-provision-log.png") {
            ProvisionLogScreen(state = sampleProvisionLog, onBack = {})
        }
    }

    @Test
    fun `가족 위치 보기 - 공유 중인 사람이 없을 때`() {
        composeTestRule.capture("04/08-family-location-empty.png") {
            FamilyLocationScreen(
                state = io.github.ndrcb5210max.ansimlink.ui.share.FamilyLocationUiState(
                    loading = false,
                ),
                onSelectGroup = {},
                onOpenMap = {},
                onBack = {},
            )
        }
    }
}
