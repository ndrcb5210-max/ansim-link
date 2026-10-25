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

    // 3단계: 재난문자 감지
    const val DETECTION_SETUP = "settings/detection"
    const val TRIGGER_RULES = "settings/rules"
    const val ALERT_LOG = "settings/alerts"

    // 디버그 빌드에서만 들어갈 수 있다(설정 화면에 항목이 보이지 않는다).
    const val DEBUG_TEST_ALERT = "settings/debug/test-alert"
    const val DEBUG_OBSERVED_PACKAGES = "settings/debug/packages"

    const val LEGAL_ARG = "document"
    const val LEGAL_PATTERN = "legal/{$LEGAL_ARG}"

    fun legal(document: LegalDocument): String = "legal/${document.routeKey}"

    /** 하단 탭을 보여 줄 경로인지. */
    fun isTopLevel(route: String?): Boolean =
        route != null && AnsimTab.entries.any { it.route == route }
}
