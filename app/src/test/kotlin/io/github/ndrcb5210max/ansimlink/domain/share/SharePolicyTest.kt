package io.github.ndrcb5210max.ansimlink.domain.share

import io.github.ndrcb5210max.ansimlink.domain.group.ShareState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공유 시작 판정.
 *
 * 여기서 제일 중요한 것은 **동의를 철회했거나 일시중지 중이면 시작하지 않는다**는 것이다
 * (CLAUDE.md 원칙 1). 그 다음이 권한이다.
 */
class SharePolicyTest {

    private fun target(
        state: ShareState,
        groupId: String = "g1",
        receiverCount: Int = 2,
    ) = ShareTarget(
        groupId = groupId,
        groupName = "우리 가족",
        shareState = state,
        receiverCount = receiverCount,
    )

    private fun decide(
        targets: List<ShareTarget>,
        signedIn: Boolean = true,
        locationPermissionGranted: Boolean = true,
        alreadySharing: Boolean = false,
        autoStart: Boolean = false,
        backgroundLocationGranted: Boolean = false,
    ) = SharePolicy.decide(
        targets = targets,
        signedIn = signedIn,
        locationPermissionGranted = locationPermissionGranted,
        alreadySharing = alreadySharing,
        autoStart = autoStart,
        backgroundLocationGranted = backgroundLocationGranted,
    )

    // ---- 통과하는 경우 ----

    @Test
    fun `동의했고 권한이 있으면 시작한다`() {
        val decision = decide(listOf(target(ShareState.Consented)))

        assertTrue(decision.allowed)
    }

    @Test
    fun `여러 그룹 중 하나만 동의 상태여도 시작한다`() {
        val decision = decide(
            listOf(
                target(ShareState.Paused, groupId = "g1"),
                target(ShareState.Consented, groupId = "g2"),
            ),
        )

        assertTrue(decision.allowed)
    }

    // ---- 동의와 일시중지 ----

    @Test
    fun `동의를 철회하면 시작하지 않는다`() {
        val decision = decide(listOf(target(ShareState.Withdrawn)))

        assertEquals(ShareBlockReason.ConsentWithdrawn, decision.blockReason)
    }

    @Test
    fun `일시중지 중이면 시작하지 않는다`() {
        val decision = decide(listOf(target(ShareState.Paused)))

        assertEquals(ShareBlockReason.Paused, decision.blockReason)
    }

    @Test
    fun `일시중지와 철회가 섞여 있으면 일시중지로 알려 준다`() {
        // 사용자가 할 일이 다르다. 일시중지는 '다시 공유'를 누르면 되고,
        // 철회는 초대 코드를 새로 받아야 한다.
        val decision = decide(
            listOf(
                target(ShareState.Withdrawn, groupId = "g1"),
                target(ShareState.Paused, groupId = "g2"),
            ),
        )

        assertEquals(ShareBlockReason.Paused, decision.blockReason)
    }

    @Test
    fun `동의 검사는 위치 권한 검사보다 먼저다`() {
        // 동의를 철회한 사람에게 위치 권한 요청 화면을 띄우면 안 된다.
        val decision = decide(
            listOf(target(ShareState.Withdrawn)),
            locationPermissionGranted = false,
        )

        assertEquals(ShareBlockReason.ConsentWithdrawn, decision.blockReason)
    }

    // ---- 그 밖의 조건 ----

    @Test
    fun `로그인하지 않으면 시작하지 않는다`() {
        val decision = decide(listOf(target(ShareState.Consented)), signedIn = false)

        assertEquals(ShareBlockReason.NotSignedIn, decision.blockReason)
    }

    @Test
    fun `그룹이 없으면 시작하지 않는다`() {
        val decision = decide(emptyList())

        assertEquals(ShareBlockReason.NoGroup, decision.blockReason)
    }

    @Test
    fun `위치 권한이 없으면 시작하지 않는다`() {
        val decision = decide(
            listOf(target(ShareState.Consented)),
            locationPermissionGranted = false,
        )

        assertEquals(ShareBlockReason.LocationPermissionMissing, decision.blockReason)
    }

    @Test
    fun `이미 공유 중이면 새로 시작하지 않는다`() {
        val decision = decide(listOf(target(ShareState.Consented)), alreadySharing = true)

        assertEquals(ShareBlockReason.AlreadySharing, decision.blockReason)
    }

