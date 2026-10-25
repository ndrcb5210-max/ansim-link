package io.github.ndrcb5210max.ansimlink.domain.alert

/**
 * 알림 제목·본문을 비교하기 쉽게 다듬는 함수들. 순수 Kotlin이라 단위 테스트가 쉽다.
 *
 * 왜 공백을 지우는가: 같은 재난문자가 기기·안드로이드 버전에 따라
 * '긴급 재난문자' / '긴급 재난 문자' 처럼 다른 띄어쓰기로 온다. 줄바꿈도 섞인다.
 * 공백을 모두 지우면 이 사본들이 한 문자열로 모여 비교가 간단해진다.
 */
object AlertText {

    /** 발동 기록에 남길 본문 길이. 그 이상은 저장하지 않는다(CLAUDE.md 최소 수집). */
    const val PREVIEW_LENGTH = 60

    /** 본문이 없을 때의 지문. 중복 판단에서 따로 걸러 낸다. */
    const val EMPTY_FINGERPRINT = "empty"

    // Char.isWhitespace() 는 전각 공백(U+3000)은 공백으로 보지만
    // 줄바꿈 없는 공백(U+00A0 NBSP)과 폭 없는 공백(U+200B)은 공백으로 보지 않는다.
    // 알림 본문에 이 두 글자가 섞여 오는 경우가 있어서 따로 지운다.
    // 눈에 보이지 않는 글자라서 소스에는 글자 대신 코드 번호로 적는다.
    private val extraBlankCodes = intArrayOf(0x00A0, 0x200B)

    private val whitespaceRun = Regex("\\s+")

    /** 모든 공백(띄어쓰기·탭·줄바꿈·전각 공백·NBSP·폭 없는 공백)을 지운다. */
    fun squeeze(value: String?): String =
        value?.filterNot { it.isWhitespace() || it.code in extraBlankCodes } ?: ""

    /**
     * 본문 앞 [PREVIEW_LENGTH] 자. 발동 기록 화면에 보여 주고, 그만큼만 저장한다.
     *
     * 줄바꿈과 이어진 공백은 한 칸으로 줄인다(목록에서 한 줄로 보여 주기 때문이다).
     * 잘렸으면 끝에 '…' 를 붙인다.
     */
    fun preview(body: String?): String {
        val flattened = body?.replace(whitespaceRun, " ")?.trim() ?: ""
        return if (flattened.length <= PREVIEW_LENGTH) {
            flattened
        } else {
            flattened.take(PREVIEW_LENGTH) + "…"
        }
    }

    /**
     * 같은 문자인지 가릴 때 쓰는 지문.
     *
     * 본문을 그대로 두면 재난문자 전문이 로컬 DB에 남는다. 그래서 공백을 지운 본문을
     * 해시해서 그 값만 저장한다. 같은 본문이면 같은 값이 나오고, 값에서 본문을 되돌릴 수 없다.
     *
     * 암호용이 아니라 '같은 문자인지'만 보는 용도라서 64비트 FNV-1a 를 쓴다
     * (바이트가 아니라 UTF-16 코드 단위로 돌린다. 결정적이고 충돌이 드물면 충분하다).
     * 본문이 비면 모두 [EMPTY_FINGERPRINT] 가 되지만, 본문이 없는 알림으로는 발동하지 않으므로
     * 중복 판단에 쓰이지 않는다.
     */
    fun fingerprint(body: String?): String {
        val normalized = squeeze(body)
        if (normalized.isEmpty()) return EMPTY_FINGERPRINT
        var hash = 0xcbf29ce484222325uL // FNV-1a 64비트 offset basis
        normalized.forEach { char ->
            hash = hash xor char.code.toULong()
            hash *= 0x100000001b3uL // FNV 소수
        }
        return hash.toString(16).padStart(16, '0')
    }
}
