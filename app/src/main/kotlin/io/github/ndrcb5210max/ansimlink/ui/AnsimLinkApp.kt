package io.github.ndrcb5210max.ansimlink.ui

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.ndrcb5210max.ansimlink.AppContainer
import io.github.ndrcb5210max.ansimlink.domain.consent.LegalDocument
import io.github.ndrcb5210max.ansimlink.ui.auth.AuthActions
import io.github.ndrcb5210max.ansimlink.ui.auth.AuthScreen
import io.github.ndrcb5210max.ansimlink.ui.auth.AuthViewModel
import io.github.ndrcb5210max.ansimlink.ui.group.CreateGroupScreen
import io.github.ndrcb5210max.ansimlink.ui.group.GroupActions
import io.github.ndrcb5210max.ansimlink.ui.group.GroupScreen
import io.github.ndrcb5210max.ansimlink.ui.group.GroupViewModel
import io.github.ndrcb5210max.ansimlink.ui.group.JoinGroupScreen
import io.github.ndrcb5210max.ansimlink.ui.home.HomeScreen
import io.github.ndrcb5210max.ansimlink.ui.legal.LegalDocumentScreen
import io.github.ndrcb5210max.ansimlink.ui.navigation.AnsimTab
import io.github.ndrcb5210max.ansimlink.ui.navigation.Routes
import io.github.ndrcb5210max.ansimlink.ui.settings.AccountScreen
import io.github.ndrcb5210max.ansimlink.ui.settings.AccountViewModel
import io.github.ndrcb5210max.ansimlink.ui.settings.DeleteAccountScreen
import io.github.ndrcb5210max.ansimlink.ui.settings.SettingsScreen

/** Google Play 구독 관리 화면. 계정 삭제 전 안내에서 연다(결제 코드는 5단계). */
private const val PLAY_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

/**
 * 앱의 가장 바깥. 로그인하지 않았으면 로그인·가입 화면을, 로그인했으면 탭 화면을 보여 준다.
 *
 * Firebase 설정이 없거나 가짜여도 죽지 않는다. 그때는 로그인 화면에 '서버 설정이 필요합니다'
 * 안내가 뜨고 전송 버튼이 꺼진다.
 */
@Composable
fun AnsimLinkApp(container: AppContainer, modifier: Modifier = Modifier) {
    val serverReady = container.isServerReady

    val signedInUser by if (serverReady) {
        container.authRepository.currentUser.collectAsStateWithLifecycle(
            initialValue = container.authRepository.currentUserNow(),
        )
    } else {
        // 설정이 없으면 Firebase 를 아예 건드리지 않는다.
        androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(null)
        }
    }

    if (signedInUser == null) {
        AuthFlow(container = container, modifier = modifier)
    } else {
        MainShell(container = container, modifier = modifier)
    }
}

/** 로그인 전 화면(로그인·가입 + 약관 보기). */
@Composable
private fun AuthFlow(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = viewModel {
        AuthViewModel(
            authRepository = container.authRepository,
            googleSignInClient = container.googleSignInClient,
            serverReady = container.isServerReady,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Routes.AUTH,
        modifier = modifier,
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                state = state,
                actions = AuthActions(
                    onModeChange = viewModel::setMode,
                    onEmailChange = viewModel::setEmail,
                    onPasswordChange = viewModel::setPassword,
                    onPasswordConfirmationChange = viewModel::setPasswordConfirmation,
                    onDisplayNameChange = viewModel::setDisplayName,
                    onToggleConsent = viewModel::toggleConsent,
                    onToggleAllConsents = viewModel::toggleAllConsents,
                    onOpenLegalDocument = { navController.navigate(Routes.legal(it)) },
                    onSubmit = viewModel::submit,
                    onGoogleSignIn = { viewModel.signInWithGoogle(activity ?: context) },
                    onForgotPassword = viewModel::sendPasswordReset,
                ),
            )
        }
        legalDestination(navController)
    }
}

