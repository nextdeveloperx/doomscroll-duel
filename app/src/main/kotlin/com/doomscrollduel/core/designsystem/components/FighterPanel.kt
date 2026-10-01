package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import com.doomscrollduel.core.designsystem.theme.DuelTheme

/**
 * One side of the arena: name, brain, big reel count and HP bar. Brain state and HP come from
 * [reels] and [limit] through [ReelUsage], so every screen agrees on them.
 */
@Composable
fun FighterPanel(
    name: String,
    reels: Int,
    limit: Int,
    owner: BrainOwner,
    modifier: Modifier = Modifier,
    brainWidth: Dp = 110.dp,
    countSize: TextUnit = 52.sp,
) {
    val colors = DuelTheme.colors
    val percent = ReelUsage.percentUsed(reels, limit)
    val state = BrainState.fromPercentUsed(percent)
    val hp = ReelUsage.hpForPercentUsed(percent)
    val nameColor = if (owner == BrainOwner.YOU) colors.pink else colors.cyan

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DuelText(
            text = name,
            style = DuelTheme.typography.bodyStrong,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BrainView(state = state, owner = owner, modifier = Modifier.width(brainWidth))
        HardShadowText(text = reels.toString(), fontSize = countSize, shadowDepth = 4.dp)
        HpBar(hp = hp, modifier = Modifier.fillMaxWidth(), showLabel = false)
        DuelText(
            text = stringResource(R.string.ds_hp_compact, hp),
            style = DuelTheme.typography.captionStrong,
            color = colors.textMuted,
        )
    }
}

@Preview(name = "FighterPanel", widthDp = 360)
@Composable
private fun FighterPanelPreview() = DuelPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FighterPanel("Tum", 47, 100, BrainOwner.YOU, Modifier.width(150.dp))
        FighterPanel("Aman", 31, 100, BrainOwner.OPPONENT, Modifier.width(150.dp))
    }
}
