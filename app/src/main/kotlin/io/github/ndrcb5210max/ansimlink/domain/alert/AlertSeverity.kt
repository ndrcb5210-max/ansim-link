package io.github.ndrcb5210max.ansimlink.domain.alert

// 재난문자의 '문자 종류'.
//
// 셀 방송에는 종류를 알려 주는 값이 없고, 우리는 재난문자 앱이 띄운 알림만 본다.
// 그래서 알림 제목으로 종류를 정한다. 제목은 기기 언어에 따라 한국어·영어로 온다.
//
// 제목 문자열의 출처: AOSP packages/apps/CellBroadcastReceiver 의 알림 제목.
// 한국어 번역은 기기·안드로이드 버전에 따라 띄어쓰기가 다르다
// ('긴급 재난문자' / '긴급 재난 문자'). 그래서 비교하기 전에 공백을 모두 지운다
// ([AlertText.squeeze]). 그러면 띄어쓰기가 다른 사본들이 한 문자열로 모인다.
//
// 각 종류에 긴 제목과 짧은 제목을 같이 둔 것은 안전장치다. 긴 제목('위급재난문자')은
// 짧은 제목('위급재난')을 포함하므로 짧은 쪽만 있어도 맞지만, 어떤 제목을 보고 만든
// 목록인지 코드에 남겨 두는 편이 나중에 실기기에서 고칠 때 편하다.
// [label] 을 strings.xml 이 아니라 여기 둔 이유: 이 네 낱말은 화면 문구가 아니라
// 제도에서 정한 이름이다(문자 종류 3단계 + 실종경보문자). 제목을 맞히는 목록과 같은 자리에
// 두면 한 군데만 고치면 되고 어긋날 일이 없다. 사람이 읽는 설명 문구는 strings.xml 에 둔다.
enum class AlertSeverity(
    // 저장(DataStore·Room)에 쓰는 이름. 한 번 정하면 바꾸지 않는다.
    val key: String,
    // 화면에 보여 주는 이름.
    val label: String,
    // 공백을 지운 한국어 제목들.
    val koreanTitles: List<String>,
    // 공백을 지우고 소문자로 만든 영어 제목들.
    val englishTitles: List<String>,
) {
    // 위급재난문자. 지진·공습처럼 바로 대피해야 하는 경보.
    Extreme(
        key = "extreme",
        label = "위급재난문자",
        koreanTitles = listOf("위급재난문자", "위급재난"),
        englishTitles = listOf("extremeemergencyalert"),
    ),

    // 긴급재난문자.
    Emergency(
        key = "emergency",
        label = "긴급재난문자",
        koreanTitles = listOf("긴급재난문자", "긴급재난"),
        englishTitles = listOf("emergencyalert"),
    ),

    // 안전안내문자. 가장 낮은 단계라 기본값은 '발동하지 않음'이다.
    PublicSafety(
        key = "public_safety",
        label = "안전안내문자",
        koreanTitles = listOf("안전안내문자", "안전안내"),
        englishTitles = listOf("publicsafetyalert"),
    ),

    // 실종경보문자. 재난이 아니라 실종 아동·장애인 등을 찾는 문자다.
    AmberAlert(
        key = "amber",
        label = "실종경보문자",
        koreanTitles = listOf("실종경보문자", "실종경보"),
        englishTitles = listOf("amberalert"),
    ),

    // 종류 미상. '새 경보', '읽지 않은 경보 3개' 같은 묶음 알림이거나 제목이 맞지 않는 알림.
    // 무엇인지 모르는 알림으로는 발동하지 않는다(TriggerReason.SeverityUnknown).
    Unknown(
        key = "unknown",
        label = "종류 미상",
        koreanTitles = emptyList(),
        englishTitles = emptyList(),
    ),
    ;

    /** 발동 규칙 화면에 줄로 보여 줄 종류인지. '종류 미상'은 사용자가 정할 것이 없다. */
    val configurable: Boolean get() = this != Unknown

    companion object {
        /** 발동 규칙 화면에 보여 주는 순서. 센 것부터. */
        val configurableEntries: List<AlertSeverity> = entries.filter { it.configurable }

        fun fromKey(key: String): AlertSeverity? = entries.firstOrNull { it.key == key }

        /**
         * 알림 제목으로 문자 종류를 정한다.
         *
         * 제목에 '포함'되는지로 본다. 기기에 따라 제목 앞뒤에 앱 이름이나 발신 시각이 붙기 때문이다.
         * 선언 순서(위급 → 긴급 → 안전안내 → 실종경보)로 먼저 맞는 것을 쓴다.
         * 'Extreme Emergency Alert' 안에 'Emergency Alert' 가 들어 있으므로 순서가 중요하다.
         */
        fun fromTitle(title: String?): AlertSeverity {
            val squeezed = AlertText.squeeze(title)
            if (squeezed.isEmpty()) return Unknown
            val lowercase = squeezed.lowercase()
            return entries.firstOrNull { severity ->
                severity.koreanTitles.any { it in squeezed } ||
                    severity.englishTitles.any { it in lowercase }
            } ?: Unknown
        }
    }
}
