package com.doomscrollduel.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.doomscrollduel.core.common.shareText
import com.doomscrollduel.core.designsystem.components.DuelTab
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.FakeData
import com.doomscrollduel.feature.broadcast.BroadcastRoomRoute
import com.doomscrollduel.feature.broadcast.BroadcastRoute
import com.doomscrollduel.feature.duel.create.NewDuelRoute
import com.doomscrollduel.feature.duel.live.LiveDuelRoute
import com.doomscrollduel.feature.duel.live.LiveDuelViewModel
import com.doomscrollduel.feature.duel.result.ResultRoute
import com.doomscrollduel.feature.battery.BatteryGuideRoute
import com.doomscrollduel.feature.home.HomeRoute
import com.doomscrollduel.domain.analytics.AnalyticsEvent
import com.doomscrollduel.domain.analytics.AnalyticsMode
import com.doomscrollduel.domain.analytics.DuelResultToken
import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.domain.usecase.DuelOutcome
import com.doomscrollduel.feature.common.AnalyticsViewModel
import com.doomscrollduel.feature.account.DeleteAccountRoute
import com.doomscrollduel.feature.legal.DisclosureRoute
import com.doomscrollduel.feature.legal.LegalDoc
import com.doomscrollduel.feature.legal.LegalScreen
import com.doomscrollduel.feature.modes.BattleHeader
import com.doomscrollduel.feature.modes.BattleModesScreen
import com.doomscrollduel.feature.home.HomeViewModel
import com.doomscrollduel.feature.modes.requiredFeature
import com.doomscrollduel.feature.modes.InviteTarget
import com.doomscrollduel.feature.paywall.PaywallRoute
import com.doomscrollduel.feature.paywall.PaywallViewModel
import com.doomscrollduel.feature.paywall.ProAccessViewModel
import com.doomscrollduel.feature.settings.FocusHoursRoute
import com.doomscrollduel.feature.progress.ProgressRoute
import com.doomscrollduel.feature.auth.LoginRoute
import com.doomscrollduel.feature.auth.SessionViewModel
import com.doomscrollduel.feature.auth.UsernameRoute
import com.doomscrollduel.feature.onboarding.OnboardingRoute
import com.doomscrollduel.feature.friends.FriendsRoute
import com.doomscrollduel.feature.profile.ProfileRoute
import com.doomscrollduel.feature.friends.InviteAcceptRoute
import com.doomscrollduel.feature.friends.InviteAcceptViewModel
import com.doomscrollduel.domain.social.ProfileState
import androidx.navigation.navDeepLink
import com.doomscrollduel.R
import com.doomscrollduel.feature.settings.SettingsRoute

object Routes {
    const val HOME = "home"
    const val MODES = "modes"
    const val NEW_DUEL = "new_duel"
    const val BROADCAST = "broadcast?join={join}"
    const val BROADCAST_ROOM = "broadcast/room"
    const val LIVE = "live/{duelId}"
    const val RESULT = "result/{duelId}"
    const val SETTINGS = "settings"
    const val PROGRESS = "progress"
    const val LOGIN = "login"
    const val USERNAME = "username"
    const val ONBOARDING = "onboarding"
    const val FRIENDS = "friends"
    const val PROFILE = "profile"
    const val INVITE_ACCEPT = "invite/{code}"

    fun inviteAccept(code: String): String = "invite/$code"
    const val BATTERY_GUIDE = "battery_guide"
    const val FOCUS_HOURS = "focus_hours"
    const val PAYWALL = "paywall?feature={feature}"
    const val DISCLOSURE = "disclosure?review={review}"
    const val DELETE_ACCOUNT = "delete_account"
    const val LEGAL = "legal/{doc}"

    /** The Broadcast page; with a room id it joins that room as soon as it opens (from an invitation). */
    fun broadcast(join: String? = null): String = if (join == null) "broadcast" else "broadcast?join=$join"

    fun live(duelId: String): String = "live/$duelId"

    fun result(duelId: String): String = "result/$duelId"

    fun legal(doc: LegalDoc): String = "legal/${doc.key}"

