package com.doomscrollduel.feature.settings.neon

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.core.designsystem.theme.Nunito
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/** Colours of the glass-and-violet Settings design. Local to this screen on purpose; the rest of the app keeps its own look. */
object Neon {
    val Bg0 = Color(0xFF05030C)
    val Bg1 = Color(0xFF0C0719)
    val Card0 = Color(0xFF181129)
    val Card1 = Color(0xFF0D0919)
    val Violet = Color(0xFF8B5CF6)
    val VioletLight = Color(0xFFB794FF)
    val VioletDeep = Color(0xFF6D3FE0)
    val Magenta = Color(0xFFC04CFF)
    val Pink = Color(0xFFE879F9)
    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFBDB1DB)
    val Gold = Color(0xFFFFC53D)
    val Green = Color(0xFF5EEAA0)
    val Orange = Color(0xFFFFB35C)
    val Red = Color(0xFFFF6B8A)

    val Gradient = Brush.horizontalGradient(listOf(Color(0xFF6C4BFF), Color(0xFFB04CFF)))
    val Border = Brush.linearGradient(listOf(Violet.copy(alpha = 0.55f), Violet.copy(alpha = 0.14f), Magenta.copy(alpha = 0.5f)))
}

/** One text style for the whole screen (Nunito, which the app already ships). */
@Composable
fun NText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Neon.Text,
    weight: FontWeight = FontWeight.SemiBold,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = Nunito,
            fontWeight = weight,
            fontSize = size,
            color = color,
            textAlign = align ?: TextAlign.Unspecified,
            lineHeight = lineHeight,
        ),
    )
}

/** The dark violet page: a deep gradient, two soft glows and a few faint waves across the top. */
fun Modifier.neonBackground(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Neon.Bg1, Neon.Bg0)))
    drawRect(Brush.radialGradient(listOf(Neon.Violet.copy(alpha = 0.22f), Color.Transparent), center = Offset(0f, 0f), radius = size.width * 0.9f))
    drawRect(Brush.radialGradient(listOf(Neon.Magenta.copy(alpha = 0.14f), Color.Transparent), center = Offset(size.width, size.height * 0.78f), radius = size.width * 0.8f))
    for (k in 0 until 3) {
        val path = Path()
        val base = size.height * 0.055f + k * 22.dp.toPx()
        path.moveTo(size.width * 0.35f, 0f)
        var x = size.width * 0.35f
        while (x <= size.width) {
            val t = (x - size.width * 0.35f) / (size.width * 0.65f)
            path.lineTo(x, base + sin((t * 2.4f + k * 0.5f) * PI.toFloat()) * 16.dp.toPx() * (1f - t * 0.3f))
            x += 12f
        }
        drawPath(path, Neon.VioletLight.copy(alpha = 0.10f - k * 0.025f), style = Stroke(width = 1.2.dp.toPx()))
    }
}

/** A glass card: dark gradient, a thin violet-to-magenta edge and a soft glow in one corner. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Neon.Card0, Neon.Card1)))
            .drawBehind {
                if (glow) {
                    drawRect(Brush.radialGradient(listOf(Neon.Violet.copy(alpha = 0.30f), Color.Transparent), center = Offset(size.width, size.height), radius = size.width * 0.7f))
                    drawRect(Brush.radialGradient(listOf(Neon.Magenta.copy(alpha = 0.16f), Color.Transparent), center = Offset(0f, size.height), radius = size.width * 0.5f))
                }
            }
            .border(1.dp, Neon.Border, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** The round icon "button" that starts a row. Not interactive. */
@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 42.dp, rounded: Boolean = false, tint: Color = Neon.VioletLight) {
    val shape = if (rounded) RoundedCornerShape(12.dp) else CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.radialGradient(listOf(Color(0xFF241A3F), Color(0xFF0F0A1E))))
            .border(1.5.dp, Brush.linearGradient(listOf(Neon.VioletLight.copy(alpha = 0.8f), Neon.Violet.copy(alpha = 0.25f))), shape),
        contentAlignment = Alignment.Center,
    ) {
        DuelIcon(icon, tint = tint, contentDescription = null, modifier = Modifier.size(size * 0.5f))
    }
}

/** A small outlined tag with a glowing dot: "Stay Focused". */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier, dot: Color = Neon.Violet) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF110B20))
            .border(1.dp, Neon.Violet.copy(alpha = 0.3f), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), dot))))
        NText(text, 12.5.sp, color = Neon.Text, maxLines = 1)
    }
}

/** A section title: icon square, name, one line of help, and a tag at the right. */
@Composable
fun SectionHeader(icon: ImageVector, title: String, subtitle: String, pill: String, modifier: Modifier = Modifier, pillDot: Color = Neon.Violet) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, size = 38.dp, rounded = true, tint = Neon.Violet)
        Column(Modifier.weight(1f)) {
            NText(title, 15.sp, weight = FontWeight.ExtraBold, maxLines = 2, lineHeight = 18.sp)
            NText(subtitle, 12.sp, color = Neon.Muted, maxLines = 2, lineHeight = 15.sp)
        }
        TagPill(pill, dot = pillDot)
    }
}

/**
 * Off / On as one control. The whole thing is a single switch for TalkBack ("[label], on"); the two words and the sliding
 * highlight are only the picture of it. The text on the lit half is white on a deep violet, which stays readable.
 */
