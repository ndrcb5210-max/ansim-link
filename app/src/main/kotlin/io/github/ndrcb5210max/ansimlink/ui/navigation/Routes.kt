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

    // 4단계: 위치 공유와 안심 메시지
    const val SHARE_SETTINGS = "settings/share"

    /** 위치 권한을 요청하기 전에 보여 주는 설명 화면. */
    const val LOCATION_DISCLOSURE = "settings/share/disclosure"
    const val SAFETY_MESSAGE = "settings/message"
    const val PROVISION_LOG = "settings/provision"

    /** 가족 위치 보기. 알림의 [위치 보기]가 여기로 온다. */
    const val FAMILY_LOCATION = "share/family"

    // 5단계: 구독 결제
    const val SUBSCRIPTION = "settings/subscription"

    /** 결제 화면(paywall). 설정 > 구독과 공유 시간 안내 양쪽에서 들어온다. */
    const val PAYWALL = "settings/subscription/paywall"

    // 디버그 빌드에서만 들어갈 수 있다(설정 화면에 항목이 보이지 않는다).
    const val DEBUG_TEST_ALERT = "settings/debug/test-alert"
    const val DEBUG_OBSERVED_PACKAGES = "settings/debug/packages"
    const val DEBUG_FAKE_BILLING = "settings/debug/fake-billing"

    const val LEGAL_ARG = "document"
    const val LEGAL_PATTERN = "legal/{$LEGAL_ARG}"

    fun legal(document: LegalDocument): String = "legal/${document.routeKey}"

    /** 하단 탭을 보여 줄 경로인지. */
    fun isTopLevel(route: String?): Boolean =
        route != null && AnsimTab.entries.any { it.route == route }
}
