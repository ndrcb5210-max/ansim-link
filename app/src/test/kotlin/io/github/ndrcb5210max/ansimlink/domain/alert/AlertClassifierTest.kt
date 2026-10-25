package io.github.ndrcb5210max.ansimlink.domain.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 재난문자 분류기 테스트.
 *
 * ## 테스트 문장의 출처
 *
 * 아래 본문은 **모두 지어낸 예시**다. 실제 재난문자의 문장 *형태*(발신 기관을 대괄호로 적고,
 * 날짜·시각과 지역을 앞에 두고, 행동 요령을 뒤에 붙이는 틀)를 참고해서 썼고,
 * 특정 문자를 그대로 옮긴 것은 하나도 없다. 지역 이름은 '○○시'처럼 가렸고,
 * 실제 지명을 쓴 곳도 그 지역에 그런 문자가 실제로 발송됐다는 뜻은 아니다.
 *
 * 이렇게 한 이유: 분류기는 **낱말**로 판단하므로 테스트에 필요한 것은 문장 틀과 낱말이다.
 * 실제 문자를 옮겨 적으면 출처를 정확히 밝히기 어려워서 검증할 수 없는 주장이 된다.
 *
 * 제목 문자열은 지어낸 것이 아니다. 3단계 지시문에 적힌 AOSP CellBroadcastReceiver 의
 * 알림 제목('위급 재난문자', '긴급 재난문자', '안전 안내문자', '실종 경보문자',
 * 일반 ko 리소스의 '긴급 재난 문자' 같은 띄어쓰기 변형, 그리고 영어 제목)을 썼다.
 */
class AlertClassifierTest {

    private val cellBroadcast = "com.android.cellbroadcastreceiver"

    private fun classify(
        title: String?,
        body: String? = null,
        packageName: String = cellBroadcast,
        channelId: String? = "broadcastMessages",
    ) = AlertClassifier.classify(
        AlertNotification(
            packageName = packageName,
            title = title,
            body = body,
            channelId = channelId,
        ),
    )

    // --- 문자 종류: 제목 ---

    @Test
    fun `위급재난문자 제목을 알아본다`() {
        assertEquals(AlertSeverity.Extreme, classify("위급 재난문자", "지진 발생").severity)
    }

    @Test
    fun `긴급재난문자 제목을 알아본다`() {
        assertEquals(AlertSeverity.Emergency, classify("긴급 재난문자", "지진 발생").severity)
    }

    @Test
    fun `안전안내문자 제목을 알아본다`() {
        assertEquals(AlertSeverity.PublicSafety, classify("안전 안내문자", "폭염 주의").severity)
    }

    @Test
    fun `실종경보문자 제목을 알아본다`() {
        assertEquals(AlertSeverity.AmberAlert, classify("실종 경보문자", "실종아동 발생").severity)
    }

    @Test
    fun `띄어쓰기가 달라도 같은 종류로 본다`() {
        // 일반 ko 리소스는 '긴급 재난 문자'처럼 낱말을 더 띄어 쓴다.
        assertEquals(AlertSeverity.Emergency, classify("긴급 재난 문자", "호우경보").severity)
        assertEquals(AlertSeverity.PublicSafety, classify("안전 안내 문자", "폭염경보").severity)
        assertEquals(AlertSeverity.Extreme, classify("위급재난문자", "지진").severity)
    }

    @Test
    fun `줄바꿈이 섞인 제목도 알아본다`() {
        assertEquals(AlertSeverity.Emergency, classify("긴급\n재난문자", "지진발생").severity)
    }

    @Test
    fun `영어 제목도 알아본다`() {
        assertEquals(AlertSeverity.Extreme, classify("Extreme Emergency Alert", "지진").severity)
        assertEquals(AlertSeverity.Emergency, classify("Emergency Alert", "지진").severity)
        assertEquals(AlertSeverity.PublicSafety, classify("Public Safety Alert", "폭염").severity)
        assertEquals(AlertSeverity.AmberAlert, classify("Amber Alert", "실종").severity)
    }

    @Test
    fun `Extreme Emergency Alert 를 Emergency Alert 로 잘못 보지 않는다`() {
        // 'Extreme Emergency Alert' 안에 'Emergency Alert' 가 들어 있다. 순서가 중요하다.
        assertEquals(AlertSeverity.Extreme, classify("Extreme Emergency Alert", "지진").severity)
    }

