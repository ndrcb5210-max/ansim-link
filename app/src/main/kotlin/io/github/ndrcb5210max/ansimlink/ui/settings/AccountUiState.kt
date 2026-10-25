package io.github.ndrcb5210max.ansimlink.ui.settings

import io.github.ndrcb5210max.ansimlink.data.AppError
import io.github.ndrcb5210max.ansimlink.data.account.DeletionPlan
import io.github.ndrcb5210max.ansimlink.data.account.OwnedGroupChoice
import io.github.ndrcb5210max.ansimlink.data.model.SignedInUser

/** 설정 > 계정 화면 상태. */
data class AccountUiState(
    val user: SignedInUser? = null,
    val serverReady: Boolean = true,
    val busy: Boolean = false,
    val error: AppError? = null,
    val verificationEmailSent: Boolean = false,
)

/** 계정 삭제 화면 상태. */
data class DeleteAccountUiState(
    val user: SignedInUser? = null,
    val serverReady: Boolean = true,
    /** 어떤 그룹을 어떻게 처리할지. 화면을 열 때 불러온다. */
    val plan: DeletionPlan? = null,
    /** 그룹 ID → 사용자가 고른 처리 방법. */
    val choices: Map<String, OwnedGroupChoice> = emptyMap(),
    val password: String = "",
    val reauthenticated: Boolean = false,
    val busy: Boolean = false,
    val error: AppError? = null,
    val deleted: Boolean = false,
) {
    val loading: Boolean get() = plan == null && serverReady && error == null

    /** Google 계정이면 비밀번호 대신 Google 로 다시 로그인한다. */
    val usesGoogleReauth: Boolean get() = user?.signedInWithGoogle == true

    val canReauthenticate: Boolean
        get() = !busy && serverReady && (usesGoogleReauth || password.isNotEmpty())

    /** 다른 구성원이 남아 있는 내 그룹마다 처리 방법을 골랐는지. */
    val allChoicesMade: Boolean
        get() = plan?.ownedGroupsWithOthers.orEmpty().all { choices.containsKey(it.group.id) }

    val canDelete: Boolean
        get() = !busy && serverReady && reauthenticated && plan != null && allChoicesMade
}
