package io.github.ndrcb5210max.ansimlink.domain.alert

// 재난 유형.
//
// 셀 방송에는 '이 문자가 무슨 재난인지' 알려 주는 값이 없다. 그래서 본문 낱말로 가린다.
// 어휘는 행정안전부 긴급재난문자 API(DSSP-IF-00247)의 재난구분 35종을 따른다
// (CLAUDE.md 3장). 설정 화면에 35개를 다 보여 주면 너무 많아서 자주 쓰는 12개로 묶는다.

/**
 * 설정 화면에 보여 주는 12개 묶음. 발동 규칙([TriggerRules])의 열이 이것이다.
 *
 * [Unknown] 은 묶음에 들어가지 않는다. 본문이 없거나(묶음 알림) 내용이 가려져서
 * 유형을 가릴 수 없을 때 쓴다. 규칙을 찾을 때는 [Other]('기타') 칸을 본다.
 *
 * @param key 저장(DataStore·Room)에 쓰는 이름. 한 번 정하면 바꾸지 않는다.
 * @param label 화면에 보여 줄 이름. 칩 하나에 들어가는 짧은 말이라 문자열 리소스 대신 여기 둔다
 *   (CLAUDE.md 가 정한 어휘 그대로여야 하고, 분류기 테스트에서도 같은 이름으로 확인한다).
 * @param priority 작을수록 먼저다. 본문에 여러 유형이 맞으면 이 순서로 하나를 고른다.
 */