    @Test
    fun `제목 앞뒤에 다른 말이 붙어도 알아본다`() {
        // 기기에 따라 앱 이름이나 발신 시각이 제목에 붙는다.
        assertEquals(
            AlertSeverity.Emergency,
            classify("[긴급재난문자] 10-25 05:12", "지진발생").severity,
        )
    }

    @Test
    fun `제목이 없으면 종류 미상이다`() {
        assertEquals(AlertSeverity.Unknown, classify(null, "지진발생").severity)
        assertEquals(AlertSeverity.Unknown, classify("", "지진발생").severity)
        assertEquals(AlertSeverity.Unknown, classify("   ", "지진발생").severity)
    }

    @Test
    fun `재난문자가 아닌 제목은 종류 미상이다`() {
        assertEquals(AlertSeverity.Unknown, classify("소프트웨어 업데이트", "지금 설치").severity)
    }

    // --- 묶음 알림 ---

    @Test
    fun `새 경보 묶음 알림은 종류 미상이고 묶음으로 표시된다`() {
        val result = classify("새 경보", body = null)
        assertEquals(AlertSeverity.Unknown, result.severity)
        assertEquals(DisasterType.Unknown, result.type)
        assertTrue(result.bundleSummary)
        assertEquals(AlertConfidence.Low, result.confidence)
    }

    @Test
    fun `읽지 않은 경보 N개 묶음 알림도 종류 미상이다`() {
        val result = classify("읽지 않은 경보 3개", body = null)
        assertEquals(AlertSeverity.Unknown, result.severity)
        assertTrue(result.bundleSummary)
    }

    @Test
    fun `영어 묶음 알림 제목도 알아본다`() {
        assertTrue(classify("New alerts", body = null).bundleSummary)
        assertTrue(classify("Unread alerts", body = null).bundleSummary)
    }

    @Test
    fun `본문이 있으면 묶음 알림이 아니다`() {
        assertFalse(classify("긴급 재난문자", "지진발생").bundleSummary)
    }

    // --- 재난 유형: 본문 낱말 ---

    @Test
    fun `지진 문자를 가린다`() {
        val result = classify(
            "긴급 재난문자",
            "[기상청] 10-25 05:12 경북 ○○시 남남서쪽 19km 지역 규모 4.2 지진발생/" +
                "낙하물로부터 몸을 보호, 진동이 멈춘 후 야외로 대피",
        )
        assertEquals(DisasterType.Earthquake, result.type)
        assertEquals("지진", result.category?.officialName)
        assertEquals(AlertConfidence.High, result.confidence)
    }

    @Test
    fun `여진도 지진으로 가린다`() {
        assertEquals(
            DisasterType.Earthquake,
            classify("긴급 재난문자", "[기상청] 여진이 이어질 수 있으니 안전에 주의바랍니다").type,
        )
    }

    @Test
    fun `지진해일은 지진보다 앞선다`() {
        // 지진해일 문자에는 '지진'이라는 낱말도 같이 들어 있다. 우선순위로 지진해일을 고른다.
        val result = classify(
            "위급 재난문자",
            "[기상청] 지진해일 경보 발령. 해안가 주민은 지진 발생 지역을 피해 " +
                "높은 곳으로 즉시 대피하십시오",
        )
        assertEquals(DisasterType.Tsunami, result.type)
        assertEquals("지진해일", result.category?.officialName)
    }

    @Test
    fun `민방공 경계경보를 가린다`() {
        val result = classify(
            "위급 재난문자",
            "[행정안전부] 오늘 06:32 서울특별시 지역에 경계경보 발령. " +
                "국민들은 대피할 준비를 하십시오",
        )
        assertEquals(DisasterType.CivilDefense, result.type)
        assertEquals("민방공", result.category?.officialName)
    }

    @Test
    fun `민방위 훈련 문자도 민방공으로 가린다`() {
        assertEquals(
            DisasterType.CivilDefense,
            classify("안전 안내문자", "[행정안전부] 오늘 14:00 민방위 대피 훈련을 실시합니다").type,
        )
    }

