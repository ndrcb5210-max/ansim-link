package io.github.ndrcb5210max.ansimlink.data.account

import io.github.ndrcb5210max.ansimlink.data.model.AnsimGroup
import io.github.ndrcb5210max.ansimlink.data.model.GroupMember
import io.github.ndrcb5210max.ansimlink.data.model.GroupWithMembers
import io.github.ndrcb5210max.ansimlink.ui.settings.DeleteAccountUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 계정 삭제 전에 '무엇을 물어봐야 하는지' 고르는 규칙.
 *
 * 내가 만든 그룹에 다른 사람이 남아 있으면 말없이 없애면 안 된다.
 */
class DeletionPlanTest {

    private val me = "me"

    private fun member(uid: String) = GroupMember(
        uid = uid,
        displayName = uid,
        locationConsent = true,
        paused = false,
        consentVersion = "2026-10-24",
        consentAtMillis = 0,
    )

    private fun group(id: String, ownerUid: String, memberUids: List<String>) = GroupWithMembers(
        group = AnsimGroup(id, "그룹 $id", ownerUid, memberUids),
        members = memberUids.map(::member),
    )

    @Test
    fun `내가 혼자인 내 그룹은 묻지 않고 지운다`() {
        val plan = DeletionPlan.from(me, listOf(group("g1", me, listOf(me))))

        assertEquals(1, plan.ownedGroupsAlone.size)
        assertTrue(plan.ownedGroupsWithOthers.isEmpty())
        assertTrue(plan.joinedGroups.isEmpty())
        assertFalse(plan.needsChoice)
    }

    @Test
    fun `내 그룹에 다른 사람이 남아 있으면 선택을 받아야 한다`() {
        val plan = DeletionPlan.from(me, listOf(group("g1", me, listOf(me, "other"))))

        assertEquals(1, plan.ownedGroupsWithOthers.size)
        assertTrue(plan.ownedGroupsAlone.isEmpty())
        assertTrue(plan.needsChoice)
    }

    @Test
    fun `남이 만든 그룹은 나만 빠지면 된다`() {
        val plan = DeletionPlan.from(me, listOf(group("g1", "owner", listOf("owner", me))))

        assertEquals(1, plan.joinedGroups.size)
        assertTrue(plan.ownedGroupsWithOthers.isEmpty())
        assertTrue(plan.ownedGroupsAlone.isEmpty())
        assertFalse(plan.needsChoice)
    }

    @Test
    fun `여러 그룹을 종류별로 나눈다`() {
        val plan = DeletionPlan.from(
            me,
            listOf(
                group("alone", me, listOf(me)),
                group("withOthers", me, listOf(me, "a")),
                group("joined", "owner", listOf("owner", me)),
            ),
        )

        assertEquals(listOf("alone"), plan.ownedGroupsAlone.map { it.group.id })
        assertEquals(listOf("withOthers"), plan.ownedGroupsWithOthers.map { it.group.id })
        assertEquals(listOf("joined"), plan.joinedGroups.map { it.group.id })
    }

    @Test
    fun `그룹이 하나도 없으면 물어볼 것이 없다`() {
        val plan = DeletionPlan.from(me, emptyList())
        assertFalse(plan.needsChoice)
    }

    @Test
    fun `재인증 전에는 삭제 버튼이 꺼진다`() {
        val state = DeleteAccountUiState(
            plan = DeletionPlan.from(me, emptyList()),
            reauthenticated = false,
        )
        assertFalse(state.canDelete)
    }

    @Test
    fun `재인증하면 삭제할 수 있다`() {
        val state = DeleteAccountUiState(
            plan = DeletionPlan.from(me, emptyList()),
            reauthenticated = true,
        )
        assertTrue(state.canDelete)
    }

    @Test
    fun `내 그룹 처리를 고르지 않으면 삭제 버튼이 꺼진다`() {
        val plan = DeletionPlan.from(me, listOf(group("g1", me, listOf(me, "a"))))
        val state = DeleteAccountUiState(plan = plan, reauthenticated = true)

        assertFalse(state.allChoicesMade)
        assertFalse(state.canDelete)
    }

    @Test
    fun `그룹마다 처리를 고르면 삭제할 수 있다`() {
        val plan = DeletionPlan.from(
            me,
            listOf(group("g1", me, listOf(me, "a")), group("g2", me, listOf(me, "b"))),
        )
        val partly = DeleteAccountUiState(
            plan = plan,
            reauthenticated = true,
            choices = mapOf("g1" to OwnedGroupChoice.Disband),
        )
        assertFalse(partly.canDelete)

        val fully = partly.copy(
            choices = partly.choices + ("g2" to OwnedGroupChoice.Transfer("b")),
        )
        assertTrue(fully.canDelete)
    }

    @Test
    fun `Google 계정은 비밀번호 없이 재인증한다`() {
        val google = DeleteAccountUiState(
            user = io.github.ndrcb5210max.ansimlink.data.model.SignedInUser(
                uid = me,
                email = "hong@example.com",
                displayName = "민수",
                emailVerified = true,
                signedInWithGoogle = true,
            ),
        )
        assertTrue(google.usesGoogleReauth)
        assertTrue(google.canReauthenticate)
    }

    @Test
    fun `이메일 계정은 비밀번호를 입력해야 재인증 버튼이 켜진다`() {
        val email = DeleteAccountUiState(
            user = io.github.ndrcb5210max.ansimlink.data.model.SignedInUser(
                uid = me,
                email = "hong@example.com",
                displayName = "민수",
                emailVerified = true,
                signedInWithGoogle = false,
            ),
        )
        assertFalse(email.canReauthenticate)
        assertTrue(email.copy(password = "abcd1234").canReauthenticate)
    }
}