enum class DisasterType(
    val key: String,
    val label: String,
    val priority: Int,
) {
    Tsunami(key = "tsunami", label = "지진해일", priority = 1),
    Earthquake(key = "earthquake", label = "지진", priority = 2),
    CivilDefense(key = "civil_defense", label = "민방공", priority = 3),
    Landslide(key = "landslide", label = "산사태", priority = 4),
    Flood(key = "flood", label = "홍수", priority = 5),
    HeavyRain(key = "heavy_rain", label = "호우", priority = 6),
    Typhoon(key = "typhoon", label = "태풍", priority = 7),
    WildFire(key = "wild_fire", label = "산불", priority = 8),
    HeavySnow(key = "heavy_snow", label = "대설", priority = 9),
    ColdWave(key = "cold_wave", label = "한파", priority = 10),
    HeatWave(key = "heat_wave", label = "폭염", priority = 11),

    /** 나머지 재난구분을 모두 묶는 칸. 실종경보문자처럼 재난 낱말이 없는 본문도 여기로 온다. */
    Other(key = "other", label = "기타", priority = 12),

    /** 유형 미상. 설정 화면에 보여 주지 않는다. 규칙은 [Other] 칸을 따른다. */
    Unknown(key = "unknown", label = "유형 미상", priority = 99),
    ;

    /** 설정 화면에 칩으로 보여 줄 유형인지. */
    val selectable: Boolean get() = this != Unknown

    /**
     * 규칙 표에서 찾아볼 칸. '유형 미상'은 '기타' 칸의 설정을 따른다.
     *
     * 왜 '기타'로 보내는가: 유형을 모른다는 이유로 조용히 발동하지 않으면,
     * 사용자는 '위급재난문자는 모두 켰는데 왜 아무 일도 없었나'를 알 수 없다.
     * '기타' 칸은 설정 화면에 보이므로 끄고 켤 수 있다.
     */
    val ruleType: DisasterType get() = if (this == Unknown) Other else this

    companion object {
        /** 설정 화면에 보여 주는 12개. 우선순위 순서다. */
        val selectableEntries: List<DisasterType> =
            entries.filter { it.selectable }.sortedBy { it.priority }

        fun fromKey(key: String): DisasterType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 행정안전부 재난구분 35종. 본문 낱말에서 여기까지 가린 뒤 [group] 으로 12개 묶음을 정한다.
 *
 * 35종까지 가리는 이유: 발동 규칙은 12개로 충분하지만, 발동 기록에는
 * '화재'와 '폭발'처럼 더 자세한 값을 남겨 두면 나중에 분류기를 고칠 때 근거가 된다.
 *
 * @param officialName 재난구분 이름. CLAUDE.md 에 적힌 35개와 글자까지 같아야 한다.
 * @param group 설정 화면용 12개 묶음.
 * @param keywords 본문에서 찾을 낱말. 본문은 공백을 지운 뒤 비교하므로 낱말에도 공백을 넣지 않는다.
 *   빈 목록이면 낱말로 찾지 않는다(기타처럼 '나머지' 칸).
 */
enum class DisasterCategory(
    val officialName: String,
    val group: DisasterType,
    val keywords: List<String>,
) {
    // --- 12개 묶음에 1:1로 대응하는 11종 ---
    Tsunami("지진해일", DisasterType.Tsunami, listOf("지진해일", "해일", "쓰나미")),
    // '진도'는 넣지 않는다. 전남 진도군 이름과 겹쳐서 엉뚱한 문자가 지진으로 잡힌다.
    Earthquake("지진", DisasterType.Earthquake, listOf("지진", "여진")),
    CivilDefense("민방공", DisasterType.CivilDefense, listOf("민방공", "민방위", "공습", "경계경보", "미사일")),
    Landslide("산사태", DisasterType.Landslide, listOf("산사태", "토사유출", "급경사지")),
    Flood("홍수", DisasterType.Flood, listOf("홍수", "범람", "댐방류", "방류")),
    // '시간당'은 넣지 않는다. 대설 문자에도 '시간당 3cm' 처럼 쓰여서 호우가 대설을 가로챈다.
    HeavyRain("호우", DisasterType.HeavyRain, listOf("호우", "집중호우", "폭우", "장맛비")),
    Typhoon("태풍", DisasterType.Typhoon, listOf("태풍")),
    WildFire("산불", DisasterType.WildFire, listOf("산불", "입산통제")),
    HeavySnow("대설", DisasterType.HeavySnow, listOf("대설", "폭설", "눈길")),
    ColdWave("한파", DisasterType.ColdWave, listOf("한파", "강추위", "수도관동파", "저체온")),
    HeatWave("폭염", DisasterType.HeatWave, listOf("폭염", "온열질환", "열사병")),

    // --- '기타'로 묶는 24종 ---
    // 아래에서 위로 올라가며 겹치는 일은 없지만, 두 개 이상 맞으면 선언 순서가 앞선 것을 쓴다.
    Ai("AI", DisasterType.Other, listOf("조류인플루엔자", "고병원성")),
    Drought("가뭄", DisasterType.Other, listOf("가뭄", "제한급수")),
    LivestockDisease("가축질병", DisasterType.Other, listOf("구제역", "아프리카돼지열병", "럼피스킨", "가축전염병")),
    StrongWind("강풍", DisasterType.Other, listOf("강풍", "돌풍")),
    Dryness("건조", DisasterType.Other, listOf("건조경보", "건조주의보")),
    Traffic("교통", DisasterType.Other, listOf("교통혼잡", "교통정체")),
    TrafficAccident("교통사고", DisasterType.Other, listOf("교통사고", "추돌")),
    TrafficControl("교통통제", DisasterType.Other, listOf("교통통제", "도로통제", "통행제한", "전면통제")),
    Finance("금융", DisasterType.Other, listOf("금융전산", "보이스피싱")),
    Etc("기타", DisasterType.Other, emptyList()),
    FineDust("미세먼지", DisasterType.Other, listOf("미세먼지", "초미세먼지")),
    Collapse("붕괴", DisasterType.Other, listOf("붕괴", "싱크홀", "지반침하")),
    Water("수도", DisasterType.Other, listOf("단수", "수도관파손", "급수중단")),
    Fog("안개", DisasterType.Other, listOf("안개", "가시거리")),
    Energy("에너지", DisasterType.Other, listOf("전력수급", "절전", "에너지절약")),
    Epidemic("전염병", DisasterType.Other, listOf("감염병", "전염병", "확진", "코로나", "메르스", "홍역")),
    PowerOutage("정전", DisasterType.Other, listOf("정전", "복전")),
    Terror("테러", DisasterType.Other, listOf("테러")),
    Telecom("통신", DisasterType.Other, listOf("통신장애", "통신망")),
    Explosion("폭발", DisasterType.Other, listOf("폭발")),
    HighSeas("풍랑", DisasterType.Other, listOf("풍랑", "너울")),
    Fire("화재", DisasterType.Other, listOf("화재", "연소확대")),
    EnvironmentalAccident(
        "환경오염사고",
        DisasterType.Other,
        listOf("유해화학물질", "화학물질", "가스누출", "환경오염", "악취"),
    ),
    YellowDust("황사", DisasterType.Other, listOf("황사")),
    ;

    companion object {
        /**
         * 공백을 지운 본문에서 맞는 재난구분을 모두 찾는다.
         *
         * 우선순위는 묶음([DisasterType.priority])으로 정한다. 같은 묶음 안에서는
         * 선언 순서가 앞선 것을 쓴다(예: '폭발'과 '화재'가 같이 있으면 '폭발').
         */
        fun matches(squeezedBody: String): List<DisasterCategory> =
            entries.filter { category ->
                category.keywords.any { it in squeezedBody }
            }

        /** 본문에 가장 잘 맞는 재난구분 하나. 아무것도 맞지 않으면 null. */
        fun best(squeezedBody: String): DisasterCategory? =
            matches(squeezedBody).minWithOrNull(
                compareBy({ it.group.priority }, { it.ordinal }),
            )
    }
}
