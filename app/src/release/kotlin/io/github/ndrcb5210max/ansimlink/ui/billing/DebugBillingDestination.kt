package io.github.ndrcb5210max.ansimlink.ui.billing

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * 릴리스 빌드에는 '가짜 결제' 화면이 없다. 그래프에 아무것도 넣지 않는다.
 *
 * 디버그 쪽 같은 이름의 함수 주석에 왜 소스셋으로 나눴는지 적어 두었다.
 * 경로(`Routes.DEBUG_FAKE_BILLING`)로 들어오려 해도 목적지가 없어서 아무 일도
 * 일어나지 않는다. 설정 화면에도 그 항목이 보이지 않는다.
 */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.debugBillingDestination(navController: NavHostController) = Unit
