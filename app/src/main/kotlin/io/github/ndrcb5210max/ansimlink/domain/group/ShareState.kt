package io.github.ndrcb5210max.ansimlink.domain.group

/**
 * 구성원의 위치 공유 상태. 그룹 구성원 모두에게 서로 보인다(CLAUDE.md 원칙 10).
 *
 * [Withdrawn] 은 '동의를 철회해서 곧 그룹에서 빠지는' 상태다.
 * 철회하면 구성원 문서를 지우고 그룹에서 나가지만, 지우는 도중에 끊기면 이 상태로 남을 수 있다.
 * 그때도 위치는 수집·전송하지 않는다([sharesLocation] 가 false 다).
 */
enum class ShareState {
    Consented,
    Paused,
    Withdrawn,
    ;

    /** 지금 이 사람의 위치를 공유해도 되는지. 동의한 상태일 때만 true 다. */
    val sharesLocation: Boolean
        get() = this == Consented

    companion object {
        /**
         * Firestore `groups/{gid}/members/{uid}` 의 값으로 상태를 정한다.
         *
         * 동의 여부를 먼저 본다. 동의가 없으면 일시중지 값과 상관없이 [Withdrawn] 이다.
         */
        fun of(locationConsent: Boolean, paused: Boolean): ShareState = when {
            !locationConsent -> Withdrawn
            paused -> Paused
            else -> Consented
        }
    }
}