    /** The Accessibility disclosure. [review] re-reads it from Settings without asking for anything. */
    fun disclosure(review: Boolean = false): String = "disclosure?review=$review"

    /** The paywall, optionally told which locked thing the person came from so it can say why. */
    fun paywall(feature: ProFeature? = null): String = if (feature == null) "paywall" else "paywall?feature=${feature.name}"
}

private const val FadeMillis = 180

/**
 * The app's navigation.
 *
 * Tabs (bottom bar): HOME, MODES ("Battles"), SETTINGS. BATTERY_GUIDE opens from the Home banner and Settings;
 * FOCUS_HOURS opens from Settings. DISCLOSURE (the Accessibility disclosure) opens from every "turn on counting" button until
 * the person has agreed, and from Settings to re-read; DELETE_ACCOUNT opens from Settings. PAYWALL opens from the soft upgrade prompts on Battles and Settings.
 * Duel flow: HOME or MODES -> NEW_DUEL -> LIVE -> RESULT, and RESULT -> NEW_DUEL for a rematch.
 * Back stack rules: every step of the duel flow clears back to HOME first, so a duel in progress or a
 * finished result is never on the back stack behind another screen. Back from LIVE or RESULT goes Home;
 * back from NEW_DUEL goes to where the user came from.
 */
