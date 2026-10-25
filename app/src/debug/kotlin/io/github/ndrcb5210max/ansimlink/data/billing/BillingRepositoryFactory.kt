package io.github.ndrcb5210max.ansimlink.data.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 디버그 빌드의 결제 저장소. 설정에서 '가짜 결제'를 고르면 [FakeBillingRepository] 를 쓴다.
 *
 * 같은 이름의 함수가 `app/src/release/` 에도 있고 거기서는 늘 진짜 Play 결제다.
 * 소스셋으로 나눈 이유는 그쪽 파일 주석에 적어 두었다.
 *
 * ## 왜 SharedPreferences 를 직접 쓰나
 * 앱의 다른 설정은 DataStore 에 둔다. 여기는 [AppContainer][io.github.ndrcb5210max.ansimlink.AppContainer]
 * 가 저장소를 만드는 **그 순간** 값을 알아야 해서(만든 뒤에 바꿀 수 없다) 코루틴 없이
 * 읽을 수 있어야 한다. 디버그 빌드에만 있는 스위치라 저장소 하나를 더 만들 이유가 없다.
 */
fun createBillingRepository(context: Context): BillingRepository {
    val scenario = FakeBillingSettings.scenario(context)
    return if (scenario == null) {
        PlayBillingRepository(context)
    } else {
        Log.i("Billing", "가짜 결제 저장소를 쓴다: " + scenario.label)
        FakeBillingRepository(scenario)
    }
}

/** 디버그 빌드에는 가짜 결제가 있다. 설정 화면에 메뉴를 보여 준다. */
val fakeBillingAvailable: Boolean = true

/**
 * 가짜 결제 설정.
 *
 * 고른 값은 **다음 앱 시작 때** 적용된다. [AppContainer][io.github.ndrcb5210max.ansimlink.AppContainer]
 * 가 저장소를 `by lazy` 로 한 번만 만들기 때문이다. 화면에 그렇게 적어 둔다.
 */
object FakeBillingSettings {

    private const val FILE = "fake-billing"
    private const val KEY_SCENARIO = "scenario"

    /** 고른 상황. null 이면 진짜 Play 결제를 쓴다(디버그 빌드의 기본값). */
    fun scenario(context: Context): FakeBillingScenario? {
        val name = preferences(context).getString(KEY_SCENARIO, null) ?: return null
        return FakeBillingScenario.entries.firstOrNull { it.name == name }
    }

    fun setScenario(context: Context, scenario: FakeBillingScenario?) {
        preferences(context).edit().apply {
            if (scenario == null) remove(KEY_SCENARIO) else putString(KEY_SCENARIO, scenario.name)
        }.apply()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