    @Test
    fun `호우 문자를 가린다`() {
        val result = classify(
            "긴급 재난문자",
            "[○○시] 오늘 08:00 호우경보 발효. 하천 근처와 저지대 주민은 안전한 곳으로 이동하세요",
        )
        assertEquals(DisasterType.HeavyRain, result.type)
        assertEquals("호우", result.category?.officialName)
    }

    @Test
    fun `홍수는 호우보다 앞선다`() {
        // 호우 문자에 '홍수경보'가 같이 적히는 경우가 있다. 우선순위로 홍수를 고른다.
        val result = classify(
            "긴급 재난문자",
            "[환경부] ○○천 홍수경보 발령. 집중호우로 수위가 빠르게 오르고 있어 " +
                "하천변 출입을 삼가 주세요",
        )
        assertEquals(DisasterType.Flood, result.type)
    }

    @Test
    fun `태풍 문자를 가린다`() {
        val result = classify(
            "긴급 재난문자",
            "[○○도] 제○호 태풍이 북상 중입니다. 외출을 줄이고 간판·창문 등 " +
                "시설물 점검에 유의하세요",
        )
        assertEquals(DisasterType.Typhoon, result.type)
        assertEquals("태풍", result.category?.officialName)
    }

    @Test
    fun `산사태 문자를 가린다`() {
        assertEquals(
            DisasterType.Landslide,
            classify("긴급 재난문자", "[산림청] ○○군 산사태 경보 발령. 산지 인근 주민은 대피하세요").type,
        )
    }

    @Test
    fun `산불 문자는 화재보다 앞선다`() {
        // 산불 문자에 '화재'라는 낱말이 같이 쓰인다. 산불이 '기타'보다 앞선다.
        val result = classify(
            "긴급 재난문자",
            "[산림청] ○○산 산불 발생. 화재가 확산되고 있어 인근 주민은 " +
                "안내에 따라 대피해 주세요",
        )
        assertEquals(DisasterType.WildFire, result.type)
        assertEquals("산불", result.category?.officialName)
    }

    @Test
    fun `대설 문자를 가린다`() {
        val result = classify(
            "안전 안내문자",
            "[○○시] 대설주의보 발효. 시간당 3cm 내외의 눈이 예상되니 대중교통을 이용해 주세요",
        )
        // '시간당'은 호우 낱말로 쓰지 않는다. 그래서 대설로 남는다.
        assertEquals(DisasterType.HeavySnow, result.type)
    }

    @Test
    fun `한파 문자를 가린다`() {
        assertEquals(
            DisasterType.ColdWave,
            classify("안전 안내문자", "[○○시] 한파주의보 발효. 수도관 동파와 저체온증에 주의하세요").type,
        )
    }

    @Test
    fun `폭염 문자를 가린다`() {
        val result = classify(
            "안전 안내문자",
            "[○○시] 오늘 폭염경보 발효. 낮 시간 야외활동을 줄이고 물을 자주 드세요. " +
                "온열질환에 주의 바랍니다",
        )
        assertEquals(DisasterType.HeatWave, result.type)
        assertEquals("폭염", result.category?.officialName)
    }

    // --- '기타'로 묶이는 유형 ---

    @Test
    fun `화재는 기타로 묶이지만 재난구분은 화재로 남는다`() {
        val result = classify(
            "긴급 재난문자",
            "[○○시] ○○동 건물 화재 발생. 인근 주민은 창문을 닫고 연기를 피해 주세요",
        )
        assertEquals(DisasterType.Other, result.type)
        assertEquals("화재", result.category?.officialName)
    }

    @Test
    fun `폭발은 화재보다 앞선다`() {
        // 둘 다 '기타' 묶음이라 선언 순서로 정한다. 폭발이 먼저 선언돼 있다.
        val result = classify(
            "위급 재난문자",
            "[○○시] ○○공장 폭발 사고로 화재가 발생했습니다. 인근 주민은 대피해 주세요",
        )
        assertEquals(DisasterType.Other, result.type)
        assertEquals("폭발", result.category?.officialName)
    }

