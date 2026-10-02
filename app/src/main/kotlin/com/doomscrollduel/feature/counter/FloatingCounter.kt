package com.doomscrollduel.feature.counter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

/**
 * The small pill drawn over Instagram, YouTube, Facebook and Snapchat: today's reels against the daily limit.
 * It shows only our own number (never anything from the other app) and takes no touches.
 * Colour follows the brain: green while happy, orange when fried, red once the limit is used.
 */

@Composable
fun FloatingCounter(count: Int, limit: Int, modifier: Modifier = Modifier) {
    val percent = ReelUsage.percentUsed(count, limit)
    val accent = when (BrainState.fromPercentUsed(percent)) {
        BrainState.HAPPY -> Kit.Green
        BrainState.FRIED -> Kit.Orange
        BrainState.ZOMBIE -> Kit.Red
    }
    val shape = RoundedCornerShape(50)
    val description = stringResource(R.string.counter_badge_description, count, limit)
    Row(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = description }
            .clip(shape)
            .background(Color(0xEB0B1235))
            .border(1.dp, accent.copy(alpha = 0.7f), shape)
            .padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        NText(count.toString(), 16.sp, weight = FontWeight.Black, maxLines = 1)
        Box(
            Modifier
                .width(44.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(Kit.Track),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction = (percent / 100f).coerceIn(0.04f, 1f))
                    .height(6.dp)
                    .background(accent),
            )
        }
        NText(stringResource(R.string.counter_badge_limit, limit), 12.sp, color = Neon.Muted, weight = FontWeight.Bold, maxLines = 1)
    }
}
