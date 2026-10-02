package com.doomscrollduel.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.R
import androidx.compose.ui.res.stringResource
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.feature.modes.BannerWord
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

/** A flat rounded progress bar. */
@Composable
fun KitBar(fraction: Float, modifier: Modifier = Modifier, tone: Color = Kit.Pink, height: Dp = 10.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Kit.Track)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(tone.copy(alpha = 0.7f), tone))),
        )
    }
}

/** A round icon button for page headers; the spoken [description] is required. */
@Composable
fun KitHeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { DuelIcon(icon, tint = Color(0xFFC9D4FF), contentDescription = null, modifier = Modifier.size(22.dp)) }
}

/** One side of a face-off: name, the brain in the state its count earns, the count and an HP bar. */
@Composable
fun KitFighter(name: String, reels: Int, limit: Int, owner: BrainOwner, modifier: Modifier = Modifier, brainWidth: Dp = 112.dp) {
    val percent = ReelUsage.percentUsed(reels, limit)
    val state = BrainState.fromPercentUsed(percent)
    val hp = ReelUsage.hpForPercentUsed(percent)
    val tone = if (owner == BrainOwner.YOU) Kit.Pink else Kit.Blue
    val hpTone = when {
        hp >= 60 -> Kit.Green
        hp >= 30 -> Kit.Orange
        else -> Kit.Red
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NText(name, 15.sp, color = tone, weight = FontWeight.ExtraBold, maxLines = 1)
        BrainView(state = state, owner = owner, modifier = Modifier.width(brainWidth))
        BannerWord(reels.toString(), listOf(Color.White, Color(0xFFE3EAFF), Color(0xFF9DB4FF)), Color(0xFF2B1A8C), tone, 40.sp)
        KitBar(hp / 100f, Modifier.fillMaxWidth(), hpTone, 8.dp)
        NText(stringResource(R.string.ds_hp_compact, hp), 12.sp, color = Neon.Muted, weight = FontWeight.Bold, maxLines = 1)
    }
}

/** The gold VS disc between two fighters. */
@Composable
fun KitVs(modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(Kit.Gold), contentAlignment = Alignment.Center) {
        NText("VS", 13.sp, color = Color(0xFF2A1A00), weight = FontWeight.Black)
    }
}
