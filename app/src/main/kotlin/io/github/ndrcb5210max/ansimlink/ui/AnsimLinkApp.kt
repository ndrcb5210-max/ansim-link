package io.github.ndrcb5210max.ansimlink.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.ndrcb5210max.ansimlink.ui.group.GroupScreen
import io.github.ndrcb5210max.ansimlink.ui.home.HomeScreen
import io.github.ndrcb5210max.ansimlink.ui.navigation.AnsimTab
import io.github.ndrcb5210max.ansimlink.ui.settings.SettingsScreen

/**
 * 앱 전체 틀. 하단 탭으로 홈·그룹·설정을 오간다.
 *
 * @param startTab 처음 보여 줄 탭. 스크린샷 테스트에서 탭별 화면을 찍을 때 쓴다.
 */
@Composable
fun AnsimLinkApp(
    modifier: Modifier = Modifier,
    startTab: AnsimTab = AnsimTab.Home,
) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route ?: startTab.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                AnsimTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    // 탭을 눌러도 백스택이 쌓이지 않게 한다.
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startTab.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AnsimTab.Home.route) { HomeScreen() }
            composable(AnsimTab.Group.route) { GroupScreen() }
            composable(AnsimTab.Settings.route) { SettingsScreen() }
        }
    }
}
