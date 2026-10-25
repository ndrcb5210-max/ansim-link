package io.github.ndrcb5210max.ansimlink.ui

import io.github.ndrcb5210max.ansimlink.data.share.ProvisionPurpose
import io.github.ndrcb5210max.ansimlink.domain.alert.DisasterType
import io.github.ndrcb5210max.ansimlink.domain.message.SafetyMessages
import io.github.ndrcb5210max.ansimlink.domain.share.AutoStartStep
import io.github.ndrcb5210max.ansimlink.domain.share.ShareBlockReason
import io.github.ndrcb5210max.ansimlink.ui.share.FamilyGroupOption
import io.github.ndrcb5210max.ansimlink.ui.share.FamilyLocationUiState
import io.github.ndrcb5210max.ansimlink.ui.share.FamilyMemberItem
import io.github.ndrcb5210max.ansimlink.ui.share.ProvisionLogItem
import io.github.ndrcb5210max.ansimlink.ui.share.ProvisionLogUiState
import io.github.ndrcb5210max.ansimlink.ui.share.SafetyMessageUiState
import io.github.ndrcb5210max.ansimlink.ui.share.ShareSettingsUiState
import io.github.ndrcb5210max.ansimlink.ui.share.ShareUiState

// 4단계 스크린샷 테스트가 같이 쓰는 것들.
//
// 2·3단계와 같은 방식이다. 시각은 문자열로 미리 넣는다. 화면이 시계를 모르므로
// (ViewModel 이 만들어 준다) 그림이 기기 시간대나 찍는 시각에 따라 달라지지 않는다.
// 거리도 미리 만든 문자열이라 위치 권한이나 실제 좌표가 필요 없다.

/** 공유 중인 홈 화면. 상시 알림과 같은 내용을 보여 주는 상태다. */
internal val sharingNow = ShareUiState(
    loading = false,
    sharing = true,
    groupNames = listOf("우리 가족"),
    receiverCount = 2,
    startedText = "18:05",
    endsText = "19:05",
    remainingMinutes = 42,
    writeCount = 7,
)

/** 공유 전 홈 화면. 시작할 수 있는 상태다. */
internal val readyToShare = ShareUiState(loading = false)

/**
 * 일시중지해서 공유를 시작할 수 없는 상태.
 *
 * 이 그림을 따로 찍는 이유: '버튼을 눌렀는데 아무 일도 없었다'를 막는 설계를
 * 블로그에서 보여 주려는 것이다. 이유를 버튼 **위**에 적고 버튼을 끈다.
 */
internal val blockedByPause = ShareUiState(
    loading = false,
    blockReason = ShareBlockReason.Paused,
)

/** 자동 시작을 아직 켜지 않은 설정 화면(명시적 공개 카드가 보인다). */
internal val shareSettingsBeforeAutoStart = ShareSettingsUiState(
    shareMinutes = 60,
    maxAllowedMinutes = 60,
    autoStartEnabled = false,
    autoStartStep = AutoStartStep.Disclosure,
    locationPermissionGranted = true,
    backgroundLocationGranted = false,
    notificationsEnabled = true,
)

/** 배경 위치 권한 단계까지 온 설정 화면. */
internal val shareSettingsBackgroundStep = ShareSettingsUiState(
    shareMinutes = 60,
    maxAllowedMinutes = 60,
    autoStartEnabled = false,
    autoStartStep = AutoStartStep.BackgroundPermission,
    locationPermissionGranted = true,
    backgroundLocationGranted = false,
    notificationsEnabled = true,
)

/**
 * 가족 위치 보기.
 *
 * 세 가지 모습을 한 화면에 담았다. 위치를 받은 사람, 정확도가 나쁜 사람,
 * 아직 첫 위치가 안 올라온 사람(공유를 막 시작한 경우).
 */
internal val sampleFamilyLocations = FamilyLocationUiState(
    loading = false,
    groups = listOf(FamilyGroupOption("g1", "우리 가족")),
    selectedGroupId = "g1",
    members = listOf(
        FamilyMemberItem(
            uid = "a",
            displayName = "지은",
            updatedText = "18:32",
            accuracyText = "25",
            distanceText = "1.2km",
            endsAtText = "19:05",
            latitude = 37.5665,
            longitude = 126.9780,
        ),
        FamilyMemberItem(
            uid = "b",
            displayName = "하늘",
            updatedText = "18:28",
            accuracyText = "380",
            distanceText = "430m",
            endsAtText = "19:02",
            latitude = 37.5700,
            longitude = 126.9800,
        ),
        FamilyMemberItem(
            uid = "c",
            displayName = "민지",
            updatedText = null,
            accuracyText = null,
            distanceText = null,
            endsAtText = "19:10",
        ),
    ),
)

/**
 * 내 위치 제공 기록.
 *
 * 진행 중인 건과 끝난 건, 제공 목적 세 가지를 모두 담았다.
 * **좌표가 한 줄도 없다** — 이 기록에는 애초에 저장하지 않는다.
 */
internal val sampleProvisionLog = ProvisionLogUiState(
    loading = false,
    items = listOf(
        ProvisionLogItem(
            id = 3,
            groupName = "우리 가족",
            purpose = ProvisionPurpose.AlertPrompt,
            periodText = "10월 25일 18:05",
            ongoing = true,
            plannedEndText = "19:05",
            receiverNames = listOf("지은", "하늘"),
            writeCount = 0,
        ),
        ProvisionLogItem(
            id = 2,
            groupName = "우리 가족",
            purpose = ProvisionPurpose.ManualShare,
            periodText = "10월 24일 14:03 ~ 14:21",
            ongoing = false,
            plannedEndText = "15:03",
            receiverNames = listOf("지은", "하늘"),
            writeCount = 5,
        ),
        ProvisionLogItem(
            id = 1,
            groupName = "우리 가족",
            purpose = ProvisionPurpose.AlertAuto,
            periodText = "10월 24일 09:41 ~ 10:41",
            ongoing = false,
            plannedEndText = "10:41",
            receiverNames = listOf("지은"),
            writeCount = 18,
        ),
    ),
)

/** 안심 메시지 편집(무료 요금제). 유형별 입력란은 보이지만 꺼져 있다. */
internal val sampleMessages = SafetyMessageUiState(
    loading = false,
    common = SafetyMessages.DEFAULT,
    premium = false,
    senderName = "민수",
)

/** 안심 메시지 편집(프리미엄). 유형별 입력란이 열린다. */
internal val samplePremiumMessages = SafetyMessageUiState(
    loading = false,
    common = "재난문자 받았어. 나는 괜찮아.",
    byType = mapOf(
        DisasterType.Earthquake to "지진이야. 책상 아래 있어. 곧 연락할게.",
        DisasterType.HeavyRain to "비가 많이 와. 집에 있어. 걱정 마.",
    ),
    premium = true,
    senderName = "민수",
)
