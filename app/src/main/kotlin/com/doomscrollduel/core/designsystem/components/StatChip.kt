package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Read-only pill showing one number with an icon (streak, coins).
 * Display only, so it is outlined but has no hard shadow (shadows signal "pressable").
 * TalkBack reads [contentDescription] once for the whole chip.
 */
@Composable
fun StatChip(
    value: String,
    icon: ImageVector,
    accent: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    val shape = DuelTheme.shapes.chip
    Row(
        modifier = modifier
            .clearAndSetSemantics { this.contentDescription = contentDescription }
            .clip(shape)
            .background(colors.surface)
            .border(ChunkyMetrics.OutlineWidth, colors.outline, shape)
            .heightIn(min = 44.dp)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        DuelIcon(icon, tint = accent, contentDescription = null, modifier = Modifier.size(24.dp))
        DuelText(text = value, style = DuelTheme.typography.button, color = colors.text, maxLines = 1)
    }
}

@Composable
fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    val formatted = rememberIndianNumber(days)
    StatChip(
        value = formatted,
        icon = DuelIcons.Flame,
        accent = DuelTheme.colors.orange,
        contentDescription = stringResource(R.string.ds_streak_description, days),
        modifier = modifier,
    )
}

@Composable
fun CoinChip(coins: Int, modifier: Modifier = Modifier) {
    val formatted = rememberIndianNumber(coins)
    StatChip(
        value = formatted,
        icon = DuelIcons.Coin,
        accent = DuelTheme.colors.yellow,
        contentDescription = stringResource(R.string.ds_coins_description, coins),
        modifier = modifier,
    )
}

/** 1,00,000 style grouping, which is what users in India expect. */
@Composable
private fun rememberIndianNumber(value: Int): String =
    remember(value) { NumberFormat.getIntegerInstance(Locale("en", "IN")).format(value) }

@Preview(name = "StatChip", widthDp = 360)
@Composable
private fun StatChipPreview() = DuelPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StreakChip(days = 12)
        CoinChip(coins = 125000)
    }
}
