package io.github.ndrcb5210max.ansimlink.domain.share

import io.github.ndrcb5210max.ansimlink.domain.group.ShareState

/**
 * 공유를 시작하지 못한 이유. 화면과 알림에 그대로 보여 준다.
 *
 * 왜 이유를 남기는가: '공유 시작을 눌렀는데 아무 일도 없었다'는 상황이 생기면 안 된다.
 * 3단계의 [io.github.ndrcb5210max.ansimlink.domain.alert.TriggerReason] 과 같은 생각이다.
 */
enum class ShareBlockReason(val key: String) {
    /** 로그인하지 않았다. */
    NotSignedIn("not_signed_in"),

    /** 들어가 있는 그룹이 없다. 혼자서는 공유할 상대가 없다. */
    NoGroup("no_group"),

    /** 위치 제공 동의를 철회했다. 어떤 경우에도 시작하지 않는다(CLAUDE.md 원칙 1). */
    ConsentWithdrawn("consent_withdrawn"),

    /** 내가 일시중지해 두었다(위치정보법 제24조). */
    Paused("paused"),

    /** 위치 권한이 없다. 설명 화면을 거쳐 요청해야 한다. */
    LocationPermissionMissing("location_permission_missing"),

    /** 이미 공유 중이다. 새로 시작하지 않고 남은 시간만 늘릴지 사용자가 정한다. */
    AlreadySharing("already_sharing"),

    /**
     * 자동 시작 모드인데 배경 위치 권한이 없다.
     *
     * 이때는 공유를 시작하지 않고 '눌러서 시작' 알림으로 대신한다
     * (CLAUDE.md 3장: 권한이 없으면 기본 모드로 돌아간다).
     */
    BackgroundLocationMissing("background_location_missing"),
}

/** 공유 시작 판단 결과. */
sealed interface ShareStartDecision {
    data object Allowed : ShareStartDecision

    data class Blocked(val reason: ShareBlockReason) : ShareStartDecision

    val allowed: Boolean get() = this is Allowed

    val blockReason: ShareBlockReason? get() = (this as? Blocked)?.reason
}

/**
 * 공유를 시작할 때 내가 그 그룹에서 어떤 상태인지.
 *
 * @param groupId 그룹 ID.
 * @param groupName 그룹 이름. 상시 알림 문구에 쓴다.
 * @param shareState 내 동의·일시중지 상태.
 * @param receiverCount 내 위치를 받게 되는 사람 수(나를 뺀 구성원 수).
 */
data class ShareTarget(
    val groupId: String,
    val groupName: String,
    val shareState: ShareState,
    val receiverCount: Int,
)

/**
 * 공유를 시작해도 되는지 정하는 순수 함수.
 *
 * 저장소도, 권한 API 도, 시계도 건드리지 않는다. 호출하는 쪽(서비스·ViewModel)이
 * 권한 확인 결과와 그룹 상태를 모아서 넘긴다.
 */
object SharePolicy {

    /**
     * @param targets 내가 들어가 있는 그룹들. 동의한 그룹이 하나도 없으면 시작하지 않는다.
     * @param signedIn 로그인 상태.
     * @param locationPermissionGranted ACCESS_FINE 또는 ACCESS_COARSE 중 하나라도 있는지.
     * @param alreadySharing 이미 공유 세션이 돌고 있는지.
     * @param autoStart 자동 시작 모드로 시작하려는 것인지. 그때만 배경 위치 권한을 본다.
     * @param backgroundLocationGranted ACCESS_BACKGROUND_LOCATION 이 있는지.
     */
    fun decide(
        targets: List<ShareTarget>,
        signedIn: Boolean,
        locationPermissionGranted: Boolean,
        alreadySharing: Boolean = false,
        autoStart: Boolean = false,
        backgroundLocationGranted: Boolean = false,
    ): ShareStartDecision {
        if (!signedIn) return ShareStartDecision.Blocked(ShareBlockReason.NotSignedIn)
        if (alreadySharing) return ShareStartDecision.Blocked(ShareBlockReason.AlreadySharing)
        if (targets.isEmpty()) return ShareStartDecision.Blocked(ShareBlockReason.NoGroup)

        // 동의 검사를 권한 검사보다 **먼저** 한다. 동의를 철회한 사람에게 위치 권한을
        // 요청하는 화면이 뜨면 안 된다(CLAUDE.md 원칙 1: 동의가 먼저다).
        if (targets.none { it.shareState.sharesLocation }) {
            // 일시중지와 철회를 구분해서 알려 준다. 사용자가 할 일이 다르다.
            val reason = if (targets.any { it.shareState == ShareState.Paused }) {
                ShareBlockReason.Paused
            } else {
                ShareBlockReason.ConsentWithdrawn
            }
            return ShareStartDecision.Blocked(reason)
        }

        if (!locationPermissionGranted) {
            return ShareStartDecision.Blocked(ShareBlockReason.LocationPermissionMissing)
        }
        if (autoStart && !backgroundLocationGranted) {
            return ShareStartDecision.Blocked(ShareBlockReason.BackgroundLocationMissing)
        }
        return ShareStartDecision.Allowed
    }

    /**
     * 실제로 위치를 보낼 그룹만 고른다.
     *
     * 동의하지 않았거나 일시중지한 그룹은 여기서 빠진다. 공유 세션이 돌고 있는 동안에도
     * 매번 이 함수를 거치므로, 공유 중에 일시중지를 누르면 그 그룹에는 바로 쓰기가 멈춘다
     * (CLAUDE.md 원칙 1: 즉시 반영).
     */
    fun sharingTargets(targets: List<ShareTarget>): List<ShareTarget> =
        targets.filter { it.shareState.sharesLocation }
}
