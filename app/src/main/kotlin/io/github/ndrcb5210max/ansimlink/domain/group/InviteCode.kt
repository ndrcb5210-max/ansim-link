package io.github.ndrcb5210max.ansimlink.domain.group

import kotlin.random.Random

/**
 * 안심 그룹 초대 코드.
 *
 * - 6자리, 만든 지 24시간 동안만 쓸 수 있다.
 * - 사람이 말로 불러 주거나 받아 적는 코드라서, 헷갈리는 글자(0·O, 1·I·L)를 뺀 32글자만 쓴다.
 * - 입력은 소문자·공백·하이픈을 섞어도 되게 [normalize] 로 정리한 뒤 비교한다.
 */
object InviteCode {

    const val LENGTH = 6

    /** 24시간. CLAUDE.md 의 '초대 코드 24시간 유효'. */
    const val VALID_FOR_MILLIS: Long = 24L * 60 * 60 * 1000

    /** 0·O·1·I·L 을 뺀 32글자. */
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String =
        buildString(LENGTH) {
            repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

    /** 사용자가 친 코드를 비교할 수 있는 모양으로 바꾼다. 공백·하이픈을 지우고 대문자로 만든다. */
    fun normalize(raw: String): String =
        raw.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    /** 길이와 글자가 코드 규칙에 맞는지. 서버에 묻기 전에 걸러 내려고 쓴다. */
    fun isWellFormed(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }

    /** 만든 시각으로부터 만료 시각을 계산한다. */
    fun expiresAtMillis(issuedAtMillis: Long): Long = issuedAtMillis + VALID_FOR_MILLIS

    /**
     * 만료됐는지 판정한다.
     *
     * 만료 시각과 정확히 같은 순간은 아직 만료되지 않은 것으로 본다(경계값은 사용자에게 유리하게).
     */
    fun isExpired(expiresAtMillis: Long, nowMillis: Long): Boolean = nowMillis > expiresAtMillis

    /** 남은 시간(분). 이미 만료됐으면 0. 화면에 '앞으로 n분 유효'로 보여 준다. */
    fun remainingMinutes(expiresAtMillis: Long, nowMillis: Long): Long =
        ((expiresAtMillis - nowMillis).coerceAtLeast(0)) / 60_000
}
