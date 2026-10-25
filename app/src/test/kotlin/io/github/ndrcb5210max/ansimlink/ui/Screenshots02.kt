package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.ndrcb5210max.ansimlink.data.model.AnsimGroup
import io.github.ndrcb5210max.ansimlink.data.model.GroupMember
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.data.model.SignedInUser
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsent
import io.github.ndrcb5210max.ansimlink.domain.consent.SignUpConsentState
import io.github.ndrcb5210max.ansimlink.ui.theme.AnsimLinkTheme

/**
 * 2단계 스크린샷 테스트가 같이 쓰는 것들.
 *
 * 화면 높이는 클래스마다 `@Config(qualifiers = ...)` 로 고정한다.
 * 테스트 안에서 `RuntimeEnvironment.setQualifiers` 로 중간에 바꾸면
 * 이미 배치된 내용이 남아 하단 탭이 두 번 그려지는 등 결과가 깨진다.
 */
internal val allSignUpConsents = SignUpConsentState(accepted = SignUpConsent.entries.toSet())

internal val sampleUser = SignedInUser(
    uid = "me",
    email = "hong@example.com",
    displayName = "민수",
    emailVerified = false,
    signedInWithGoogle = false,
)

/** 동의함·일시중지·동의 철회가 한 화면에 다 보이도록 만든 예시 그룹. */
internal val sampleGroup = GroupWithMembers(
    group = AnsimGroup("g1", "우리 가족", "me", listOf("me", "a", "b")),
    members = listOf(
        GroupMember("me", "민수", locationConsent = true, paused = false, "2026-10-24", 0),
        GroupMember("a", "지은", locationConsent = true, paused = true, "2026-10-24", 0),
        GroupMember("b", "하늘", locationConsent = false, paused = false, "2026-10-24", 0),
    ),
)

/** 화면 하나를 통째로 찍는다. */
internal fun ComposeContentTestRule.capture(fileName: String, content: @Composable () -> Unit) {
    setContent {
        AnsimLinkTheme(darkTheme = false) { content() }
    }
    onRoot().captureRoboImage(fileName)
}

// 하단 탭이 같이 보이는 화면은 1단계 스크린샷(ScreenshotTest)에서 기본 화면 크기로 찍는다.
// 기본 크기가 아닌 화면에서 Scaffold 를 그리면 하단 탭이 위쪽에 한 번 더 그려진다.
