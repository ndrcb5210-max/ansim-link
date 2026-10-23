package io.github.ndrcb5210max.ansimlink.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.ndrcb5210max.ansimlink.R

/** 하단 탭 세 개. 1단계에서는 이 세 화면만 있다. */
enum class AnsimTab(
    val route: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Home(
        route = "home",
        labelRes = R.string.tab_home,
        iconRes = R.drawable.ic_tab_home,
    ),
    Group(
        route = "group",
        labelRes = R.string.tab_group,
        iconRes = R.drawable.ic_tab_group,
    ),
    Settings(
        route = "settings",
        labelRes = R.string.tab_settings,
        iconRes = R.drawable.ic_tab_settings,
    ),
}
