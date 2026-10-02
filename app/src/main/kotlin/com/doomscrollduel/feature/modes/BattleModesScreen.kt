package com.doomscrollduel.feature.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import androidx.compose.foundation.layout.PaddingValues
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitBar
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitHeaderIcon
import com.doomscrollduel.feature.common.KitIconDisc
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitPill
import com.doomscrollduel.feature.common.KitStat
import com.doomscrollduel.feature.home.ActiveBattleUi
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.FaceOff
import com.doomscrollduel.core.designsystem.theme.Nunito
import com.doomscrollduel.core.designsystem.theme.RussoOne
import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.feature.paywall.UpgradePrompt
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons
import com.doomscrollduel.feature.settings.neon.GradientIcon

enum class BattleMode { DUEL, SQUAD, NIGHT_PACT, FORFEIT_DARE, STRICT_LOCK }

/** The Pro feature a mode needs, or null when the mode is free. Agrees with `ProGate` in the domain. */
fun BattleMode.requiredFeature(): ProFeature? = when (this) {
    BattleMode.SQUAD -> ProFeature.SQUAD_BATTLE
    BattleMode.STRICT_LOCK -> ProFeature.STRICT_LOCK
    BattleMode.DUEL, BattleMode.NIGHT_PACT, BattleMode.FORFEIT_DARE -> null
}

/** What the top of the Battle page shows about the person. All of it is real: name, streak, coins, today's count. */
data class BattleHeader(
    val name: String = "",
    val streakDays: Int = 0,
    val coins: Int = 0,
    val reelsToday: Int = 0,
    val reelLimit: Int = 100,
    val hp: Int = 100,
)

internal fun Modifier.navyBackground(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1235), Color(0xFF050818))))
    drawRect(Brush.radialGradient(listOf(Color(0x33FF4D8D), Color.Transparent), center = Offset(0f, 0f), radius = size.width * 0.8f))
    drawRect(Brush.radialGradient(listOf(Color(0x333DA5FF), Color.Transparent), center = Offset(size.width, size.height * 0.45f), radius = size.width * 0.9f))
}

internal val PinkHot = Color(0xFFFF4D8D)
internal val BlueHot = Color(0xFF3DA5FF)
internal val VioletHot = Color(0xFFA56BFF)
internal val GoldHot = Color(0xFFFFB627)

/**
 * The Battle tab: a face-off hero with the play button, every game mode as a full-width row, three numbers, quick links,
 * today's challenge and an invite card. [upgradePrompt] is set when a non-Pro person tapped a Pro mode; it sits at the
 * bottom and "Baad mein" closes it.
 */
