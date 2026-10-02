package com.doomscrollduel.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.Brain3D
import com.doomscrollduel.core.designsystem.brain.Brain3DMode
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.brain.ReelUsage
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.blocking.TimeFormat
import com.doomscrollduel.domain.blocking.WindowKind
import com.doomscrollduel.feature.FakeData
import com.doomscrollduel.feature.common.accent
import com.doomscrollduel.feature.common.label
import com.doomscrollduel.feature.modes.BannerWord
import com.doomscrollduel.feature.modes.BlueHot
import com.doomscrollduel.feature.modes.GoldHot
import com.doomscrollduel.feature.modes.NeonPage
import com.doomscrollduel.feature.modes.PinkHot
import com.doomscrollduel.feature.modes.VioletHot
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons
import com.doomscrollduel.tracking.health.TrackingIssue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val Surface = Color(0xFF12173D)
private val SurfaceEdge = Color(0x1AFFFFFF)
private val TickOff = Color(0xFF232A5C)
private val CtaStart = Color(0xFFFF4D8D)
private val CtaEnd = Color(0xFF6D3FE0)

/**
 * Home: greeting bar, a tick gauge that fills as reels pile up with the brain living inside it, three quick numbers,
 * today's split per app as one stacked bar, the running battle and one filled call-to-action.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onNewBattle: () -> Unit,
    onOpenBattle: () -> Unit,
    onFixTracking: (TrackingIssue) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    /** What the avatar shows; defaults to the sample name for previews. */
    avatarName: String? = null,
    modifier: Modifier = Modifier,
    /** Optional card shown under the banner (the one-time anonymous-data question). */
    extraTop: (@Composable ColumnScope.() -> Unit)? = null,
    onOpenFriends: () -> Unit = {},
) {
    val percentUsed = ReelUsage.percentUsed(state.reelsToday, state.reelLimit)
    NeonPage(
        modifier = modifier,
        bottom = { HomeCta(stringResource(R.string.home_new_battle), onNewBattle) },
    ) {
        GreetingBar(
            name = avatarName ?: state.userName,
            coins = state.coins,
            onOpenProfile = onOpenProfile,
            onOpenFriends = onOpenFriends,
            onOpenSettings = onOpenSettings,
        )
        Spacer(Modifier.height(12.dp))
        if (state.issue != TrackingIssue.NONE) {
            TrackingNotice(state.issue) { onFixTracking(state.issue) }
            Spacer(Modifier.height(12.dp))
        }
        if (extraTop != null) {
            extraTop()
            Spacer(Modifier.height(12.dp))
        }
        Gauge(state, percentUsed)
        Spacer(Modifier.height(10.dp))
        MoodLine(state.brainState)
        state.window?.let { window ->
            Spacer(Modifier.height(12.dp))
            WindowStrip(
                stringResource(
                    if (window.kind == WindowKind.BEDTIME) R.string.home_window_bedtime else R.string.home_window_focus,
                    TimeFormat.time12(window.end.atZone(java.time.ZoneId.systemDefault()).toLocalTime()),
                ),
            )
        }
        Spacer(Modifier.height(16.dp))
        StatRow(state)
        Spacer(Modifier.height(16.dp))
        BreakdownCard(state.perApp)
        Spacer(Modifier.height(16.dp))
        if (state.battle != null) BattleCard(state.battle, onOpenBattle) else InviteCard(onNewBattle)
    }
}

// ------------------------------------------------------------------------------------------------ surfaces

/** A flat filled card with a hairline edge. Hierarchy comes from fill and size, not from glowing outlines. */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    fill: Brush = Brush.verticalGradient(listOf(Surface, Surface)),
    edge: Color = SurfaceEdge,
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

// ------------------------------------------------------------------------------------------------ top bar

