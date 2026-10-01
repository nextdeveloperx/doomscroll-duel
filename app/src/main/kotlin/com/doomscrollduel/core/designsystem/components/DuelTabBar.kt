package com.doomscrollduel.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.doomscrollduel.R

/** Top-level destinations shown in the bottom bar. */
enum class DuelTab(val id: String) {
    HOME("home"),
    BATTLES("battles"),
    SETTINGS("settings"),
}

/** The app's bottom navigation: Home, Battles, Settings. */
@Composable
fun DuelTabBar(
    selected: DuelTab,
    onSelect: (DuelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        BottomNavItem(DuelTab.HOME.id, stringResource(R.string.tab_home), DuelIcons.Home),
        BottomNavItem(DuelTab.BATTLES.id, stringResource(R.string.tab_battles), DuelIcons.Bolt),
        BottomNavItem(DuelTab.SETTINGS.id, stringResource(R.string.tab_settings), DuelIcons.Gear),
    )
    BottomNavBar(
        items = items,
        selectedId = selected.id,
        onSelect = { id -> onSelect(DuelTab.entries.first { it.id == id }) },
        modifier = modifier,
    )
}