@Composable
fun DuelNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    /** Intents that arrive while the app is already open (an invite link, a tapped notification). */
    newIntents: kotlinx.coroutines.flow.Flow<android.content.Intent> = kotlinx.coroutines.flow.emptyFlow(),
) {
    val reducedMotion = DuelTheme.motion.reduced
    val session: SessionViewModel = hiltViewModel()
    val profileState by session.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val startRoute = remember {
        when {
            session.startsAtLogin() -> Routes.LOGIN
            session.startsAtOnboarding() -> Routes.ONBOARDING
            else -> Routes.HOME
        }
    }
    val inviteHost = remember { context.getString(R.string.invite_web_host) }

    LaunchedEffect(newIntents) { newIntents.collect { navController.handleDeepLink(it) } }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = when (backStackEntry?.destination?.route) {
        Routes.HOME -> DuelTab.HOME
        Routes.MODES -> DuelTab.BATTLES
        Routes.PROGRESS -> DuelTab.PROGRESS
        Routes.PROFILE -> DuelTab.PROFILE
        else -> null
    }

    // Sign-in decides where the person goes next: choose a username first, then on to what they came for.
    val route = backStackEntry?.destination?.route
    LaunchedEffect(profileState, route) {
        fun leave(from: String) {
            val pending = session.prefs.pendingInvite
            when {
                pending != null -> navController.navigate(Routes.inviteAccept(pending)) { popUpTo(from) { inclusive = true } }
                navController.previousBackStackEntry != null -> navController.popBackStack()
                else -> navController.navigate(Routes.HOME) { popUpTo(from) { inclusive = true } }
            }
        }
        fun startOnboarding(from: String) {
            navController.navigate(Routes.ONBOARDING) { popUpTo(from) { inclusive = true }; launchSingleTop = true }
        }
        when (profileState) {
            is ProfileState.Ready -> when (route) {
                Routes.LOGIN, Routes.USERNAME ->
                    if (session.prefs.onboardingDone) leave(route) else startOnboarding(route)
                Routes.HOME ->
                    if (!session.prefs.onboardingDone) startOnboarding(route)
                    else session.prefs.pendingInvite?.let { navController.navigate(Routes.inviteAccept(it)) }
            }
            // The app cannot be used without an account: signed out (first start, or after Logout) always means the login
            // page, with nothing behind it. Only the login page itself, the legal pages it links to and an invite link
            // (which sends the person to login by itself and remembers the code) may be shown while signed out.
            ProfileState.SignedOut -> if (route != null && route != Routes.LOGIN && route != Routes.LEGAL && route != Routes.INVITE_ACCEPT) {
                navController.navigate(Routes.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
            // Signed in, but the server is not ready: do not keep the person on the login page.
            ProfileState.Unavailable -> if (route == Routes.LOGIN) {
                if (session.prefs.onboardingDone) leave(Routes.LOGIN) else startOnboarding(Routes.LOGIN)
            }
            // A new person: the first-run pages start with the username; later visits to this state use the plain username page.
            ProfileState.NeedsUsername -> if (route == Routes.LOGIN || route == Routes.HOME) {
                if (!session.prefs.onboardingDone) {
                    startOnboarding(route)
                } else {
                    navController.navigate(Routes.USERNAME) { if (route == Routes.LOGIN) popUpTo(Routes.LOGIN) { inclusive = true } }
                }
            }
            else -> Unit
        }
    }

    fun openTab(tab: DuelTab) {
        val route = when (tab) {
            DuelTab.HOME -> Routes.HOME
            DuelTab.BATTLES -> Routes.MODES
            DuelTab.PROGRESS -> Routes.PROGRESS
            DuelTab.PROFILE -> Routes.PROFILE
        }
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Column(modifier = modifier.fillMaxSize().background(DuelTheme.colors.background)) {
        // The tab bar pads for the navigation bar itself, so the screens above it must not.
        val contentInsets = if (currentTab != null) {
            Modifier.consumeWindowInsets(WindowInsets.navigationBars)
        } else {
            Modifier
        }
        Box(Modifier.weight(1f).then(contentInsets)) {
            NavHost(
                navController = navController,
                startDestination = startRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { if (reducedMotion) EnterTransition.None else fadeIn(tween(FadeMillis)) },
                exitTransition = { if (reducedMotion) ExitTransition.None else fadeOut(tween(FadeMillis)) },
                popEnterTransition = { if (reducedMotion) EnterTransition.None else fadeIn(tween(FadeMillis)) },
                popExitTransition = { if (reducedMotion) ExitTransition.None else fadeOut(tween(FadeMillis)) },
            ) {
                composable(Routes.LOGIN) {
                    LoginRoute(
                        onOpenPrivacy = { navController.navigate(Routes.legal(LegalDoc.PRIVACY)) },
                        onOpenTerms = { navController.navigate(Routes.legal(LegalDoc.TERMS)) },
                    )
                }
                composable(Routes.USERNAME) { UsernameRoute() }
                composable(Routes.ONBOARDING) {
                    OnboardingRoute(
                        onOpenDisclosure = { navController.navigate(Routes.disclosure()) },
                        onOpenBatteryGuide = { navController.navigate(Routes.BATTERY_GUIDE) },
                        onFinished = { pending ->
                            if (pending != null) {
                                navController.navigate(Routes.inviteAccept(pending)) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                            } else {
                                navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                            }
                        },
                    )
                }
                composable(Routes.PROFILE) {
                    ProfileRoute(
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Routes.LOGIN) { launchSingleTop = true } },
                        onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onChooseUsername = { navController.navigate(Routes.USERNAME) { launchSingleTop = true } },
                        onLoggedOut = { /* the sign-in gate above opens the login page */ },
                    )
                }
                composable(Routes.FRIENDS, deepLinks = listOf(navDeepLink { uriPattern = "doomscrollduel://friends" })) {
                    FriendsRoute(
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Routes.LOGIN) { launchSingleTop = true } },
                        onChooseUsername = { navController.navigate(Routes.USERNAME) { launchSingleTop = true } },
                        onOpenDuel = { duelId -> navController.navigate(Routes.live(duelId)) },
                        onJoinBroadcast = { roomId -> navController.navigate(Routes.broadcast(roomId)) },
                    )
                }
                composable(
                    route = Routes.INVITE_ACCEPT,
                    arguments = listOf(navArgument(InviteAcceptViewModel.ARG) { type = NavType.StringType }),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "doomscrollduel://invite/{code}" },
                        navDeepLink { uriPattern = "https://$inviteHost/invite/{code}" },
                    ),
                ) {
                    InviteAcceptRoute(
                        onHome = {
                            if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
                                navController.navigate(Routes.HOME) { popUpTo(Routes.INVITE_ACCEPT) { inclusive = true } }
                            }
                        },
                        onLogin = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.INVITE_ACCEPT) { inclusive = true } } },
                    )
                }
                composable(Routes.HOME) {
                    HomeRoute(
                        onNewBattle = { openTab(DuelTab.BATTLES) },
                        onOpenBattle = { duelId -> navController.navigate(Routes.live(duelId)) },
                        onOpenBatteryGuide = { navController.navigate(Routes.BATTERY_GUIDE) },
                        onOpenDisclosure = { navController.navigate(Routes.disclosure()) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onOpenProfile = { openTab(DuelTab.PROFILE) },
                        onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                    )
                }
                composable(Routes.MODES) {
                    val context = LocalContext.current
                    val access: ProAccessViewModel = hiltViewModel()
                    val proView by access.view.collectAsStateWithLifecycle()
                    var prompt by remember { mutableStateOf<ProFeature?>(null) }
                    val home: HomeViewModel = hiltViewModel()
                    val homeState by home.uiState.collectAsStateWithLifecycle()
                    val battlesVm: com.doomscrollduel.feature.home.BattlesViewModel = hiltViewModel()
                    val myBattles by battlesVm.battles.collectAsStateWithLifecycle()
                    BattleModesScreen(
                        battles = myBattles,
                        onOpenBroadcast = { navController.navigate(Routes.broadcast()) },
                        onOpenBattle = { duelId -> navController.navigate(Routes.live(duelId)) },
                        header = BattleHeader(
                            name = (profileState as? ProfileState.Ready)?.profile?.displayName.orEmpty(),
                            streakDays = homeState.streakDays,
                            coins = homeState.coins,
                            reelsToday = homeState.reelsToday,
                            reelLimit = homeState.reelLimit,
                            hp = homeState.hp,
                        ),
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        onOpenProfile = { openTab(DuelTab.PROFILE) },
                        onOpenProgress = { openTab(DuelTab.PROGRESS) },
                        onOpenPaywall = { navController.navigate(Routes.paywall()) },
                        onModeSelected = { mode ->
                            val needed = mode.requiredFeature()
                            if (needed != null && !proView.isPro) {
                                prompt = needed
                            } else {
                                // Only the 1v1 flow exists so far; every mode opens it until the others are built.
                                navController.navigate(Routes.NEW_DUEL)
                            }
                        },
                        upgradePrompt = prompt,
                        onSeePro = {
                            navController.navigate(Routes.paywall(prompt))
                            prompt = null
                        },
                        onDismissPrompt = { prompt = null },
                        onInvite = { target -> context.shareText(context.getString(target.message)) },
                        onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                    )
                }
                composable(
                    route = Routes.BROADCAST,
                    arguments = listOf(navArgument("join") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    deepLinks = listOf(navDeepLink { uriPattern = "doomscrollduel://broadcast/join/{join}" }),
                ) {
                    BroadcastRoute(
                        onBack = { navController.popBackStack() },
                        onOpenRoom = { navController.navigate(Routes.BROADCAST_ROOM) { launchSingleTop = true } },
                    )
                }
                composable(Routes.BROADCAST_ROOM) {
                    BroadcastRoomRoute(onClose = { navController.popBackStack() })
                }
                composable(Routes.NEW_DUEL) {
                    val events: AnalyticsViewModel = hiltViewModel()
                    NewDuelRoute(
                        onBack = { navController.popBackStack() },
                        onSent = { duelId, form ->
                            events.track(AnalyticsEvent.DuelCreated(AnalyticsMode.DUEL, form.duration.hours, form.stakeCoins, form.reelLimit))
                            navController.navigate(Routes.live(duelId)) {
                                popUpTo(Routes.HOME)
                            }
                        },
                        onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                    )
                }
                composable(
                    route = Routes.LIVE,
                    arguments = listOf(navArgument(LiveDuelViewModel.ARG) { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = "doomscrollduel://duel/{duelId}" }),
                ) {
                    LiveDuelRoute(
                        onSeeResult = { duelId ->
                            navController.navigate(Routes.result(duelId)) {
                                popUpTo(Routes.LIVE) { inclusive = true }
                            }
                        },
                        onClose = {
                            if (!navController.popBackStack()) navController.navigate(Routes.HOME) { popUpTo(Routes.LIVE) { inclusive = true } }
                        },
                    )
                }
                composable(
                    route = Routes.RESULT,
                    arguments = listOf(navArgument(LiveDuelViewModel.ARG) { type = NavType.StringType }),
                    deepLinks = listOf(navDeepLink { uriPattern = "doomscrollduel://result/{duelId}" }),
                ) {
                    val events: AnalyticsViewModel = hiltViewModel()
                    ResultRoute(
                        onRematch = {
                            navController.navigate(Routes.NEW_DUEL) {
                                popUpTo(Routes.HOME)
                            }
                        },
                        onClose = {
                            if (!navController.popBackStack()) navController.navigate(Routes.HOME) { popUpTo(Routes.RESULT) { inclusive = true } }
                        },
                        onFinishedShown = { r ->
                            events.track(AnalyticsEvent.DuelFinished(AnalyticsMode.DUEL, r.outcome.toAnalytics(), r.me.reels, r.me.reels >= r.reelLimit))
                        },
                    )
                }
                composable(Routes.PROGRESS) {
                    ProgressRoute(onSeePro = { navController.navigate(Routes.paywall()) })
                }
                composable(Routes.SETTINGS) {
                    SettingsRoute(
                        onOpenBatteryGuide = { navController.navigate(Routes.BATTERY_GUIDE) },
                        onOpenFocusHours = { navController.navigate(Routes.FOCUS_HOURS) },
                        onOpenPaywall = { navController.navigate(Routes.paywall(it)) },
                        onOpenDisclosure = { navController.navigate(Routes.disclosure()) },
                        onOpenDisclosureReview = { navController.navigate(Routes.disclosure(review = true)) },
                        onOpenDeleteAccount = { navController.navigate(Routes.DELETE_ACCOUNT) },
                        onOpenPrivacy = { navController.navigate(Routes.legal(LegalDoc.PRIVACY)) },
                        onOpenTerms = { navController.navigate(Routes.legal(LegalDoc.TERMS)) },
                        onBack = { navController.popBackStack() },
                        onOpenFriends = { navController.navigate(Routes.FRIENDS) },
                        onSignIn = { navController.navigate(Routes.LOGIN) { launchSingleTop = true } },
                        onChooseUsername = { navController.navigate(Routes.USERNAME) { launchSingleTop = true } },
                    )
                }
                composable(
                    route = Routes.PAYWALL,
                    arguments = listOf(
                        navArgument(PaywallViewModel.ARG_FEATURE) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    PaywallRoute(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.DISCLOSURE,
                    arguments = listOf(
                        navArgument("review") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    DisclosureRoute(
                        reviewOnly = entry.arguments?.getBoolean("review") ?: false,
                        onClose = { navController.popBackStack() },
                        onOpenPrivacy = { navController.navigate(Routes.legal(LegalDoc.PRIVACY)) },
                    )
                }
                composable(
                    route = Routes.LEGAL,
                    arguments = listOf(navArgument(LegalDoc.ARG) { type = NavType.StringType }),
                ) { entry ->
                    LegalScreen(
                        doc = LegalDoc.fromKey(entry.arguments?.getString(LegalDoc.ARG)),
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.DELETE_ACCOUNT) {
                    DeleteAccountRoute(
                        onBack = { navController.popBackStack() },
                        // After deletion the person lands on Home; the account screen is not left on the back stack.
                        onFinished = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    )
                }
                composable(Routes.FOCUS_HOURS) {
                    FocusHoursRoute(onBack = { navController.popBackStack() })
                }
                composable(Routes.BATTERY_GUIDE) {
                    BatteryGuideRoute(onBack = { navController.popBackStack() })
                }
            }
        }
        if (currentTab != null) {
            NeonTabBar(selected = currentTab, onSelect = ::openTab)
        }
    }
}

private fun DuelOutcome.toAnalytics(): DuelResultToken = when (this) {
    DuelOutcome.WIN -> DuelResultToken.WIN
    DuelOutcome.LOSS -> DuelResultToken.LOSS
    DuelOutcome.DRAW -> DuelResultToken.DRAW
}
