package io.github.ndrcb5210max.ansimlink.data.billing

import android.content.Context

/**
 * 릴리스 빌드의 결제 저장소. **늘 진짜 Play 결제다.**
 *
 * 같은 이름의 함수가 `app/src/debug/` 에도 있고 거기서는 가짜 저장소를 돌려줄 수 있다.
 * 소스셋으로 나눈 이유: `BuildConfig.DEBUG` 로 가르면 가짜 구현의 **코드가 릴리스 APK
 * 안에 들어간다.** R8 이 지워 줄 것으로 기대할 수는 있지만 기대일 뿐이고, 결제처럼
 * '없어야 하는 것이 있으면 바로 사고'인 곳에서는 기대가 아니라 보장이 필요하다.
 * 소스셋에 없는 코드는 컴파일 대상이 아니므로 들어갈 길이 없다.
 */
fun createBillingRepository(context: Context): BillingRepository =
    PlayBillingRepository(context)