    @Test
    fun `전염병 문자는 기타로 묶인다`() {
        val result = classify("안전 안내문자", "[○○시] 감염병 확진자가 늘고 있습니다. 손 씻기를 생활화해 주세요")
        assertEquals(DisasterType.Other, result.type)
        assertEquals("전염병", result.category?.officialName)
    }

    @Test
    fun `미세먼지 문자는 기타로 묶인다`() {
        assertEquals("미세먼지", classify("안전 안내문자", "[○○시] 초미세먼지 주의보 발령").category?.officialName)
    }

    @Test
    fun `교통통제 문자는 기타로 묶인다`() {
        assertEquals(
            "교통통제",
            classify("안전 안내문자", "[○○시] ○○대로 도로통제. 우회해 주세요").category?.officialName,
        )
    }

    @Test
    fun `실종경보문자는 재난 낱말이 없어 기타가 된다`() {
        val result = classify(
            "실종 경보문자",
            "[경찰청] ○○○(7세, 남) 10-25 14:00 ○○시 ○○동에서 실종. " +
                "보신 분은 112로 신고해 주세요",
        )
        assertEquals(AlertSeverity.AmberAlert, result.severity)
        assertEquals(DisasterType.Other, result.type)
        assertNull("아는 재난 낱말이 없다", result.category)
    }

    @Test
    fun `아는 낱말이 없는 본문은 기타가 된다`() {
        val result = classify("긴급 재난문자", "[○○시] 자세한 내용은 시청 누리집을 확인해 주세요")
        assertEquals(DisasterType.Other, result.type)
        assertEquals(AlertConfidence.High, result.confidence)
    }

    // --- 본문이 없거나 가려진 경우 ---

    @Test
    fun `제목은 맞고 본문이 없으면 유형 미상이고 신뢰도는 보통이다`() {
        // Android 15 부터 OTP 로 판단된 알림은 내용이 가려져 올 수 있다.
        // 내용이 가려지면 본문이 비어 보이므로 이 경로로 들어온다.
        val result = classify("긴급 재난문자", body = null)
        assertEquals(AlertSeverity.Emergency, result.severity)
        assertEquals(DisasterType.Unknown, result.type)
        assertEquals(AlertConfidence.Medium, result.confidence)
        assertNull(result.category)
    }

    @Test
    fun `공백만 있는 본문도 유형 미상이다`() {
        assertEquals(DisasterType.Unknown, classify("긴급 재난문자", "   \n  ").type)
    }

    @Test
    fun `유형 미상은 규칙을 찾을 때 기타 칸을 본다`() {
        assertEquals(DisasterType.Other, classify("긴급 재난문자", body = null).type.ruleType)
    }

    // --- 신뢰도 ---

    @Test
    fun `제목과 유형을 다 가리면 신뢰도가 높다`() {
        assertEquals(AlertConfidence.High, classify("긴급 재난문자", "지진발생").confidence)
    }

    @Test
    fun `제목을 못 가리면 신뢰도가 낮다`() {
        assertEquals(AlertConfidence.Low, classify("무슨 알림", "지진발생").confidence)
    }

    // --- 본문 앞 60자와 지문 ---