@Composable
fun SegmentedSwitch(checked: Boolean, onChecked: (Boolean) -> Unit, label: String, offText: String, onText: String, enabled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    val reduced = DuelTheme.motion.reduced
    val thumbX by animateDpAsState(if (checked) 46.dp else 0.dp, tween(if (reduced) 0 else 180), label = "segThumb")
    // The touch area is 48 dp tall as Android asks; the picture inside is the slimmer 36 dp control of the design.
    Box(
        modifier = Modifier
            .width(98.dp)
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(98.dp)
                .height(36.dp)
                .clip(shape)
                .background(Color(0xFF09050F))
                .border(1.2.dp, Neon.Violet.copy(alpha = 0.4f), shape)
                .padding(3.dp),
        ) {
            Box(
                Modifier
                    .offset(x = thumbX)
                    .width(46.dp)
                    .height(30.dp)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFA77BFF), Color(0xFF7C4DFF))))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), shape),
            )
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                SegmentWord(offText, selected = !checked)
                SegmentWord(onText, selected = checked)
            }
        }
    }
}

@Composable
private fun SegmentWord(text: String, selected: Boolean) {
    Box(Modifier.width(46.dp).height(30.dp), contentAlignment = Alignment.Center) {
        NText(text, 14.sp, color = if (selected) Color.White else Neon.Muted, weight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
    }
}

/** The gold PRO chip next to a title. */
@Composable
fun ProChip(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF3A2A08), Color(0xFF2A1D05))))
            .border(1.dp, Neon.Gold.copy(alpha = 0.8f), shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DuelIcon(NeonIcons.Crown, tint = Neon.Gold, contentDescription = null, modifier = Modifier.size(12.dp))
        NText(text, 11.sp, color = Neon.Gold, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** A button: the filled one is the violet-to-magenta gradient, the other is a glass outline. */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    danger: Boolean = false,
    height: Dp = 44.dp,
) {
    val shape = RoundedCornerShape(50)
    val outline = if (danger) Neon.Red.copy(alpha = 0.7f) else Neon.Violet.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .heightIn(min = height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .then(if (filled) Modifier.background(Neon.Gradient) else Modifier.background(Color(0xFF110B20)).border(1.2.dp, outline, shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        NText(text, 14.sp, color = if (danger && !filled) Neon.Red else Color.White, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** A round violet +/- button. */
@Composable
fun RoundStep(plus: Boolean, onClick: () -> Unit, label: String, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .drawBehind { drawCircle(Neon.Violet.copy(alpha = 0.35f), radius = size.minDimension / 2f + 5.dp.toPx()) }
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFB896FF), Color(0xFF7A47F0))))
            .border(1.2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            val w = 2.4.dp.toPx()
            drawLine(Color(0xFF14082E), Offset(2f, size.height / 2f), Offset(size.width - 2f, size.height / 2f), strokeWidth = w, cap = StrokeCap.Round)
            if (plus) drawLine(Color(0xFF14082E), Offset(size.width / 2f, 2f), Offset(size.width / 2f, size.height - 2f), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

/**
 * The limit as a ring: a dim track, a glowing violet arc (log scale, so 10 to 1000 all show up), a bright end knob,
 * and the number in the middle. Pure decoration for the number; the +/- buttons change it.
 */
@Composable
fun LimitDial(limit: Int, unit: String, size: Dp = 118.dp, min: Int = 10, max: Int = 1000) {
    // 25 % of the ring is already lit at the smallest limit, so the dial never looks empty; the rest grows on a log scale.
    val fraction = (0.25f + 0.75f * ln(limit.coerceIn(min, max).toFloat() / min) / ln(max.toFloat() / min)).coerceIn(0.25f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f + 6.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = arcSize.width / 2f
            drawCircle(Color(0xFF0B0716), radius = this.size.minDimension / 2f)
            drawCircle(Neon.Violet.copy(alpha = 0.18f), radius = this.size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            rotate(135f, center) {
                drawArc(Neon.Violet.copy(alpha = 0.16f), 0f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(Neon.Violet.copy(alpha = 0.28f), 0f, 270f * fraction, false, topLeft, arcSize, style = Stroke(stroke + 6.dp.toPx(), cap = StrokeCap.Round))
                drawArc(
                    Brush.sweepGradient(listOf(Color(0xFF6C4BFF), Color(0xFFB04CFF), Color(0xFFE0B7FF)), center),
                    0f, 270f * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            // dotted ticks along the part that is still empty
            val ticks = 26
            for (i in 0..ticks) {
                val f = i / ticks.toFloat()
                if (f <= fraction) continue
                val a = (135f + 270f * f) * PI.toFloat() / 180f
                val r1 = radius - stroke / 2f - 5.dp.toPx()
                val r2 = r1 - 4.dp.toPx()
                drawLine(Neon.VioletLight.copy(alpha = 0.35f), center + Offset(cos(a), sin(a)) * r1, center + Offset(cos(a), sin(a)) * r2, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
            val end = (135f + 270f * fraction) * PI.toFloat() / 180f
            val knob = center + Offset(cos(end), sin(end)) * radius
            drawCircle(Color.White.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = knob)
            drawCircle(Color.White, radius = 4.dp.toPx(), center = knob)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NText(limit.toString(), 28.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            NText(unit, 12.sp, color = Neon.Muted, maxLines = 1)
        }
    }
}

/** Visual only: used by the bottom bar to leave room under the scrolling content. */
@Composable
fun BoxScope.BottomScrim(height: Dp = 130.dp) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Neon.Bg0.copy(alpha = 0.92f), Neon.Bg0))),
    )
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** An icon filled with a gradient instead of one flat colour (the glossy icons of the Battle page and tab bar). */
@Composable
fun GradientIcon(icon: ImageVector, brush: Brush, size: Dp, modifier: Modifier = Modifier) {
    DuelIcon(
        icon,
        tint = Color.White,
        contentDescription = null,
        modifier = modifier
            .size(size)
            .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(brush, blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn)
            },
    )
}
