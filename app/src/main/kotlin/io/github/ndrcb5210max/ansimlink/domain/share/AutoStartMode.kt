package io.github.ndrcb5210max.ansimlink.domain.share

/**
 * 발동 모드. CLAUDE.md 3장 '발동 모드는 두 가지다'.
 *
 * Google Play 는 포그라운드 서비스 위치 사용을 '사용자가 시작한 동작의 연장'으로 본다.
 * 그래서 기본은 [Prompt] 다.
 */
enum class ShareStartMode {
    /**
     * 기본 '한 번 눌러 시작'. 재난문자를 감지하면 알림으로 묻고, 사용자가 누를 때 시작한다.
     * 배경 위치 권한이 필요 없다.
     */
    Prompt,

    /**
     * 선택 '자동 시작'. 설정에서 켜야 하고 배경 위치 권한이 필요하다.
     * 권한이 없으면 [Prompt] 로 돌아간다.
     */
    Auto,
    ;

    val isAuto: Boolean get() = this == Auto
}

/**
 * 자동 시작을 켜는 순서의 어느 단계에 있는지.
 *
 * 순서를 바꾸면 안 된다(CLAUDE.md 3장, Google Play 배경 위치 정책).
 * ① 명시적 공개 화면 → ② 위치 권한 → ③ 배경 위치 권한(설정 화면으로 이동).
 *
 * 왜 이 순서인가: 명시적 공개는 '권한을 요청하기 전에' 보여 줘야 한다. 그리고 배경 위치
 * 권한은 전경 위치 권한이 먼저 있어야 요청할 수 있다. Android 11 부터는 배경 위치를
 * 런타임 대화상자로 받을 수 없고 설정 화면으로 보내야 한다.
 */
enum class AutoStartStep {
    /** ① '앱이 닫혀 있을 때도 위치를 수집해 그룹에 공유합니다'를 먼저 읽어야 한다. */
    Disclosure,

    /** ② 전경 위치 권한(ACCESS_FINE/COARSE_LOCATION). */
    LocationPermission,

    /** ③ 배경 위치 권한(ACCESS_BACKGROUND_LOCATION). 설정 화면에서 '항상 허용'. */
    BackgroundPermission,

    /** 세 단계를 다 거쳤다. 자동 시작을 켤 수 있다. */
    Ready,
}

/**
 * 자동 시작 설정 화면의 상태 판단. 순수 함수다.
 *
 * @param disclosureAccepted 명시적 공개 화면에서 '이해했습니다'를 눌렀는지.
 *   뒤로 가기로는 절대 true 가 되지 않는다(CLAUDE.md 원칙 8의 같은 생각).
 */
object AutoStartFlow {

    fun nextStep(
        disclosureAccepted: Boolean,
        locationPermissionGranted: Boolean,
        backgroundLocationGranted: Boolean,
    ): AutoStartStep = when {
        !disclosureAccepted -> AutoStartStep.Disclosure
        !locationPermissionGranted -> AutoStartStep.LocationPermission
        !backgroundLocationGranted -> AutoStartStep.BackgroundPermission
        else -> AutoStartStep.Ready
    }

    /**
     * 지금 실제로 자동 시작으로 동작하는 모드.
     *
     * 설정에서 켜 두었어도 권한이 빠졌으면 [ShareStartMode.Prompt] 로 돌아간다.
     * 사용자가 나중에 시스템 설정에서 '항상 허용'을 '앱 사용 중에만'으로 내릴 수 있으므로,
     * 저장된 스위치 값만 믿으면 안 된다.
     */
    fun effectiveMode(
        autoStartEnabled: Boolean,
        locationPermissionGranted: Boolean,
        backgroundLocationGranted: Boolean,
    ): ShareStartMode = if (
        autoStartEnabled && locationPermissionGranted && backgroundLocationGranted
    ) {
        ShareStartMode.Auto
    } else {
        ShareStartMode.Prompt
    }
}
