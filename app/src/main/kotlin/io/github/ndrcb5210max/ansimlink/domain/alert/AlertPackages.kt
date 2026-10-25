package io.github.ndrcb5210max.ansimlink.domain.alert

/**
 * 재난문자 알림을 읽을 앱 허용 목록.
 *
 * 왜 허용 목록인가: 알림 접근 권한은 **휴대폰의 모든 알림**을 볼 수 있는 권한이다.
 * 우리는 재난문자 앱이 띄운 알림만 필요하므로, 다른 앱의 알림은 패키지 이름만 보고
 * 바로 버린다(제목·본문을 읽지 않고, 어디에도 저장하지 않는다).
 *
 * 근거(CLAUDE.md 3장): 일반 앱은 셀 브로드캐스트를 직접 받을 수 없다
 * (`RECEIVE_EMERGENCY_BROADCAST` 는 signature|privileged). 한국 재난문자 채널(4370~4392)은
 * 기본 재난문자 앱에만 전달된다(AOSP CellBroadcastService).
 *
 * TODO(3단계): 삼성 기기의 실제 재난문자 앱 패키지 이름을 아직 모른다. 실기기에서
 *   디버그 빌드의 '받은 알림 패키지 기록' 화면으로 확인한 뒤 [known] 에 추가한다.
 *   추측으로 넣으면 엉뚱한 앱의 알림을 읽게 되므로 확인 전에는 넣지 않는다.
 */
object AlertPackages {

    /** AOSP·Google 재난문자 앱. 확인된 것만 둔다. */
    val known: Set<String> = setOf(
        "com.google.android.cellbroadcastreceiver",
        "com.android.cellbroadcastreceiver",
        "com.android.cellbroadcastreceiver.module",
    )

    /**
     * 디버그 빌드에서 '테스트 재난문자 보내기'가 쓰는 알림 채널 ID.
     *
     * 테스트 알림은 앱이 스스로 띄우므로 패키지 이름이 우리 앱이다. 우리 앱 전체를
     * 허용 목록에 넣으면 앱이 띄우는 다른 알림(예: '위치를 공유할까요?')까지 다시 읽혀
     * 발동 기록이 더럽혀진다. 그래서 **이 채널로 온 알림만** 테스트용으로 받아들인다.
     */
    const val DEBUG_TEST_CHANNEL_ID = "ansim_debug_test_alert"

    /** 재난문자 앱이 띄운 알림인지. */
    fun isKnownAlertPackage(packageName: String): Boolean = packageName in known

    /**
     * 디버그 빌드의 테스트 알림인지.
     *
     * @param allowSelf 디버그 빌드일 때만 true 를 넘긴다(릴리스 빌드에서는 항상 false).
     */
    fun isDebugTestAlert(
        packageName: String,
        channelId: String?,
        selfPackageName: String,
        allowSelf: Boolean,
    ): Boolean = allowSelf &&
        packageName == selfPackageName &&
        channelId == DEBUG_TEST_CHANNEL_ID

    /**
     * 이 알림을 더 들여다봐도 되는지. false 면 제목·본문을 읽지 않고 바로 버린다.
     */
    fun shouldInspect(
        packageName: String,
        channelId: String?,
        selfPackageName: String,
        allowSelf: Boolean,
    ): Boolean = isKnownAlertPackage(packageName) ||
        isDebugTestAlert(packageName, channelId, selfPackageName, allowSelf)
}
