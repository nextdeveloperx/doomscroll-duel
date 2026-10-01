package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

enum class ModeTag { FREE, PRO }

/**
 * Coloured game-mode tile: icon, name, description and a FREE / PRO tag.
 * Everything on it is ink on a bright fill. TalkBack reads one sentence:
 * "[name]. [description]. [FREE|PRO]".
 */
@Composable
fun ModeTile(
    name: String,
    description: String,
    icon: ImageVector,
    color: Color,
    tag: ModeTag,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val tagText = stringResource(if (tag == ModeTag.FREE) R.string.ds_tag_free else R.string.ds_tag_pro)
    val spoken = stringResource(R.string.ds_mode_tile_description, name, description, tagText)

    ChunkySurface(
        shape = DuelTheme.shapes.tile,
        fill = color,
        pressed = pressed,
        modifier = modifier
            .sizeIn(minWidth = ChunkyMetrics.MinTouchTarget, minHeight = ChunkyMetrics.MinTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = spoken },
    ) {
        Column(
            modifier = Modifier
                .clearAndSetSemantics { }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.text)
                        .border(ChunkyMetrics.OutlineWidth, colors.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    DuelIcon(icon, tint = colors.onBright, contentDescription = null, modifier = Modifier.size(26.dp))
                }
                TagPill(tag = tag, text = tagText)
            }
            DuelText(text = name, style = DuelTheme.typography.heading, color = colors.onBright)
            DuelText(text = description, style = DuelTheme.typography.body, color = colors.onBright)
        }
    }
}

@Composable
private fun TagPill(tag: ModeTag, text: String) {
    val colors = DuelTheme.colors
    val chip = DuelTheme.shapes.chip
    // FREE: white pill, ink text. PRO: ink pill, yellow text. Both read clearly on any tile colour.
    val fill = if (tag == ModeTag.FREE) colors.text else colors.outline
    val textColor = if (tag == ModeTag.FREE) colors.onBright else colors.yellow
    DuelText(
        text = text,
        style = DuelTheme.typography.captionStrong,
        color = textColor,
        maxLines = 1,
        modifier = Modifier
            .clip(chip)
            .background(fill)
            .border(ChunkyMetrics.OutlineWidth, colors.outline, chip)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Preview(name = "ModeTile", widthDp = 360)
@Composable
private fun ModeTilePreview() {
    DuelPreview {
        val colors = DuelTheme.colors
        ModeTile(
            name = "Classic Duel",
            description = "Kam reels dekhne wala jeetega.",
            icon = DuelIcons.Bolt,
            color = colors.orangeDeep,
            tag = ModeTag.FREE,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        ModeTile(
            name = "Squad Battle",
            description = "5 dost, ek leaderboard.",
            icon = DuelIcons.Friends,
            color = colors.lavender,
            tag = ModeTag.PRO,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
