package io.github.ndrcb5210max.ansimlink.ui.billing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.ndrcb5210max.ansimlink.data.billing.FakeBillingSettings
import io.github.ndrcb5210max.ansimlink.ui.navigation.Routes

/**
 * 디버그 빌드의 '가짜 결제' 화면을 내비게이션 그래프에 넣는다.
 *
 * 같은 이름의 함수가 `app/src/release/` 에도 있고 거기서는 **아무것도 넣지 않는다.**
 * 3단계의 디버그 화면들은 그래프에 늘 넣고 설정 화면에서 길만 숨겼는데
 * (`BuildConfig.DEBUG` 가 상수라 R8 이 지워 준다), 여기서는 화면 자체가 디버그
 * 소스셋에 있으므로 그 방법을 쓸 수 없다. 소스셋으로 나누는 쪽이 보장도 더 확실하다.
 */
fun NavGraphBuilder.debugBillingDestination(navController: NavHostController) {
    composable(Routes.DEBUG_FAKE_BILLING) {
        val context = LocalContext.current
        var selected by remember { mutableStateOf(FakeBillingSettings.scenario(context)) }
        FakeBillingScreen(
            selected = selected,
            onSelect = { scenario ->
                FakeBillingSettings.setScenario(context, scenario)
                selected = scenario
            },
            onBack = { navController.popBackStack() },
        )
    }
}