@Composable
fun BattleModesScreen(
    onModeSelected: (BattleMode) -> Unit,
    modifier: Modifier = Modifier,
    header: BattleHeader = BattleHeader(),
    upgradePrompt: ProFeature? = null,
    onSeePro: () -> Unit = {},
    onDismissPrompt: () -> Unit = {},
    onInvite: (InviteTarget) -> Unit = {},
    onOpenFriends: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenProgress: () -> Unit = {},
    onOpenPaywall: () -> Unit = {},
    battles: List<ActiveBattleUi> = emptyList(),
    onOpenBattle: (String) -> Unit = {},
    onOpenBroadcast: () -> Unit = {},
) {
    KitPage(
        modifier = modifier,
        title = stringResource(R.string.tab_battles),
        bottomInset = false,
        trailing = {
            KitHeaderIcon(NeonIcons.Bell, stringResource(R.string.bn_cd_friends), onOpenFriends)
            KitHeaderIcon(NeonIcons.Gear, stringResource(R.string.bn_cd_settings), onOpenSettings)
        },
        bottom = if (upgradePrompt != null) {
            { Box(Modifier.align(Alignment.BottomCenter).padding(12.dp)) { UpgradePrompt(upgradePrompt, onSeePro = onSeePro, onLater = onDismissPrompt) } }
        } else null,
    ) {
        Hero(onPlay = { onModeSelected(BattleMode.DUEL) })
        if (battles.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            NText(stringResource(R.string.bn_my_battles), 18.sp, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                battles.forEach { BattleRow(it) { onOpenBattle(it.duelId) } }
            }
        }
        Spacer(Modifier.height(18.dp))
        NText(stringResource(R.string.bn_game_modes), 18.sp, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeRow(NeonIcons.Swords, PinkHot, stringResource(R.string.mode_duel_name), stringResource(R.string.bn_duel_sub), false) { onModeSelected(BattleMode.DUEL) }
            ModeRow(NeonIcons.People, BlueHot, stringResource(R.string.mode_squad_name), stringResource(R.string.bn_squad_sub), true) { onModeSelected(BattleMode.SQUAD) }
            ModeRow(NeonIcons.Moon, VioletHot, stringResource(R.string.mode_night_name), stringResource(R.string.bn_night_sub), false) { onModeSelected(BattleMode.NIGHT_PACT) }
            ModeRow(NeonIcons.Trophy, GoldHot, stringResource(R.string.mode_dare_name), stringResource(R.string.bn_dare_sub), false) { onModeSelected(BattleMode.FORFEIT_DARE) }
            ModeRow(NeonIcons.Lock, Neon.Orange, stringResource(R.string.mode_lock_name), stringResource(R.string.bn_lock_sub), true) { onModeSelected(BattleMode.STRICT_LOCK) }
            ModeRow(com.doomscrollduel.feature.common.KitIcons.Broadcast, Neon.Green, stringResource(R.string.bc_mode_name), stringResource(R.string.bc_mode_sub), false) { onOpenBroadcast() }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KitStat(NeonIcons.Flame, GoldHot, stringResource(R.string.home_streak_value, header.streakDays), stringResource(R.string.home_streak_label), Modifier.weight(1f))
            KitStat(NeonIcons.Bolt, BlueHot, "${header.reelsToday} / ${header.reelLimit}", stringResource(R.string.home_reels_label), Modifier.weight(1.2f))
            KitStat(NeonIcons.Heart, Neon.Green, stringResource(R.string.home_hp_value, header.hp), stringResource(R.string.home_brain_hp), Modifier.weight(1.2f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickLink(stringResource(R.string.bn_link_friends), NeonIcons.People, PinkHot, onOpenFriends, Modifier.weight(1f))
            QuickLink(stringResource(R.string.bn_link_stats), NeonIcons.Bars, BlueHot, onOpenProgress, Modifier.weight(1f))
            QuickLink(stringResource(R.string.bn_link_invite), NeonIcons.Gift, VioletHot, { onInvite(InviteTarget.RIVAL) }, Modifier.weight(1f))
            QuickLink(stringResource(R.string.bn_link_pro), NeonIcons.Store, GoldHot, onOpenPaywall, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        DailyHeader(onOpenProgress)
        Spacer(Modifier.height(10.dp))
        DailyChallenge(header)
        Spacer(Modifier.height(14.dp))
        InviteCard(onInvite = { onInvite(InviteTarget.NIGHT_OWL) })
    }
}

// ------------------------------------------------------------------------------------------------------------ hero

/** The face-off picture on top; the title, the line under it and the play button sit below it, clear of the brains. */
@Composable
private fun Hero(onPlay: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    val title = stringResource(R.string.bn_banner_a) + " " + stringResource(R.string.bn_banner_b)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Kit.Surface),
    ) {
        FaceOff(arena = true, aspect = 1.6f, corner = 0.dp)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NText(title, 24.sp, weight = FontWeight.Black, maxLines = 1)
            NText(stringResource(R.string.bn_banner_tag), 13.sp, color = Neon.Muted, maxLines = 2, lineHeight = 17.sp)
            Spacer(Modifier.height(10.dp))
            KitButton(stringResource(R.string.bn_play_now), onPlay, icon = NeonIcons.Swords)
        }
    }
}

// ------------------------------------------------------------------------------------------------ my battles

/** One running or waiting battle: who, how it stands, how long is left. Tap opens it. */
@Composable
private fun BattleRow(battle: ActiveBattleUi, onClick: () -> Unit) {
    val line = when {
        battle.waiting -> stringResource(R.string.bn_battle_waiting, battle.opponentName)
        battle.finished -> stringResource(
            when {
                battle.myReels < battle.opponentReels -> R.string.bn_battle_won
                battle.myReels > battle.opponentReels -> R.string.bn_battle_lost
                else -> R.string.bn_battle_draw
            },
        )
        else -> stringResource(R.string.bn_battle_score, battle.timeLeft)
    }
    KitCard(radius = 22.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = onClick) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            KitIconDisc(NeonIcons.Swords, if (battle.waiting) GoldHot else if (battle.finished) Kit.Green else PinkHot, size = 44.dp)
            Column(Modifier.weight(1f)) {
                NText(stringResource(R.string.bn_battle_vs, battle.opponentName), 16.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                NText(line, 13.sp, color = Neon.Muted, maxLines = 2, lineHeight = 17.sp)
            }
            if (!battle.waiting) {
                // Both numbers, big: mine in pink on the left, theirs in blue on the right.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NText(battle.myReels.toString(), 22.sp, color = PinkHot, weight = FontWeight.Black, maxLines = 1)
                    NText("-", 16.sp, color = Neon.Muted, weight = FontWeight.Bold)
                    NText(battle.opponentReels.toString(), 22.sp, color = BlueHot, weight = FontWeight.Black, maxLines = 1)
                }
            }
            DuelIcon(NeonIcons.ChevronRight, tint = Neon.Muted, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------------ mode rows

@Composable
private fun ModeRow(icon: ImageVector, accent: Color, name: String, sub: String, pro: Boolean, onClick: () -> Unit) {
    val spoken = if (pro) "$name, $sub, ${stringResource(R.string.bn_pro)}" else "$name, $sub"
    KitCard(
        modifier = Modifier.semantics { contentDescription = spoken },
        radius = 22.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            KitIconDisc(icon, accent, size = 48.dp)
            Column(Modifier.weight(1f)) {
                NText(name, 16.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                NText(sub, 13.sp, color = Neon.Muted, maxLines = 2, lineHeight = 17.sp)
            }
            if (pro) KitPill(stringResource(R.string.bn_pro), tone = GoldHot, filled = true)
            DuelIcon(NeonIcons.ChevronRight, tint = Neon.Muted, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------- links, daily, invite

@Composable
private fun QuickLink(label: String, icon: ImageVector, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KitCard(
        modifier = modifier.semantics { contentDescription = label },
        radius = 18.dp,
        padding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KitIconDisc(icon, accent, size = 36.dp)
            NText(label, 12.sp, weight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun DailyHeader(onViewAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NText(stringResource(R.string.bn_daily), 18.sp, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
        Row(
            modifier = Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onViewAll).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NText(stringResource(R.string.bn_view_all), 13.sp, color = PinkHot, weight = FontWeight.Bold, maxLines = 1)
            DuelIcon(NeonIcons.ChevronRight, tint = PinkHot, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun DailyChallenge(header: BattleHeader) {
    val limit = header.reelLimit.coerceAtLeast(1)
    KitCard(radius = 22.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KitIconDisc(NeonIcons.Target, PinkHot, size = 46.dp)
            Column(Modifier.weight(1f)) {
                NText(stringResource(R.string.bn_daily_title, header.reelLimit), 14.sp, weight = FontWeight.Bold, maxLines = 2, lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitBar((header.reelsToday.toFloat() / limit).coerceIn(0f, 1f), Modifier.weight(1f), PinkHot, 8.dp)
                    NText("${header.reelsToday}/${header.reelLimit}", 12.sp, color = Neon.Muted, maxLines = 1)
                }
            }
            KitPill(stringResource(R.string.bn_daily_bonus), tone = GoldHot)
        }
    }
}

@Composable
private fun InviteCard(onInvite: () -> Unit) {
    val title = stringResource(R.string.bn_invite_title)
    KitCard(radius = 22.dp, fill = Brush.horizontalGradient(listOf(VioletHot.copy(alpha = 0.28f), Kit.Surface))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KitIconDisc(NeonIcons.Gift, VioletHot, size = 46.dp)
            Column(Modifier.weight(1f)) {
                NText(title, 15.sp, weight = FontWeight.ExtraBold, maxLines = 1)
                NText(stringResource(R.string.bn_invite_sub), 12.sp, color = Neon.Muted, maxLines = 2, lineHeight = 16.sp)
            }
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Kit.Primary)
                    .clickable(role = Role.Button, onClick = onInvite)
                    .padding(horizontal = 18.dp)
                    .semantics { contentDescription = title },
                contentAlignment = Alignment.Center,
            ) { NText(stringResource(R.string.bn_invite_btn), 13.sp, weight = FontWeight.ExtraBold, maxLines = 1) }
        }
    }
}

/** One word of the banner title: heavy italic Russo One, a dark outline, a coloured halo and a gradient face. */
@Composable
internal fun BannerWord(text: String, face: List<Color>, outline: Color, halo: Color, size: androidx.compose.ui.unit.TextUnit) {
    val base = TextStyle(
        fontFamily = RussoOne,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = size,
        letterSpacing = 0.5.sp,
    )
    Box {
        // Halo, then the dark outline, then the lit face on top.
        BasicText(text, maxLines = 1, style = base.copy(color = halo.copy(alpha = 0.55f), drawStyle = Stroke(width = 22f, join = StrokeJoin.Round), shadow = androidx.compose.ui.graphics.Shadow(halo, Offset.Zero, 28f)))
        BasicText(text, maxLines = 1, style = base.copy(color = outline, drawStyle = Stroke(width = 13f, join = StrokeJoin.Round)))
        BasicText(text, maxLines = 1, style = base.copy(brush = Brush.verticalGradient(face)))
    }
}

internal fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1f - a),
        green = green * a + background.green * (1f - a),
        blue = blue * a + background.blue * (1f - a),
        alpha = 1f,
    )
}

/** The Battle design's page: navy backdrop, status-bar padding, a scrolling column and an optional pinned bottom slot. */
@Composable
internal fun NeonPage(
    modifier: Modifier = Modifier,
    bottom: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().navyBackground()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(top = 10.dp, bottom = if (bottom != null) 84.dp else 16.dp),
            content = content,
        )
        if (bottom != null) bottom()
    }
}

@Preview(name = "Battle 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun BattleModesPreview() {
    BattleModesScreen(onModeSelected = {}, header = BattleHeader("Vikas", 4, 1250, 42, 100, 58))
}