/** 로그인 뒤 화면. 하단 탭과 그 위에 올라오는 화면들. */
@Composable
private fun MainShell(container: AppContainer, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val groupViewModel: GroupViewModel = viewModel {
        GroupViewModel(
            authRepository = container.authRepository,
            groupRepository = container.groupRepository,
            serverReady = container.isServerReady,
        )
    }
    val accountViewModel: AccountViewModel = viewModel {
        AccountViewModel(
            authRepository = container.authRepository,
            accountDeleter = container.accountDeleter,
            googleSignInClient = container.googleSignInClient,
            serverReady = container.isServerReady,
        )
    }

    AnsimTabScaffold(
        // 최상위 경로가 아니면(그룹 만들기, 계정 삭제 등) 하단 탭을 숨긴다.
        selectedTab = AnsimTab.entries.firstOrNull { it.route == currentRoute },
        onSelectTab = { tab ->
            navController.navigate(tab.route) {
                // 탭을 눌러도 백스택이 쌓이지 않게 한다.
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AnsimTab.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AnsimTab.Home.route) { HomeScreen() }

            composable(AnsimTab.Group.route) {
                val state by groupViewModel.state.collectAsStateWithLifecycle()
                GroupScreen(
                    state = state,
                    actions = GroupActions(
                        onCreateGroup = {
                            groupViewModel.resetCreateState()
                            navController.navigate(Routes.GROUP_CREATE)
                        },
                        onJoinGroup = {
                            groupViewModel.resetJoinState()
                            navController.navigate(Routes.GROUP_JOIN)
                        },
                        onCreateInvite = groupViewModel::createInvite,
                        onSetPaused = groupViewModel::setPaused,
                        onWithdraw = groupViewModel::withdrawConsent,
                        onDismissInvite = groupViewModel::dismissInvite,
                    ),
                )
            }

            composable(Routes.GROUP_CREATE) {
                val state by groupViewModel.createState.collectAsStateWithLifecycle()
                LaunchedEffect(state.createdGroupId) {
                    if (state.createdGroupId != null) navController.popBackStack()
                }
                CreateGroupScreen(
                    state = state,
                    onNameChange = groupViewModel::setNewGroupName,
                    onSubmit = groupViewModel::createGroup,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.GROUP_JOIN) {
                val state by groupViewModel.joinState.collectAsStateWithLifecycle()
                LaunchedEffect(state.joined) {
                    if (state.joined) navController.popBackStack()
                }
                JoinGroupScreen(
                    state = state,
                    onCodeChange = groupViewModel::setInviteCode,
                    onLookup = groupViewModel::lookupInvite,
                    onConsentChange = groupViewModel::setJoinConsent,
                    onJoin = groupViewModel::joinGroup,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(AnsimTab.Settings.route) {
                SettingsScreen(
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    onOpenLegalDocument = { navController.navigate(Routes.legal(it)) },
                )
            }

            composable(Routes.ACCOUNT) {
                val state by accountViewModel.account.collectAsStateWithLifecycle()
                AccountScreen(
                    state = state,
                    onResendVerification = accountViewModel::resendVerification,
                    onSignOut = accountViewModel::signOut,
                    onDeleteAccount = { navController.navigate(Routes.ACCOUNT_DELETE) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ACCOUNT_DELETE) {
                val state by accountViewModel.delete.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val activity = LocalActivity.current
                LaunchedEffect(Unit) { accountViewModel.loadDeletionPlan() }
                DeleteAccountScreen(
                    state = state,
                    onPasswordChange = accountViewModel::setPassword,
                    onReauthenticate = {
                        accountViewModel.reauthenticate(activity ?: context)
                    },
                    onChoiceChange = accountViewModel::setChoice,
                    onOpenSubscriptions = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, PLAY_SUBSCRIPTIONS_URL.toUri()),
                        )
                    },
                    onDelete = accountViewModel::deleteAccount,
                    onBack = { navController.popBackStack() },
                )
            }

            legalDestination(navController)
        }
    }
}

/**
 * 하단 탭이 달린 바깥 틀.
 *
 * 내비게이션을 모르는 순수한 화면이라 스크린샷 테스트에서도 그대로 쓴다.
 *
 * @param selectedTab 지금 선택된 탭. null 이면 하단 탭을 숨긴다(탭 위에 올라온 화면).
 */
@Composable
fun AnsimTabScaffold(
    selectedTab: AnsimTab?,
    onSelectTab: (AnsimTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (selectedTab != null) {
                NavigationBar {
                    AnsimTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { if (selectedTab != tab) onSelectTab(tab) },
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
            }
        },
        content = content,
    )
}

/** 약관 보기 화면. 로그인 전·후 양쪽 그래프에 같이 넣는다. */
private fun androidx.navigation.NavGraphBuilder.legalDestination(navController: NavHostController) {
    composable(Routes.LEGAL_PATTERN) { entry ->
        val key = entry.arguments?.getString(Routes.LEGAL_ARG)
        val document = key?.let { LegalDocument.fromRouteKey(it) }
        if (document == null) {
            // 알 수 없는 경로로 들어오면 조용히 돌아간다.
            LaunchedEffect(Unit) { navController.popBackStack() }
        } else {
            LegalDocumentScreen(
                document = document,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
