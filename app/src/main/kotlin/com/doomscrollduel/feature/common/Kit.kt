package com.doomscrollduel.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.feature.modes.navyBackground
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons

/**
 * The shared pieces of the redesigned pages (everything except Settings): flat filled cards with a hairline edge, one
 * filled pink-to-violet primary button, quiet secondary buttons, a header with a back button, and small chips.
 * Hierarchy comes from fill and size, never from glowing outlines.
 */
object Kit {
    val Surface = Color(0xFF12173D)
    val SurfaceHigh = Color(0xFF1A2050)
    val Edge = Color(0x1AFFFFFF)
    val Track = Color(0xFF232A5C)
    val Pink = Color(0xFFFF4D8D)
    val Blue = Color(0xFF3DA5FF)
    val Violet = Color(0xFFA56BFF)
    val VioletDeep = Color(0xFF6D3FE0)
    val Gold = Color(0xFFFFB627)
    val Green = Color(0xFF5EEAA0)
    val Orange = Color(0xFFFFB35C)
    val Red = Color(0xFFFF6B8A)
    val Primary = Brush.horizontalGradient(listOf(Pink, VioletDeep))
}

/** A flat filled card with a hairline edge; tap-able when [onClick] is given. */
@Composable
fun KitCard(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    fill: Brush = Brush.verticalGradient(listOf(Kit.Surface, Kit.Surface)),
    edge: Color = Kit.Edge,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * A page: navy backdrop, a header row (back button, title, optional trailing actions), a scrolling body and an optional
 * pinned bottom slot such as the primary button. Pass [onBack] = null on tab pages.
 */
@Composable
fun KitPage(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    bottom: (@Composable BoxScope.() -> Unit)? = null,
    bottomSpace: Dp = 92.dp,
    /** True on full pages; false on tab pages, where the tab bar already sits above the system bar. */
    bottomInset: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().navyBackground()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (title != null || onBack != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (onBack != null) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button, onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            DuelIcon(NeonIcons.Back, tint = Color.White, contentDescription = stringResource(R.string.paywall_back), modifier = Modifier.size(24.dp))
                        }
                    } else {
                        Spacer(Modifier.size(12.dp))
                    }
                    if (title != null) {
                        NText(title, 22.sp, weight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    trailing?.invoke(this)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = if (bottom != null) bottomSpace else 24.dp),
                content = content,
            )
        }
        if (bottom != null) {
            Box(if (bottomInset) Modifier.fillMaxSize().navigationBarsPadding() else Modifier.fillMaxSize()) { bottom() }
        }
    }
}

enum class KitButtonKind { Primary, Secondary, Danger }

/** The one button style: filled gradient for the main action, a soft tonal fill for the rest. */
@Composable
fun KitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: KitButtonKind = KitButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val pill = RoundedCornerShape(50)
    val fill = when (kind) {
        KitButtonKind.Primary -> Kit.Primary
        KitButtonKind.Secondary -> Brush.verticalGradient(listOf(Kit.SurfaceHigh, Kit.SurfaceHigh))
        KitButtonKind.Danger -> Brush.horizontalGradient(listOf(Color(0xFFE5365A), Color(0xFFB4173F)))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(pill)
            .background(fill)
            .then(if (kind == KitButtonKind.Secondary) Modifier.border(1.dp, Kit.Edge, pill) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) DuelIcon(icon, tint = Color.White, contentDescription = null, modifier = Modifier.size(20.dp))
        NText(text, 15.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center, maxLines = 2)
    }
}

/** The page's main action pinned to the bottom of a [KitPage]. */
@Composable
fun BoxScope.KitBottomButton(text: String, onClick: () -> Unit, icon: ImageVector? = null, enabled: Boolean = true, kind: KitButtonKind = KitButtonKind.Primary) {
    KitButton(
        text = text,
        onClick = onClick,
        icon = icon,
        enabled = enabled,
        kind = kind,
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

/** A small rounded label. */
@Composable
fun KitPill(text: String, modifier: Modifier = Modifier, tone: Color = Kit.Violet, filled: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) tone else tone.copy(alpha = 0.16f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        NText(text, 12.sp, color = if (filled) Color(0xFF1B0A12) else tone, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** A selectable option chip (limits, stakes, durations). */
@Composable
fun KitChoice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(pill)
            .background(if (selected) Brush.horizontalGradient(listOf(Kit.Pink, Kit.VioletDeep)) else Brush.verticalGradient(listOf(Kit.Surface, Kit.Surface)))
            .border(1.dp, if (selected) Color.Transparent else Kit.Edge, pill)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { NText(text, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1) }
}

/** A round icon on a tinted disc. */
@Composable
fun KitIconDisc(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        DuelIcon(icon, tint = tint, contentDescription = null, modifier = Modifier.size(size * 0.5f))
    }
}

/** A section title with an optional one-line explanation underneath. */
@Composable
fun KitSection(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        NText(title, 18.sp, weight = FontWeight.ExtraBold)
        if (subtitle != null) NText(subtitle, 13.sp, color = Neon.Muted, lineHeight = 18.sp)
    }
}

/** A labelled number tile (icon disc, value, caption). */
@Composable
fun KitStat(icon: ImageVector, tint: Color, value: String, label: String, modifier: Modifier = Modifier) {
    KitCard(modifier = modifier, radius = 20.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
        KitIconDisc(icon, tint, size = 32.dp)
        Spacer(Modifier.height(10.dp))
        NText(value, 17.sp, weight = FontWeight.ExtraBold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        NText(label, 12.sp, color = Neon.Muted, maxLines = 2, lineHeight = 15.sp)
    }
}
