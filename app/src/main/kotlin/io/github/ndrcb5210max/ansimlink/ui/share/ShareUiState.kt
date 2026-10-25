package io.github.ndrcb5210max.ansimlink.ui.share

import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.share.AutoStartStep
import io.github.ndrcb5210max.ansimlink.domain.share.ShareBlockReason

/**
 * 4단계 화면들의 상태.
 *
 * 2·3단계와 같은 방식이다. 화면은 상태와 콜백만 받고 시계·권한·저장소를 모르므로,
 * Firebase 없이 미리보기와 스크린샷을 찍을 수 있고 그림이 찍는 시각에 따라 달라지지 않는다.
 * 시각 문자열은 모두 ViewModel 이 미리 만들어 넘긴다.
 */

/**
 * 홈 화면의 공유 상태(배너).
 *
 * @param blockReason 지금 공유를 시작할 수 없는 이유. null 이면 시작할 수 있다.
 * @param startedText "18:05" 처럼 미리 만든 문자열.
 */
data class ShareUiState(
    val loading: Boolean = true,
    val sharing: Boolean = false,
    val groupNames: List<String> = emptyList(),
    val receiverCount: Int = 0,
    val startedText: String = "",
    val endsText: String = "",
    val remainingMinutes: Int = 0,
    val writeCount: Int = 0,
    val blockReason: ShareBlockReason? = null,
) {
    val canStart: Boolean get() = !sharing && blockReason == null

    val groupLabel: String get() = groupNames.joinToString(", ")
}

/**
 * 위치 공유 설정 화면(공유 시간 + 자동 시작).
 *
 * @param maxAllowedMinutes 이 요금제에서 고를 수 있는 최대값. 그보다 긴 칩은 꺼진 채로
 *   **같이 보여 준다**(프리미엄에 무엇이 있는지 숨기지 않는다).
 * @param premium 프리미엄인지. 꺼진 칩 아래에 '구독하면 180분까지' 안내를 띄울지
 *   정한다. 이미 프리미엄인 사람에게 그 안내를 보여 주면 안 된다.
 */
data class ShareSettingsUiState(
    val shareMinutes: Int = 60,
    val maxAllowedMinutes: Int = 60,
    val premium: Boolean = false,
    val autoStartEnabled: Boolean = false,
    val autoStartStep: AutoStartStep = AutoStartStep.Disclosure,
    val locationPermissionGranted: Boolean = false,
    val backgroundLocationGranted: Boolean = false,
    val notificationsEnabled: Boolean = false,
)

/** 가족 위치 보기 화면. */
data class FamilyLocationUiState(
    val loading: Boolean = true,
    val groups: List<FamilyGroupOption> = emptyList(),
    val selectedGroupId: String? = null,
    val members: List<FamilyMemberItem> = emptyList(),
    /** 내 위치를 몰라 거리를 셀 수 없는 상태. 화면에 그 사실을 적는다. */
    val myLocationUnknown: Boolean = false,
)

data class FamilyGroupOption(val id: String, val name: String)

/**
 * 가족 위치 한 줄.
 *
 * 지도 SDK 를 쓰지 않는다(API 키가 필요하고 커밋할 수 없다). 대신 시각·정확도·거리를
 * 글로 보여 주고 [지도 앱으로 열기]로 `geo:` 인텐트를 띄운다.
 *
 * @param distanceText "1.2km" 처럼 미리 만든 문자열. 내 위치를 모르면 null.
 * @param latitude 지도 앱을 열 때 쓴다. 위치를 아직 못 받았으면 null.
 */
data class FamilyMemberItem(
    val uid: String,
    val displayName: String,
    val updatedText: String?,
    val accuracyText: String?,
    val distanceText: String?,
    val endsAtText: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}

/** 내 위치 제공 기록 화면. */
data class ProvisionLogUiState(
    val loading: Boolean = true,
    val items: List<ProvisionLogItem> = emptyList(),
)

/**
 * 제공 기록 한 줄.
 *
 * @param periodText "10월 25일 18:05 ~ 19:05" 처럼 미리 만든 문자열.
 * @param plannedEndText 진행 중일 때 보여 줄 예정 종료 시각.
 */
data class ProvisionLogItem(
    val id: Long,
    val groupName: String,
    val purpose: ProvisionPurpose,
    val periodText: String,
    val ongoing: Boolean,
    val plannedEndText: String,
    val receiverNames: List<String>,
    val writeCount: Int,
)

/** 안심 메시지 편집 화면. */
data class SafetyMessageUiState(
    val loading: Boolean = true,
    val common: String = "",
    val byType: Map<DisasterType, String> = emptyMap(),
    /** 프리미엄이면 재난 유형별 입력란이 열린다. 서버가 검증한 구독 상태에서 나온다. */
    val premium: Boolean = false,
    /** 미리보기에 쓸 내 이름("엄마: 재난문자 받았어…"). */
    val senderName: String = "",
)