@Composable
private fun GreetingBar(name: String, coins: Int, onOpenProfile: () -> Unit, onOpenFriends: () -> Unit, onOpenSettings: () -> Unit) {
    val shown = name.trim().ifBlank { stringResource(R.string.bn_default_name) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(PinkHot, VioletHot)))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.bn_cd_profile), onClick = onOpenProfile),
            contentAlignment = Alignment.Center,
        ) { NText(shown.first().uppercase(), 18.sp, weight = FontWeight.ExtraBold) }
        Column(Modifier.weight(1f)) {
            NText(stringResource(R.string.home_greeting), 12.sp, color = Neon.Muted, maxLines = 1)
            NText(shown, 18.sp, weight = FontWeight.ExtraBold, maxLines = 1)
        }
        val coinText = "%,d".format(coins)
        val spoken = stringResource(R.string.bn_cd_coins, coinText)
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(Surface)
                .border(1.dp, GoldHot.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            DuelIcon(NeonIcons.Bolt, tint = GoldHot, contentDescription = null, modifier = Modifier.size(18.dp))
            NText(coinText, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1)
        }
        BarIcon(NeonIcons.Bell, stringResource(R.string.bn_cd_friends), onOpenFriends)
        BarIcon(NeonIcons.Gear, stringResource(R.string.bn_cd_settings), onOpenSettings)
    }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { DuelIcon(icon, tint = Color(0xFFC9D4FF), contentDescription = null, modifier = Modifier.size(22.dp)) }
}

// ------------------------------------------------------------------------------------------------ hero gauge

private fun moodColor(state: BrainState): Color = when (state) {
    BrainState.HAPPY -> PinkHot
    BrainState.FRIED -> Color(0xFFFFA23D)
    BrainState.ZOMBIE -> Color(0xFF7CE08A)
}

/**
 * The hero: 270 degrees of tick marks that light up as the day's reels use up the limit, the brain inside, the count
 * sitting in the open gap at the bottom. Green while there is room, orange past 40 percent, red over the limit.
 */