    @Test
    fun `본문은 앞 60자까지만 남긴다`() {
        val body = "가".repeat(200)
        val preview = classify("긴급 재난문자", body).bodyPreview
        assertEquals(AlertText.PREVIEW_LENGTH + 1, preview.length) // 60자 + 생략 기호
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun `짧은 본문은 그대로 남기고 줄바꿈만 정리한다`() {
        assertEquals(
            "[○○시] 호우경보 발효",
            classify("긴급 재난문자", "[○○시]\n호우경보   발효").bodyPreview,
        )
    }

    @Test
    fun `띄어쓰기만 다른 본문은 같은 지문이 된다`() {
        assertEquals(
            classify("긴급 재난문자", "[○○시] 호우경보 발효").bodyFingerprint,
            classify("긴급 재난문자", "[○○시]호우경보발효").bodyFingerprint,
        )
    }

    @Test
    fun `내용이 다른 본문은 다른 지문이 된다`() {
        assertNotEquals(
            classify("긴급 재난문자", "[○○시] 호우경보 발효").bodyFingerprint,
            classify("긴급 재난문자", "[○○시] 대설경보 발효").bodyFingerprint,
        )
    }

    @Test
    fun `본문이 없으면 지문이 empty 다`() {
        assertEquals(AlertText.EMPTY_FINGERPRINT, classify("긴급 재난문자", null).bodyFingerprint)
    }

    @Test
    fun `채널 ID 는 그대로 기록한다`() {
        assertEquals(
            "broadcastMessages",
            classify("긴급 재난문자", "지진발생", channelId = "broadcastMessages").channelId,
        )
    }

    // --- 35종 어휘와 12개 묶음 ---

    @Test
    fun `재난구분 35종이 모두 있다`() {
        assertEquals(35, DisasterCategory.entries.size)
    }

    @Test
    fun `설정 화면에 보여 주는 유형은 12개다`() {
        assertEquals(12, DisasterType.selectableEntries.size)
    }

    @Test
    fun `35종의 묶음은 모두 설정 화면에 있는 유형이다`() {
        DisasterCategory.entries.forEach { category ->
            assertTrue(
                category.officialName + " 의 묶음이 설정 화면에 없다",
                category.group.selectable,
            )
        }
    }

    @Test
    fun `12개 유형에는 1대1로 대응하는 재난구분이 있다`() {
        // '기타'를 뺀 11개는 같은 이름의 재난구분이 있어야 한다.
        DisasterType.selectableEntries
            .filterNot { it == DisasterType.Other }
            .forEach { type ->
                assertTrue(
                    type.label + " 에 대응하는 재난구분이 없다",
                    DisasterCategory.entries.any {
                        it.group == type && it.officialName == type.label
                    },
                )
            }
    }

    @Test
    fun `우선순위는 지시문에 적힌 순서다`() {
        assertEquals(
            listOf(
                "지진해일", "지진", "민방공", "산사태", "홍수", "호우",
                "태풍", "산불", "대설", "한파", "폭염", "기타",
            ),
            DisasterType.selectableEntries.map { it.label },
        )
    }

    @Test
    fun `저장 키는 서로 겹치지 않는다`() {
        assertEquals(
            DisasterType.entries.size,
            DisasterType.entries.map { it.key }.toSet().size,
        )
        assertEquals(
            AlertSeverity.entries.size,
            AlertSeverity.entries.map { it.key }.toSet().size,
        )
    }

    // --- 허용 목록 ---

    @Test
    fun `알려진 재난문자 앱만 허용 목록에 있다`() {
        assertTrue(AlertPackages.isKnownAlertPackage("com.android.cellbroadcastreceiver"))
        assertTrue(AlertPackages.isKnownAlertPackage("com.google.android.cellbroadcastreceiver"))
        assertTrue(AlertPackages.isKnownAlertPackage("com.android.cellbroadcastreceiver.module"))
        assertFalse(AlertPackages.isKnownAlertPackage("com.example.anything"))
        assertFalse(AlertPackages.isKnownAlertPackage("io.github.ndrcb5210max.ansimlink"))
    }

    @Test
    fun `디버그 테스트 알림은 정해진 채널로 올 때만 받아들인다`() {
        val self = "io.github.ndrcb5210max.ansimlink"

        assertTrue(
            "디버그 빌드 + 테스트 채널",
            AlertPackages.shouldInspect(
                packageName = self,
                channelId = AlertPackages.DEBUG_TEST_CHANNEL_ID,
                selfPackageName = self,
                allowSelf = true,
            ),
        )
        assertFalse(
            "우리 앱의 다른 알림(발동 알림)은 다시 읽지 않는다",
            AlertPackages.shouldInspect(
                packageName = self,
                channelId = "disaster_trigger",
                selfPackageName = self,
                allowSelf = true,
            ),
        )
        assertFalse(
            "릴리스 빌드에서는 우리 앱 알림을 아예 보지 않는다",
            AlertPackages.shouldInspect(
                packageName = self,
                channelId = AlertPackages.DEBUG_TEST_CHANNEL_ID,
                selfPackageName = self,
                allowSelf = false,
            ),
        )
        assertFalse(
            "허용 목록 밖 앱",
            AlertPackages.shouldInspect(
                packageName = "com.example.anything",
                channelId = AlertPackages.DEBUG_TEST_CHANNEL_ID,
                selfPackageName = self,
                allowSelf = true,
            ),
        )
    }
}
