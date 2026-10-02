package com.doomscrollduel.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.doomscrollduel.R

/** Top-level destinations shown in the bottom bar. */
enum class DuelTab(val id: String) {
    HOME("home"),
    BATTLES("battles"),
    PROGRESS("progress"),
    PROFILE("profile"),
}

/** The app's bottom navigation: Home, Battles, Progress. Settings opens from the gear on Home. */
@Composable
fun DuelTabBar(
    selected: DuelTab,
    onSelect: (DuelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomNavItem(DuelTab.HOME.id, stringResource(R.string.tab_home), DuelIcons.Home),
        BottomNavItem(DuelTab.BATTLES.id, stringResource(R.string.tab_battles), DuelIcons.Bolt),
        BottomNavItem(DuelTab.PROGRESS.id, stringResource(R.string.tab_progress), DuelIcons.Chart),
        BottomNavItem(DuelTab.PROFILE.id, stringResource(R.string.tab_profile), DuelIcons.Friends),
    )
    BottomNavBar(
        items = items,
        selectedId = selected.id,
        onSelect = { id -> onSelect(DuelTab.entries.first { it.id == id }) },
        modifier = modifier,
    )
}
