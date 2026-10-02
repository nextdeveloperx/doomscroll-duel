package com.doomscrollduel.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.DuelTab
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.NeonIcons

private val BarBackdrop = Color(0xFF050818)
private val CapsuleFill = Color(0xFF12173D)
private val IdleIcon = Color(0xFF9AA6D6)
private val ActiveStart = Color(0xFFFF4D8D)
private val ActiveEnd = Color(0xFF6D3FE0)

/**
 * The bottom bar: a floating capsule. Only the open tab shows its name, inside a filled pink-to-violet pill that grows
 * to make room; the other tabs are plain icons. Every tab keeps a spoken label and a touch target of at least 48dp.
 */
@Composable
fun NeonTabBar(selected: DuelTab, onSelect: (DuelTab) -> Unit, modifier: Modifier = Modifier) {
    val capsule = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BarBackdrop)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(capsule)
                .background(CapsuleFill)
                .border(1.dp, Color(0x1AFFFFFF), capsule)
                .padding(6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab(DuelTab.HOME, selected, onSelect, NeonIcons.HomeSolid, stringResource(R.string.tab_home))
            Tab(DuelTab.BATTLES, selected, onSelect, NeonIcons.Swords, stringResource(R.string.tab_battles))
            Tab(DuelTab.PROGRESS, selected, onSelect, NeonIcons.Bars, stringResource(R.string.tab_progress))
            Tab(DuelTab.PROFILE, selected, onSelect, NeonIcons.PersonSolid, stringResource(R.string.tab_profile))
        }
    }
}

@Composable
private fun RowScope.Tab(tab: DuelTab, selected: DuelTab, onSelect: (DuelTab) -> Unit, icon: ImageVector, label: String) {
    val isSelected = tab == selected
    val weight by animateFloatAsState(if (isSelected) 2.3f else 1f, spring(stiffness = 400f), label = "tabWeight")
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(shape)
            .then(if (isSelected) Modifier.background(Brush.horizontalGradient(listOf(ActiveStart, ActiveEnd))) else Modifier)
            .selectable(selected = isSelected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = { onSelect(tab) })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            DuelIcon(icon, tint = if (isSelected) Color.White else IdleIcon, contentDescription = null, modifier = Modifier.size(24.dp))
            if (isSelected) NText(label, 14.sp, color = Color.White, weight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}
