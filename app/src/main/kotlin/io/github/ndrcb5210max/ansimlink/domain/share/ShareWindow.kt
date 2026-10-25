package io.github.ndrcb5210max.ansimlink.domain.share

import io.github.ndrcb5210max.ansimlink.domain.group.Plan
import io.github.ndrcb5210max.ansimlink.domain.group.PlanLimits

/**
 * 공유 시간의 한도. 숫자는 CLAUDE.md 원칙 3('기본 60분, 설정 30~180분')에서 가져왔다.
 *
 * 요금제별 상한은 [PlanLimits.maxShareMinutes] 가 정한다(무료 60, 프리미엄 180).
 * 여기 [MAX_MINUTES] 는 어떤 요금제에서도 넘을 수 없는 값이다.
 */
object ShareDuration {

    const val DEFAULT_MINUTES = 60
    const val MIN_MINUTES = 30

    /** 프리미엄 상한과 같다. 요금제와 무관한 하드 상한으로도 쓴다. */
    const val MAX_MINUTES = 180

    /** 설정 화면의 슬라이더가 움직이는 눈금. */
    const val STEP_MINUTES = 30

    /**
     * 설정 화면에 고를 수 있게 보여 줄 값. 요금제 상한을 넘는 값도 **같이 보여 주고**
     * 고를 수 없게 표시한다. 프리미엄에 무엇이 있는지 숨기지 않으려는 것이다.
     */
    val selectableMinutes: List<Int> =
        (MIN_MINUTES..MAX_MINUTES step STEP_MINUTES).toList()

    /**
     * 사용자가 고른 값을 이 요금제에서 실제로 쓸 수 있는 값으로 줄인다.
     *
     * 앱이 혼자 판단해서 기능을 **열지는** 않는다는 원칙이 있으므로(CLAUDE.md 3장),
     * 여기서는 언제나 '막는' 방향으로만 움직인다. 저장된 값이 상한보다 크면 상한으로 내린다.
     * 구독을 해지해 상한이 60분으로 내려가도 저장된 180분 때문에 그대로 공유되지 않는다.
     */
    fun effectiveMinutes(requestedMinutes: Int, plan: Plan): Int {
        val cap = minOf(PlanLimits.maxShareMinutes(plan), MAX_MINUTES)
        return requestedMinutes.coerceIn(MIN_MINUTES, cap)
    }

    /** 이 요금제에서 고를 수 있는 값인지. 설정 화면이 칩을 끄는 데 쓴다. */
    fun isAllowed(minutes: Int, plan: Plan): Boolean =
        minutes <= minOf(PlanLimits.maxShareMinutes(plan), MAX_MINUTES)
}

/**
 * 공유 세션 하나의 시간 창.
 *
 * 시계를 품지 않는다. 시작 시각을 인자로 받아 끝 시각을 계산해 두므로, 서비스가 죽었다
 * 되살아나도 같은 값으로 되살릴 수 있고 테스트에서 시간을 마음대로 돌릴 수 있다.
 *
 * @param startedAtMillis 공유를 시작한 시각.
 * @param minutes 공유할 시간(분). [ShareDuration.effectiveMinutes] 를 거친 값을 넣는다.
 */
data class ShareWindow(
    val startedAtMillis: Long,
    val minutes: Int,
) {
    /** 자동으로 끝나는 시각. 상시 알림에 '○시 ○분 종료 예정'으로 보여 준다. */
    val endsAtMillis: Long get() = startedAtMillis + minutes * MILLIS_PER_MINUTE

    /** [nowMillis] 기준으로 남은 시간. 다 끝났으면 0. */
    fun remainingMillis(nowMillis: Long): Long =
        (endsAtMillis - nowMillis).coerceAtLeast(0L)

    /**
     * 끝났는지. 끝 시각에 **닿으면** 끝난 것으로 본다.
     *
     * 경계를 '이상'으로 둔 이유: 자동 종료 알람이 정확히 끝 시각에 깨어났을 때
     * '아직 안 끝났다'로 보면 세션이 1밀리초 동안 더 살아 있는 상태가 된다.
     */
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= endsAtMillis

    /** 남은 시간을 분으로. 올림한다(29초 남았는데 '0분 남음'으로 보이지 않게). */
    fun remainingMinutes(nowMillis: Long): Int {
        val remaining = remainingMillis(nowMillis)
        return ((remaining + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
    }

    companion object {
        const val MILLIS_PER_MINUTE: Long = 60_000L

        /** 지금 시각과 고른 시간으로 창을 만든다. */
        fun start(nowMillis: Long, requestedMinutes: Int, plan: Plan): ShareWindow = ShareWindow(
            startedAtMillis = nowMillis,
            minutes = ShareDuration.effectiveMinutes(requestedMinutes, plan),
        )
    }
}
