package io.github.ndrcb5210max.ansimlink.domain.billing

/** 결제 주기의 단위. */
enum class BillingPeriodUnit { Day, Week, Month, Year }

/**
 * 결제 주기. Play 가 ISO 8601 기간 문자열로 준다(`P1W`, `P1M`, `P3M`, `P6M`, `P1Y`, `P7D`).
 *
 * @param raw Play 가 준 원래 문자열. [unit] 이 null 일 때 화면이 이것을 그대로 보여 준다.
 * @param unit 알아본 단위. 모르는 형식이면 null.
 * @param count 단위의 개수. [unit] 이 null 이면 0.
 */
data class BillingPeriod(
    val raw: String,
    val unit: BillingPeriodUnit?,
    val count: Int,
) {

    /** 화면에 기간을 한국어로 적을 수 있는지. 못 하면 [raw] 를 그대로 보여 준다. */
    val known: Boolean get() = unit != null && count > 0

    companion object {

        /**
         * ISO 8601 기간 문자열을 읽는다.
         *
         * 구독 결제 주기는 `P` + 숫자 + 단위 한 덩어리로만 온다. 그것만 읽고 나머지는
         * '모르는 형식'으로 둔다. 억지로 해석해서 **틀린 주기를 화면에 적는 것이 가장
         * 나쁘다** — 사용자가 매달 빠져나갈 돈을 그 숫자로 판단하기 때문이다.
         * 그래서 모르면 Play 가 준 문자열을 그대로 보여 준다.
         *
         * 대소문자는 가린다. ISO 8601 은 대문자를 쓰지만, 혹시 다른 모양이 와도
         * '모르는 형식'으로 떨어지는 것보다 읽어 주는 쪽이 낫다.
         */
        fun parse(raw: String?): BillingPeriod {
            val text = raw?.trim().orEmpty()
            val match = PATTERN.matchEntire(text)
            val count = match?.groupValues?.get(1)?.toIntOrNull()
            val unit = when (match?.groupValues?.get(2)?.uppercase()) {
                "D" -> BillingPeriodUnit.Day
                "W" -> BillingPeriodUnit.Week
                "M" -> BillingPeriodUnit.Month
                "Y" -> BillingPeriodUnit.Year
                else -> null
            }
            return if (unit != null && count != null && count > 0) {
                BillingPeriod(raw = text, unit = unit, count = count)
            } else {
                BillingPeriod(raw = text, unit = null, count = 0)
            }
        }

        // 'P' + 숫자 + 단위 한 글자. 'T'(시·분·초)는 구독 주기에 쓰이지 않으므로 받지 않는다.
        private val PATTERN = Regex("^[Pp](\\d+)([DdWwMmYy])$")
    }
}
