package com.doomscrollduel.feature.progress

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitPill
import com.doomscrollduel.feature.modes.BlueHot
import com.doomscrollduel.feature.modes.GoldHot
import com.doomscrollduel.feature.modes.PinkHot
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun ProgressRoute(
    onSeePro: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val ui = viewModel.ui.collectAsStateWithLifecycle().value
    ProgressScreen(
        ui = ui,
        onMode = viewModel::setMode,
        onBack = viewModel::back,
        onNext = viewModel::next,
        onSeePro = onSeePro,
        modifier = modifier,
    )
}

/**
 * Progress in the Battle design: how many reels, hour by hour for a day or day by day for a week. Daily/Weekly switch,
 * arrows to go back in time, a glowing bar chart, three numbers and a nudge to Pro. A plain function of [ui].
 */
@Composable
fun ProgressScreen(
    ui: ProgressUi,
    onMode: (ProgressMode) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSeePro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KitPage(modifier = modifier, title = stringResource(R.string.progress_title)) {
        ModeSwitch(ui.mode, onMode)
        Spacer(Modifier.height(14.dp))
        DateRow(ui, onBack, onNext)
        Spacer(Modifier.height(10.dp))
        KitCard(padding = PaddingValues(start = 10.dp, end = 14.dp, top = 18.dp, bottom = 14.dp)) {
            Chart(ui)
            if (ui.total == 0) {
                Spacer(Modifier.height(4.dp))
                NText(
                    stringResource(if (ui.mode == ProgressMode.DAILY) R.string.progress_empty_day else R.string.progress_empty_week),
                    12.sp, color = Neon.Muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Stats(ui)
        Spacer(Modifier.height(14.dp))
        ProPanel(onSeePro)
    }
}

// ----- Daily / Weekly ---------------------------------------------------------------------------------------------

@Composable
private fun ModeSwitch(mode: ProgressMode, onMode: (ProgressMode) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Kit.Surface)
            .border(1.dp, Kit.Edge, shape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(ProgressMode.DAILY to R.string.progress_daily, ProgressMode.WEEKLY to R.string.progress_weekly).forEach { (m, label) ->
            val on = m == mode
            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp)
                    .clip(shape)
                    .then(if (on) Modifier.background(Kit.Primary) else Modifier)
                    .clickable(role = Role.Tab) { if (!on) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMode(m) } }
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                NText(stringResource(label), 15.sp, color = if (on) Color.White else Neon.Muted, weight = FontWeight.ExtraBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DateRow(ui: ProgressUi, onBack: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Chevron(left = true, enabled = ui.canGoBack, label = stringResource(R.string.progress_previous), onClick = onBack)
        NText(periodTitle(ui), 18.sp, weight = FontWeight.ExtraBold, maxLines = 1)
        Chevron(left = false, enabled = ui.canGoNext, label = stringResource(R.string.progress_next), onClick = onNext)
    }
}

@Composable
private fun Chevron(left: Boolean, enabled: Boolean, label: String, onClick: () -> Unit) {
    val tint = if (enabled) Color.White else Neon.Muted.copy(alpha = 0.35f)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.background(Kit.Surface).border(1.dp, Kit.Edge, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 10.dp, height = 18.dp)) {
            val p = Path().apply {
                if (left) { moveTo(size.width, 0f); lineTo(0f, size.height / 2f); lineTo(size.width, size.height) }
                else { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height) }
            }
            drawPath(p, tint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun periodTitle(ui: ProgressUi): String {
    val short = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    return when (ui.mode) {
        ProgressMode.DAILY -> when (ui.offset) {
            0 -> stringResource(R.string.progress_today)
            -1 -> stringResource(R.string.progress_yesterday)
            else -> ui.end.format(short)
        }
        ProgressMode.WEEKLY ->
            if (ui.offset == 0) stringResource(R.string.progress_last7)
            else stringResource(R.string.progress_range, ui.start.format(short), ui.end.format(short))
    }
}

// ----- chart ------------------------------------------------------------------------------------------------------

@Composable
private fun Chart(ui: ProgressUi) {
    val reduced = DuelTheme.motion.reduced
    val weekly = ui.mode == ProgressMode.WEEKLY
    val top = niceCeiling(maxOf(ui.values.maxOrNull() ?: 0, if (weekly) minOf(ui.limit, (ui.values.maxOrNull() ?: 0) * 3 + 4) else 0, 4))
    val grow = remember { Animatable(1f) }
    LaunchedEffect(ui.values, ui.mode) {
        if (reduced) grow.snapTo(1f) else { grow.snapTo(0f); grow.animateTo(1f, tween(480)) }
    }
    val spoken = chartDescription(ui)
    val labels = xLabels(ui)
    val peak = ui.peakIndex
    val barTop = if (weekly) PinkHot else BlueHot
    val barBottom = if (weekly) Color(0xFF8A2BE2) else Color(0xFF3F5BFF)

    Column(modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken; role = Role.Image }) {
        Row(Modifier.fillMaxWidth().height(200.dp)) {
            Column(
                modifier = Modifier.width(30.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                listOf(top, top / 2, 0).forEach { NText(it.toString(), 11.sp, color = Neon.Muted, maxLines = 1) }
            }
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val h = size.height
                val grid = Color.White.copy(alpha = 0.12f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val y = h - h * f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f, pathEffect = if (f == 0f) null else dash)
                }
                val n = ui.values.size
                val slot = size.width / n
                val barW = slot * if (weekly) 0.58f else 0.6f
                ui.values.forEachIndexed { i, v ->
                    if (v <= 0) {
                        // A tiny stub, so an empty slot still reads as "zero" and not as "missing".
                        drawRoundRect(barTop.copy(alpha = 0.45f), Offset(i * slot + (slot - barW) / 2f, h - 5f), Size(barW, 5f), CornerRadius(2.5f))
                        return@forEachIndexed
                    }
                    val bh = (v.toFloat() / top) * h * grow.value
                    val x = i * slot + (slot - barW) / 2f
                    val over = weekly && v > ui.limit
                    val (c1, c2) = when {
                        over -> Color(0xFFFF6B8A) to Color(0xFFD01E48)
                        i == peak && !weekly -> Color(0xFFFFE66D) to Color(0xFFFF9F1C)
                        else -> barTop to barBottom
                    }
                    // Soft glow, then the bar itself.
                    drawRoundRect(Brush.verticalGradient(listOf(c1, c2), startY = h - bh, endY = h), Offset(x, h - bh), Size(barW, bh), CornerRadius(barW / 2f))
                }
                if (weekly && ui.limit <= top) {
                    val y = h - (ui.limit.toFloat() / top).coerceAtMost(1f) * h
                    drawLine(GoldHot, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(start = 38.dp)) {
            if (weekly) {
                labels.forEach { NText(it, 11.sp, color = Neon.Muted, maxLines = 1, align = TextAlign.Center, modifier = Modifier.weight(1f)) }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEach { NText(it, 11.sp, color = Neon.Muted, maxLines = 1) }
                }
            }
        }
    }
}

/** 1, 2, 2.5, 5 or 10 times a power of ten, whichever is the first one at or above [value]. */
private fun niceCeiling(value: Int): Int {
    val magnitude = 10.0.pow(kotlin.math.floor(log10(value.toDouble())))
    for (f in listOf(1.0, 2.0, 4.0, 5.0, 10.0)) {
        val candidate = (f * magnitude)
        if (candidate >= value) return ceil(candidate).toInt().let { if (it % 2 == 1) it + 1 else it }
    }
    return value
}

@Composable
private fun xLabels(ui: ProgressUi): List<String> = when (ui.mode) {
    ProgressMode.DAILY -> listOf(0, 6, 12, 18, 24).map { hourLabel(it) }
    ProgressMode.WEEKLY -> (0..6).map { ui.start.plusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3) }
}

private fun hourLabel(hour: Int): String {
    val h = hour % 24
    val twelve = if (h % 12 == 0) 12 else h % 12
    return twelve.toString() + if (h < 12) "am" else "pm"
}

@Composable
private fun chartDescription(ui: ProgressUi): String {
    val peak = ui.peakIndex
    return when {
        ui.total == 0 -> stringResource(R.string.progress_chart_none)
        ui.mode == ProgressMode.DAILY -> stringResource(R.string.progress_chart_day, ui.total, hourLabel(peak ?: 0))
        else -> stringResource(R.string.progress_chart_week, ui.total, dayName(ui.start.plusDays((peak ?: 0).toLong())))
    }
}

private fun dayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

// ----- numbers and Pro --------------------------------------------------------------------------------------------

@Composable
private fun Stats(ui: ProgressUi) {
    val weekly = ui.mode == ProgressMode.WEEKLY
    val peak = ui.peakIndex
    val second = if (weekly) (ui.total / 7).toString() else (peak?.let { hourLabel(it) } ?: "-")
    val secondLabel = if (weekly) R.string.progress_avg_day else R.string.progress_peak_hour
    val third = if (weekly) (peak?.let { ui.start.plusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3) } ?: "-")
    else (if (ui.limit > 0) "${(ui.total * 100L / ui.limit).coerceAtMost(999)}%" else "-")
    val thirdLabel = if (weekly) R.string.progress_peak_day else R.string.progress_of_limit
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(ui.total.toString(), R.string.progress_total, PinkHot, Modifier.weight(1f))
        StatTile(second, secondLabel, BlueHot, Modifier.weight(1f))
        StatTile(third, thirdLabel, Color(0xFFA56BFF), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, @androidx.annotation.StringRes label: Int, tone: Color, modifier: Modifier = Modifier) {
    val labelText = stringResource(label)
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = "$labelText: $value" }) {
        KitCard(radius = 20.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
            Box(Modifier.size(width = 22.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(tone))
            Spacer(Modifier.height(10.dp))
            NText(value, 24.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            NText(labelText, 12.sp, color = Neon.Muted, maxLines = 2, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun ProPanel(onSeePro: () -> Unit) {
    KitCard(
        radius = 22.dp,
        fill = Brush.horizontalGradient(listOf(GoldHot.copy(alpha = 0.22f), Kit.Surface)),
        edge = GoldHot.copy(alpha = 0.35f),
        onClick = onSeePro,
        onClickLabel = stringResource(R.string.progress_pro_action),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KitPill(stringResource(R.string.ds_tag_pro), tone = GoldHot, filled = true)
            NText(stringResource(R.string.progress_pro_title), 14.sp, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), lineHeight = 18.sp)
            Canvas(Modifier.size(width = 9.dp, height = 15.dp)) {
                val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height) }
                drawPath(p, GoldHot, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Preview(name = "Progress daily", widthDp = 390, heightDp = 844)
@Composable
private fun ProgressDailyPreview() {
    ProgressScreen(
        ui = ProgressUi(values = List(24) { if (it in 8..23) (it * 7) % 23 else 0 }, limit = 100),
        onMode = {}, onBack = {}, onNext = {}, onSeePro = {},
    )
}
