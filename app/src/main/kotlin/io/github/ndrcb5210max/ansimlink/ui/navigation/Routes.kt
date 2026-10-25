package io.github.ndrcb5210max.ansimlink.ui.navigation

import io.github.ndrcb5210max.ansimlink.domain.consent.LegalDocument

/**
 * 화면 경로.
 *
 * 하단 탭 세 개는 [AnsimTab] 에 있고, 여기에는 탭 위에 올라오는 화면들을 둔다.
 * 이 화면들에서는 하단 탭을 숨긴다([isTopLevel]).
 */
object Routes {
    const val AUTH = "auth"
    const val GROUP_CREATE = "group/create"
    const val GROUP_JOIN = "group/join"
    const val ACCOUNT = "settings/account"
    const val ACCOUNT_DELETE = "settings/account/delete"

    const val LEGAL_ARG = "document"
    const val LEGAL_PATTERN = "legal/{$LEGAL_ARG}"

    fun legal(document: LegalDocument): String = "legal/${document.routeKey}"

    /** 하단 탭을 보여 줄 경로인지. */
    fun isTopLevel(route: String?): Boolean =
        route != null && AnsimTab.entries.any { it.route == route }
}
