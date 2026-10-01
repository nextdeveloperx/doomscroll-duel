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
import com.doomscrollduel.core.designsystem.components.DuelTabBar
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.FakeData
import com.doomscrollduel.feature.duel.create.NewDuelRoute
import com.doomscrollduel.feature.duel.live.LiveDuelScreen
import com.doomscrollduel.feature.duel.result.ResultScreen
import com.doomscrollduel.feature.duel.result.rememberShareText
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
import com.doomscrollduel.feature.modes.BattleModesScreen
import com.doomscrollduel.feature.modes.requiredFeature
import com.doomscrollduel.feature.paywall.PaywallRoute
import com.doomscrollduel.feature.paywall.PaywallViewModel
import com.doomscrollduel.feature.paywall.ProAccessViewModel
import com.doomscrollduel.feature.settings.FocusHoursRoute
import com.doomscrollduel.feature.settings.SettingsRoute

object Routes {
    const val HOME = "home"
    const val MODES = "modes"
    const val NEW_DUEL = "new_duel"
    const val LIVE = "live"
    const val RESULT = "result"
    const val SETTINGS = "settings"
    const val BATTERY_GUIDE = "battery_guide"
    const val FOCUS_HOURS = "focus_hours"
    const val PAYWALL = "paywall?feature={feature}"
    const val DISCLOSURE = "disclosure?review={review}"
    const val DELETE_ACCOUNT = "delete_account"

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
) {
    val reducedMotion = DuelTheme.motion.reduced
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = when (backStackEntry?.destination?.route) {
        Routes.HOME -> DuelTab.HOME
        Routes.MODES -> DuelTab.BATTLES
        Routes.SETTINGS -> DuelTab.SETTINGS
        else -> null
    }

    fun openTab(tab: DuelTab) {
        val route = when (tab) {
            DuelTab.HOME -> Routes.HOME
            DuelTab.BATTLES -> Routes.MODES
            DuelTab.SETTINGS -> Routes.SETTINGS
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
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { if (reducedMotion) EnterTransition.None else fadeIn(tween(FadeMillis)) },
                exitTransition = { if (reducedMotion) ExitTransition.None else fadeOut(tween(FadeMillis)) },
                popEnterTransition = { if (reducedMotion) EnterTransition.None else fadeIn(tween(FadeMillis)) },
                popExitTransition = { if (reducedMotion) ExitTransition.None else fadeOut(tween(FadeMillis)) },
            ) {
                composable(Routes.HOME) {
                    HomeRoute(
                        onNewBattle = { openTab(DuelTab.BATTLES) },
                        onOpenBattle = { navController.navigate(Routes.LIVE) },
                        onOpenBatteryGuide = { navController.navigate(Routes.BATTERY_GUIDE) },
                        onOpenDisclosure = { navController.navigate(Routes.disclosure()) },
                    )
                }
                composable(Routes.MODES) {
                    val access: ProAccessViewModel = hiltViewModel()
                    val proView by access.view.collectAsStateWithLifecycle()
                    var prompt by remember { mutableStateOf<ProFeature?>(null) }
                    BattleModesScreen(
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
                    )
                }
                composable(Routes.NEW_DUEL) {
                    val events: AnalyticsViewModel = hiltViewModel()
                    NewDuelRoute(
                        onBack = { navController.popBackStack() },
                        onSend = { form ->
                            events.track(AnalyticsEvent.DuelCreated(AnalyticsMode.DUEL, form.duration.hours, form.stakeCoins, form.reelLimit))
                            navController.navigate(Routes.LIVE) {
                                popUpTo(Routes.HOME)
                            }
                        },
                    )
                }
                composable(Routes.LIVE) {
                    LiveDuelScreen(
                        state = FakeData.live,
                        onRoast = { /* roast messages arrive with FCM in the social milestone */ },
                        onSeeResult = {
                            navController.navigate(Routes.RESULT) {
                                popUpTo(Routes.LIVE) { inclusive = true }
                            }
                        },
                    )
                }
                composable(Routes.RESULT) {
                    val context = LocalContext.current
                    val events: AnalyticsViewModel = hiltViewModel()
                    LaunchedEffect(Unit) {
                        val r = FakeData.result
                        events.track(AnalyticsEvent.DuelFinished(AnalyticsMode.DUEL, r.outcome.toAnalytics(), r.me.reels, r.me.reels >= r.reelLimit))
                    }
                    val shareText = rememberShareText(FakeData.result)
                    ResultScreen(
                        state = FakeData.result,
                        onShare = { context.shareText(shareText) },
                        onRematch = {
                            navController.navigate(Routes.NEW_DUEL) {
                                popUpTo(Routes.HOME)
                            }
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsRoute(
                        onOpenBatteryGuide = { navController.navigate(Routes.BATTERY_GUIDE) },
                        onOpenFocusHours = { navController.navigate(Routes.FOCUS_HOURS) },
                        onOpenPaywall = { navController.navigate(Routes.paywall(it)) },
                        onOpenDisclosure = { navController.navigate(Routes.disclosure()) },
                        onOpenDisclosureReview = { navController.navigate(Routes.disclosure(review = true)) },
                        onOpenDeleteAccount = { navController.navigate(Routes.DELETE_ACCOUNT) },
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
            DuelTabBar(selected = currentTab, onSelect = ::openTab)
        }
    }
}

private fun DuelOutcome.toAnalytics(): DuelResultToken = when (this) {
    DuelOutcome.WIN -> DuelResultToken.WIN
    DuelOutcome.LOSS -> DuelResultToken.LOSS
    DuelOutcome.DRAW -> DuelResultToken.DRAW
}
