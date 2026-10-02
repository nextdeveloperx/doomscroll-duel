package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.sp
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
        // A big, faint copy of the icon in the corner gives each mode its own character without more text.
        DuelIcon(
            icon,
            tint = colors.onBright.copy(alpha = 0.13f),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 22.dp, y = 22.dp)
                .size(104.dp)
                .rotate(-14f),
        )
        Column(
            modifier = Modifier
                .clearAndSetSemantics { }
                .fillMaxHeight()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ModeMedallion(icon = icon, tint = color)
                ModeTagPill(tag = tag, text = tagText)
            }
            Spacer(Modifier.height(12.dp))
            DuelText(text = name, style = DuelTheme.typography.heading, color = colors.onBright)
            Spacer(Modifier.height(4.dp))
            DuelText(text = description, style = DuelTheme.typography.body, color = colors.onBright)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            ModeAction(
                text = stringResource(if (tag == ModeTag.PRO) R.string.ds_action_unlock else R.string.ds_action_play),
                pro = tag == ModeTag.PRO,
            )
        }
    }
}

/** A round "button" for the mode icon: a white ball lit from the top left, with the icon inked on it. */
@Composable
private fun ModeMedallion(icon: ImageVector, tint: Color) {
    val colors = DuelTheme.colors
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    0f to Color.White,
                    0.6f to lerp(Color.White, tint, 0.18f),
                    1f to lerp(Color.White, tint, 0.55f),
                    center = Offset(18f, 14f),
                    radius = 120f,
                ),
            )
            .border(ChunkyMetrics.OutlineWidth, colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DuelIcon(icon, tint = colors.onBright, contentDescription = null, modifier = Modifier.size(28.dp))
    }
}

/** The "KHELO" call to action: an ink pill with a chevron, so every tile visibly ends in a button. */
@Composable
internal fun ModeAction(text: String, pro: Boolean, modifier: Modifier = Modifier) {
    val colors = DuelTheme.colors
    val face = if (pro) colors.yellow else colors.text
    Row(
        modifier = modifier
            .clip(DuelTheme.shapes.chip)
            .background(colors.outline)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DuelText(text = text, style = DuelTheme.typography.button.copy(fontSize = 15.sp), color = face, maxLines = 1)
        Canvas(Modifier.size(width = 9.dp, height = 14.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
            }
            drawPath(path, face, style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun ModeTagPill(tag: ModeTag, text: String, modifier: Modifier = Modifier) {
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
        modifier = modifier
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