@Composable
private fun Gauge(state: HomeUiState, percentUsed: Int) {
    val over = state.reelsToday > state.reelLimit
    val tone = when {
        over -> Neon.Red
        percentUsed > 40 -> Neon.Orange
        else -> Neon.Green
    }
    val mood = moodColor(state.brainState)
    val fraction by animateFloatAsState(
        (state.reelsToday.toFloat() / state.reelLimit.coerceAtLeast(1)).coerceIn(0f, 1f),
        tween(900),
        label = "gauge",
    )
    val shownCount by animateIntAsState(state.reelsToday, tween(600), label = "count")
    val spokenBrain = stringResource(
        when (state.brainState) {
            BrainState.HAPPY -> R.string.ds_brain_you_happy
            BrainState.FRIED -> R.string.ds_brain_you_fried
            BrainState.ZOMBIE -> R.string.ds_brain_you_zombie
        },
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .size(292.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(mood.copy(alpha = 0.30f), Color.Transparent), radius = size.minDimension * 0.5f),
                    )
                },
        ) {
            Canvas(Modifier.size(292.dp)) {
                val ticks = 60
                val outer = size.minDimension / 2f - 4.dp.toPx()
                for (i in 0..ticks) {
                    val t = i / ticks.toFloat()
                    val angle = Math.toRadians((135.0 + 270.0 * t))
                    val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                    val len = if (i % 5 == 0) 20.dp.toPx() else 13.dp.toPx()
                    val lit = i == 0 || (fraction > 0f && t <= fraction + 0.0001f)
                    drawLine(
                        color = if (lit) tone else TickOff,
                        start = center + dir * (outer - len),
                        end = center + dir * outer,
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            Brain3D(
                mode = Brain3DMode.SINGLE,
                state = state.brainState,
                owner = BrainOwner.YOU,
                percentUsed = percentUsed,
                contentDescription = spokenBrain,
                modifier = Modifier.align(Alignment.Center).offset(y = (-10).dp).size(232.dp),
                fallback = {
                    Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
                        BrainView(state = state.brainState, modifier = Modifier.width(170.dp))
                    }
                },
            )
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BannerWord(
                    text = shownCount.toString(),
                    face = listOf(Color.White, Color(0xFFE3EAFF), Color(0xFF9DB4FF)),
                    outline = Color(0xFF2B1A8C),
                    halo = Color(0xFF6A8CFF),
                    size = 44.sp,
                )
                NText(stringResource(R.string.home_reels_label), 13.sp, color = Neon.Muted, weight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(2.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        NText(stringResource(R.string.home_limit_caption, state.reelLimit), 13.sp, color = Neon.Muted, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        NText(stringResource(R.string.home_used_percent, percentUsed), 13.sp, color = tone, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun MoodLine(brain: BrainState) {
    val mood = moodColor(brain)
    val label = when (brain) {
        BrainState.HAPPY -> R.string.home_mood_happy
        BrainState.FRIED -> R.string.home_mood_fried
        BrainState.ZOMBIE -> R.string.home_mood_zombie
    }
    val tip = when (brain) {
        BrainState.HAPPY -> R.string.home_tip_happy
        BrainState.FRIED -> R.string.home_tip_fried
        BrainState.ZOMBIE -> R.string.home_tip_zombie
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(mood.copy(alpha = 0.16f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(mood))
            NText(stringResource(label), 13.sp, color = mood, weight = FontWeight.ExtraBold, maxLines = 1)
        }
        NText(stringResource(tip), 13.sp, color = Neon.Muted, align = TextAlign.Center, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

@Composable
private fun WindowStrip(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VioletHot.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DuelIcon(NeonIcons.Moon, tint = VioletHot, contentDescription = null, modifier = Modifier.size(20.dp))
        NText(text, 13.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------------------------------------ numbers

@Composable
private fun StatRow(state: HomeUiState) {
    val over = state.reelsToday > state.reelLimit
    val left = abs(state.reelLimit - state.reelsToday)
    val hpTone = when {
        state.hp >= 60 -> Neon.Green
        state.hp >= 30 -> Neon.Orange
        else -> Neon.Red
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            icon = NeonIcons.Heart, tint = hpTone,
            value = stringResource(R.string.home_hp_value, state.hp),
            label = stringResource(R.string.home_brain_hp),
            spoken = stringResource(R.string.home_hp_value, state.hp),
            modifier = Modifier.weight(1.25f),
        )
        StatTile(
            icon = NeonIcons.Bolt, tint = if (over) Neon.Red else BlueHot,
            value = stringResource(R.string.home_left_value, left),
            label = stringResource(if (over) R.string.home_over_label else R.string.home_left_label),
            spoken = stringResource(if (over) R.string.home_stat_over_cd else R.string.home_stat_left_cd, left),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = NeonIcons.Flame, tint = GoldHot,
            value = stringResource(R.string.home_streak_value, state.streakDays),
            label = stringResource(R.string.home_streak_label),
            spoken = stringResource(R.string.bn_streak, state.streakDays),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(icon: ImageVector, tint: Color, value: String, label: String, spoken: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        radius = 20.dp,
        padding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            DuelIcon(icon, tint = tint, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        NText(value, 17.sp, weight = FontWeight.ExtraBold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        NText(label, 12.sp, color = Neon.Muted, maxLines = 2, lineHeight = 15.sp)
    }
}

// ------------------------------------------------------------------------------------------------ per-app split

/** One stacked bar for the day, split by app, with a legend that gives each app's count and share. */
@Composable
private fun BreakdownCard(perApp: List<AppCount>) {
    val colors = DuelTheme.colors
    val total = perApp.sumOf { it.count }
    Card {
        NText(stringResource(R.string.home_breakdown_title), 16.sp, weight = FontWeight.ExtraBold, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(TickOff), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (total > 0) {
                perApp.filter { it.count > 0 }.forEach { row ->
                    Box(Modifier.weight(row.count.toFloat()).height(14.dp).background(row.app.accent(colors)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        perApp.forEach { row ->
            val spoken = stringResource(R.string.home_breakdown_row, row.app.label(), row.count)
            val share = if (total > 0) row.count * 100 / total else 0
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).clearAndSetSemantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(row.app.accent(colors)))
                NText(row.app.label(), 14.sp, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                NText("$share%", 12.sp, color = Neon.Muted, maxLines = 1)
                NText(row.count.toString(), 15.sp, weight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.width(36.dp), align = TextAlign.End)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ battle

/** The running battle as a split card: your side in pink, theirs in blue, a gold VS between them. */
@Composable
private fun BattleCard(battle: ActiveBattleUi, onClick: () -> Unit) {
    Card(
        radius = 24.dp,
        fill = Brush.horizontalGradient(listOf(PinkHot.copy(alpha = 0.22f), Surface, BlueHot.copy(alpha = 0.22f))),
        edge = GoldHot.copy(alpha = 0.35f),
        onClick = onClick,
        onClickLabel = stringResource(R.string.home_open_battle),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NText(
                stringResource(
                    when {
                        battle.waiting -> R.string.home_battle_sent_title
                        battle.finished -> R.string.home_battle_over_title
                        else -> R.string.home_battle_title
                    },
                ),
                16.sp, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (!battle.waiting && !battle.finished) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(GoldHot).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    NText(stringResource(R.string.home_time_left, battle.timeLeft), 12.sp, color = Color(0xFF2A1A00), weight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (battle.waiting) {
            NText(stringResource(R.string.home_battle_waiting, battle.opponentName), 14.sp, color = Neon.Muted, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Side(stringResource(R.string.label_you), battle.myReels, PinkHot, Modifier.weight(1f))
                Box(Modifier.size(38.dp).clip(CircleShape).background(GoldHot), contentAlignment = Alignment.Center) {
                    NText("VS", 13.sp, color = Color(0xFF2A1A00), weight = FontWeight.Black)
                }
                Side(battle.opponentName, battle.opponentReels, BlueHot, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        NText(
            stringResource(R.string.home_stake_line, battle.stakeCoins),
            12.sp, color = Neon.Muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Side(name: String, reels: Int, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        NText(name, 14.sp, color = tone, weight = FontWeight.ExtraBold, maxLines = 1)
        BannerWord(reels.toString(), listOf(Color.White, Color(0xFFD7E3FF)), Color(0xFF2B1A8C), tone, 38.sp)
    }
}

@Composable
private fun InviteCard(onClick: () -> Unit) {
    Card(
        radius = 24.dp,
        fill = Brush.linearGradient(listOf(PinkHot.copy(alpha = 0.28f), VioletHot.copy(alpha = 0.22f))),
        onClick = onClick,
        onClickLabel = stringResource(R.string.home_new_battle),
        padding = PaddingValues(18.dp),
    ) {
        NText(stringResource(R.string.home_promo_title), 18.sp, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(4.dp))
        NText(stringResource(R.string.home_promo_body), 13.sp, color = Neon.Muted, lineHeight = 18.sp)
    }
}

// ------------------------------------------------------------------------------------------------ notice and CTA

@Composable
private fun TrackingNotice(issue: TrackingIssue, onFix: () -> Unit) {
    val needsConsent = issue == TrackingIssue.CONSENT_NEEDED
    val off = issue == TrackingIssue.ACCESSIBILITY_OFF || needsConsent
    val title = stringResource(if (needsConsent) R.string.banner_consent_title else if (off) R.string.banner_off_title else R.string.banner_battery_title)
    val body = stringResource(if (needsConsent) R.string.banner_consent_body else if (off) R.string.banner_off_body else R.string.banner_battery_body)
    val action = stringResource(if (needsConsent) R.string.banner_consent_action else if (off) R.string.banner_off_action else R.string.banner_battery_action)
    val tone = if (off) Neon.Red else Neon.Orange
    Card(radius = 20.dp, fill = Brush.verticalGradient(listOf(tone.copy(alpha = 0.14f), tone.copy(alpha = 0.14f))), edge = tone.copy(alpha = 0.45f), padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                NText(title, 15.sp, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                NText(body, 12.sp, color = Neon.Muted, lineHeight = 16.sp)
            }
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tone)
                    .clickable(role = Role.Button, onClick = onFix)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { NText(action, 13.sp, color = Color(0xFF1B0A12), weight = FontWeight.ExtraBold, maxLines = 2, align = TextAlign.Center) }
        }
    }
}

/** The one primary action, pinned above the tab bar as a filled pink-to-violet pill (white text, contrast above 4.5:1). */
@Composable
private fun BoxScope.HomeCta(text: String, onClick: () -> Unit) {
    val pill = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(pill)
            .background(Brush.horizontalGradient(listOf(CtaStart, CtaEnd)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        DuelIcon(NeonIcons.Swords, tint = Color.White, contentDescription = null, modifier = Modifier.size(22.dp))
        NText(text, 16.sp, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Preview(name = "Home 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    HomeScreen(state = FakeData.home, onNewBattle = {}, onOpenBattle = {}, onFixTracking = {})
}

@Preview(name = "Home counter off 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenOffPreview() {
    HomeScreen(
        state = FakeData.home.copy(issue = TrackingIssue.ACCESSIBILITY_OFF),
        onNewBattle = {}, onOpenBattle = {}, onFixTracking = {},
    )
}