    // ---- 자동 시작 ----

    @Test
    fun `자동 시작은 배경 위치 권한이 있어야 한다`() {
        val decision = decide(
            listOf(target(ShareState.Consented)),
            autoStart = true,
            backgroundLocationGranted = false,
        )

        assertEquals(ShareBlockReason.BackgroundLocationMissing, decision.blockReason)
    }

    @Test
    fun `자동 시작에 배경 위치 권한이 있으면 시작한다`() {
        val decision = decide(
            listOf(target(ShareState.Consented)),
            autoStart = true,
            backgroundLocationGranted = true,
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun `기본 모드는 배경 위치 권한이 없어도 시작한다`() {
        val decision = decide(
            listOf(target(ShareState.Consented)),
            autoStart = false,
            backgroundLocationGranted = false,
        )

        assertTrue(decision.allowed)
    }

    // ---- 실제로 보낼 그룹 고르기 ----

    @Test
    fun `동의하지 않거나 일시중지한 그룹에는 보내지 않는다`() {
        val targets = listOf(
            target(ShareState.Consented, groupId = "g1"),
            target(ShareState.Paused, groupId = "g2"),
            target(ShareState.Withdrawn, groupId = "g3"),
        )

        val sharing = SharePolicy.sharingTargets(targets)

        assertEquals(listOf("g1"), sharing.map { it.groupId })
    }

    @Test
    fun `공유 중에 일시중지하면 그 그룹은 바로 빠진다`() {
        // 공유 세션이 돌고 있는 동안에도 매번 sharingTargets 를 거치므로 즉시 반영된다.
        val before = listOf(target(ShareState.Consented, groupId = "g1"))
        val after = listOf(target(ShareState.Paused, groupId = "g1"))

        assertEquals(1, SharePolicy.sharingTargets(before).size)
        assertTrue(SharePolicy.sharingTargets(after).isEmpty())
    }

    // ---- 자동 시작 켜기 순서 ----

    @Test
    fun `자동 시작은 명시적 공개부터 거친다`() {
        assertEquals(
            AutoStartStep.Disclosure,
            AutoStartFlow.nextStep(
                disclosureAccepted = false,
                locationPermissionGranted = true,
                backgroundLocationGranted = true,
            ),
        )
    }

    @Test
    fun `공개를 읽은 뒤 위치 권한, 그 뒤 배경 위치 권한 순서다`() {
        assertEquals(
            AutoStartStep.LocationPermission,
            AutoStartFlow.nextStep(
                disclosureAccepted = true,
                locationPermissionGranted = false,
                backgroundLocationGranted = false,
            ),
        )
        assertEquals(
            AutoStartStep.BackgroundPermission,
            AutoStartFlow.nextStep(
                disclosureAccepted = true,
                locationPermissionGranted = true,
                backgroundLocationGranted = false,
            ),
        )
        assertEquals(
            AutoStartStep.Ready,
            AutoStartFlow.nextStep(
                disclosureAccepted = true,
                locationPermissionGranted = true,
                backgroundLocationGranted = true,
            ),
        )
    }

    @Test
    fun `설정에서 켜 두었어도 배경 위치 권한이 빠지면 기본 모드로 돌아간다`() {
        // 사용자가 시스템 설정에서 '항상 허용'을 '앱 사용 중에만'으로 내린 경우.
        val mode = AutoStartFlow.effectiveMode(
            autoStartEnabled = true,
            locationPermissionGranted = true,
            backgroundLocationGranted = false,
        )

        assertEquals(ShareStartMode.Prompt, mode)
        assertFalse(mode.isAuto)
    }

    @Test
    fun `권한이 다 있고 설정도 켜져 있으면 자동 시작이다`() {
        val mode = AutoStartFlow.effectiveMode(
            autoStartEnabled = true,
            locationPermissionGranted = true,
            backgroundLocationGranted = true,
        )

        assertEquals(ShareStartMode.Auto, mode)
    }

    @Test
    fun `설정을 끄면 권한이 다 있어도 기본 모드다`() {
        val mode = AutoStartFlow.effectiveMode(
            autoStartEnabled = false,
            locationPermissionGranted = true,
            backgroundLocationGranted = true,
        )

        assertEquals(ShareStartMode.Prompt, mode)
    }
}
