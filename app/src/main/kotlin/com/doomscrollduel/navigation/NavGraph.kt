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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
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
import com.doomscrollduel.feature.modes.BattleModesScreen
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
}

private const val FadeMillis = 180

/**
 * The app's navigation.
 *
 * Tabs (bottom bar): HOME, MODES ("Battles"), SETTINGS. BATTERY_GUIDE opens from the Home banner and Settings;
 * FOCUS_HOURS opens from Settings.
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
                    )
                }
                composable(Routes.MODES) {
                    BattleModesScreen(
                        // Only the 1v1 flow exists so far; every mode opens it until the others are built.
                        onModeSelected = { navController.navigate(Routes.NEW_DUEL) },
                    )
                }
                composable(Routes.NEW_DUEL) {
                    NewDuelRoute(
                        onBack = { navController.popBackStack() },
                        onSend = {
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
