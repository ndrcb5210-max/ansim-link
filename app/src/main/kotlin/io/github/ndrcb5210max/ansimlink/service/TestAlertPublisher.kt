package io.github.ndrcb5210max.ansimlink.service

import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.ndrcb5210max.ansimlink.BuildConfig
import io.github.ndrcb5210max.ansimlink.R
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertPackages
import io.github.ndrcb5210max.ansimlink.domain.alert.AlertSeverity

/**
 * 디버그 빌드 전용 '테스트 재난문자 보내기'.
 *
 * 앱이 재난문자 앱과 같은 모양(제목 = 문자 종류, 본문 = 재난문자 문구)의 알림을 직접 띄우고,
 * 그것을 우리 리스너가 받아 분류 → 규칙 → 기록 → 발동 알림까지 가는지 확인한다.
 * 실제 재난문자를 기다리지 않고 흐름 전체를 점검할 수 있다.
 *
 * 우리 앱 전체를 허용 목록에 넣지 않고 [AlertPackages.DEBUG_TEST_CHANNEL_ID] 채널만
 * 받아들이는 이유는 [AlertPackages] 에 적어 두었다.
 *
 * 릴리스 빌드에서는 [publish] 가 아무 일도 하지 않는다.
 */
object TestAlertPublisher {

    /** 설정 화면의 '테스트 재난문자 보내기'가 고를 수 있는 보기. */
    val samples: List<TestAlertSample> = listOf(
        TestAlertSample(
            severity = AlertSeverity.Emergency,
            // 아래 본문은 모두 실제 재난문자의 문장 '형태'를 참고해 지어낸 예시다.
            // 특정 문자를 옮긴 것이 아니므로 지역 이름은 ○○ 로 가렸다.
            body = "[기상청] 10-25 05:12 경북 ○○시 남남서쪽 19km 지역 " +
                "규모 4.2 지진발생/낙하물로부터 몸을 보호, 진동 멈춘 후 야외로 대피",
        ),
        TestAlertSample(
            severity = AlertSeverity.Extreme,
            body = "[행정안전부] 오늘 15:00 전국 민방공 대피 훈련, 경계경보 발령. " +
                "안내에 따라 가까운 대피시설로 이동해 주세요",
        ),
        TestAlertSample(
            severity = AlertSeverity.PublicSafety,
            body = "[○○시] 오늘 폭염경보 발효. 낮 시간 야외활동을 줄이고 " +
                "물을 자주 드세요. 온열질환에 주의 바랍니다",
        ),
        TestAlertSample(
            severity = AlertSeverity.Unknown,
            body = null,
            titleOverride = "새 경보",
        ),
    )

    /**
     * 테스트 알림을 띄운다.
     *
     * @return 띄웠으면 true. 릴리스 빌드이거나 알림 권한이 없으면 false.
     */
    fun publish(context: Context, sample: TestAlertSample): Boolean {
        if (!BuildConfig.DEBUG) return false
        AlertNotifications.ensureChannels(context)

        val builder = NotificationCompat
            .Builder(context, AlertPackages.DEBUG_TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_alert)
            .setContentTitle(sample.title)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        sample.body?.let { body ->
            builder.setContentText(body)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        return AlertNotifications.post(
            context,
            AlertNotifications.DEBUG_TEST_NOTIFICATION_ID,
            builder.build(),
        )
    }
}

/**
 * 테스트 알림 한 가지.
 *
 * @param titleOverride 제목을 문자 종류 이름이 아닌 값으로 바꿀 때 쓴다(묶음 알림 흉내).
 */
data class TestAlertSample(
    val severity: AlertSeverity,
    val body: String?,
    val titleOverride: String? = null,
) {
    val title: String get() = titleOverride ?: severity.label
}
