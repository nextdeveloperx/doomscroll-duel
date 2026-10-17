package com.doomscrollduel.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.domain.social.LeaderboardBoard
import com.doomscrollduel.domain.social.LeaderboardEntry
import com.doomscrollduel.domain.social.LeaderboardUi
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitAvatarPhoto
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons

@Composable
fun LeaderboardRoute(modifier: Modifier = Modifier, viewModel: LeaderboardViewModel = hiltViewModel()) {
    val board by viewModel.board.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshMyScore() }
    LeaderboardSection(board = board, ui = ui, onBoard = viewModel::select, onRetry = viewModel::retry, modifier = modifier)
}

/**
 * Dost (friends, resets weekly) / India (everyone, resets monthly). Lives inside the Progress tab, not its own
 * destination — see domain/social/LeaderboardRepository for how the two boards and the resets work.
 */
@Composable
fun LeaderboardSection(
    board: LeaderboardBoard,
    ui: LeaderboardUi,
    onBoard: (LeaderboardBoard) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        NText(stringResource(R.string.leaderboard_title), 18.sp, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        BoardSwitch(board, onBoard)
        Spacer(Modifier.height(8.dp))
        KitCard(padding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
            when (ui) {
                LeaderboardUi.Loading -> LoadingRows()
                is LeaderboardUi.Error -> ErrorState(onRetry)
                is LeaderboardUi.Loaded -> if (ui.isEmpty) EmptyState(board) else LoadedBoard(ui)
            }
        }
    }
}

@Composable
private fun BoardSwitch(board: LeaderboardBoard, onBoard: (LeaderboardBoard) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).background(Kit.Surface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(LeaderboardBoard.FRIENDS_WEEKLY to R.string.leaderboard_tab_friends, LeaderboardBoard.INDIA_MONTHLY to R.string.leaderboard_tab_india)
            .forEach { (b, label) ->
                val on = b == board
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(shape)
                        .then(if (on) Modifier.background(Kit.Primary) else Modifier)
                        .clickable(role = Role.Tab) { if (!on) onBoard(b) }
                        .semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    NText(stringResource(label), 14.sp, color = if (on) Color.White else Neon.Muted, weight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
    }
}

@Composable
private fun LoadingRows() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Kit.Track))
                Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(Kit.Track))
            }
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NText(stringResource(R.string.leaderboard_error), 14.sp, color = Neon.Muted, align = TextAlign.Center, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        KitButton(stringResource(R.string.leaderboard_retry), onRetry, kind = KitButtonKind.Secondary)
    }
}

@Composable
private fun EmptyState(board: LeaderboardBoard) {
    val text = if (board == LeaderboardBoard.FRIENDS_WEEKLY) R.string.leaderboard_empty_friends else R.string.leaderboard_empty_india
    NText(stringResource(text), 14.sp, color = Neon.Muted, align = TextAlign.Center, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun LoadedBoard(ui: LeaderboardUi.Loaded) {
    val podium = ui.entries.take(3)
    val rest = ui.entries.drop(3)
    Column {
        if (podium.isNotEmpty()) Podium(podium)
        if (rest.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = if (podium.isNotEmpty()) 6.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                rest.forEachIndexed { index, entry -> LeaderboardRow(rank = index + 4, entry = entry) }
            }
        }
        // "Find my position": I have a rank but I'm not in the visible list above (e.g. outside India's top 50).
        // Never fabricated — only shown when the backend actually returned both a rank and an entry for me.
        if (ui.myEntry != null && ui.myRank != null && ui.entries.none { it.isMe }) {
            Spacer(Modifier.height(8.dp))
            NText(stringResource(R.string.leaderboard_your_rank), 11.sp, color = Neon.VioletLight, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(3.dp))
            LeaderboardRow(rank = ui.myRank, entry = ui.myEntry)
        }
    }
}

private data class MedalTone(val light: Color, val deep: Color, val label: Color)

private fun medalTone(rank: Int): MedalTone = when (rank) {
    1 -> MedalTone(Color(0xFFFFE9A8), Kit.Gold, Color(0xFF3A2400))
    2 -> MedalTone(Color(0xFFE4EAF7), Color(0xFF9FB4E0), Color(0xFF16213A))
    else -> MedalTone(Color(0xFFF0B98B), Color(0xFFD08A5A), Color(0xFF3A1E0A))
}

/** The top three as a compact, balanced podium: rank 1 in the middle with slightly more presence, 2 and 3 either
 * side — small enough that even on a 320dp phone the rest of the list is a swipe away, not several screens down. */
@Composable
private fun Podium(entries: List<LeaderboardEntry>) {
    val first = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        second?.let { PodiumPlace(rank = 2, entry = it, modifier = Modifier.weight(1f).padding(top = 10.dp)) } ?: Spacer(Modifier.weight(1f))
        first?.let { PodiumPlace(rank = 1, entry = it, modifier = Modifier.weight(1.05f)) } ?: Spacer(Modifier.weight(1.05f))
        third?.let { PodiumPlace(rank = 3, entry = it, modifier = Modifier.weight(1f).padding(top = 10.dp)) } ?: Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PodiumPlace(rank: Int, entry: LeaderboardEntry, modifier: Modifier = Modifier) {
    val tone = medalTone(rank)
    val avatarSize = if (rank == 1) 42.dp else 36.dp
    val spoken = stringResource(R.string.leaderboard_row_description, rank, entry.displayName, entry.coins)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (rank == 1) {
            DuelIcon(NeonIcons.Crown, tint = Kit.Gold, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.height(1.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(avatarSize + 4.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(tone.light, tone.deep)))
                    .then(if (entry.isMe) Modifier.border(2.dp, Kit.Pink, CircleShape) else Modifier),
            )
            KitAvatarPhoto(entry.displayName, entry.photoUrl, size = avatarSize, tone = if (entry.isMe) Kit.Pink else Kit.Blue)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(tone.deep)
                    .border(1.dp, Color(0xFF0B0E26), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                NText(rank.toString(), 9.sp, color = tone.label, weight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(4.dp))
        NText(entry.displayName, 11.sp, weight = FontWeight.ExtraBold, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        NText("@${entry.username}", 9.sp, color = Neon.Muted, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            DuelIcon(NeonIcons.Coin, tint = Kit.Gold, contentDescription = null, modifier = Modifier.size(10.dp))
            NText(entry.coins.toString(), 12.sp, weight = FontWeight.Black, color = Kit.Gold)
        }
    }
}

/** One compact row: rank, avatar, display name + username, score — a fixed, consistent height no matter the rank,
 * so many rows are scannable at once instead of each one eating the whole card. */
@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry) {
    val spoken = stringResource(R.string.leaderboard_row_description, rank, entry.displayName, entry.coins)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (entry.isMe) Modifier.background(Brush.horizontalGradient(listOf(Color(0x40FF4D8D), Color(0x1AFF4D8D)))) else Modifier)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            NText(rank.toString(), 13.sp, color = Neon.Muted, weight = FontWeight.ExtraBold)
        }
        KitAvatarPhoto(entry.displayName, entry.photoUrl, size = 30.dp, tone = if (entry.isMe) Kit.Pink else Kit.Blue)
        Column(Modifier.weight(1f)) {
            NText(entry.displayName, 13.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            NText("@${entry.username}", 10.sp, color = Neon.Muted, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            DuelIcon(NeonIcons.Coin, tint = Kit.Gold, contentDescription = null, modifier = Modifier.size(12.dp))
            NText(entry.coins.toString(), 14.sp, weight = FontWeight.Black, color = Kit.Gold)
        }
    }
}

@Preview(name = "Leaderboard loaded", widthDp = 390)
@Composable
private fun LeaderboardLoadedPreview() = ScreenPreview {
    val entries = listOf(
        LeaderboardEntry("1", "aman", "Aman", 480, false),
        LeaderboardEntry("2", "riya", "Riya", 360, false),
        LeaderboardEntry("3", "me", "Tum", 310, true),
        LeaderboardEntry("4", "vik", "Vikram", 120, false),
    )
    LeaderboardSection(
        board = LeaderboardBoard.FRIENDS_WEEKLY,
        ui = LeaderboardUi.Loaded(entries, myRank = 3, myEntry = entries[2]),
        onBoard = {}, onRetry = {},
    )
}

@Preview(name = "Leaderboard loading", widthDp = 390)
@Composable
private fun LeaderboardLoadingPreview() = ScreenPreview {
    LeaderboardSection(board = LeaderboardBoard.INDIA_MONTHLY, ui = LeaderboardUi.Loading, onBoard = {}, onRetry = {})
}

@Preview(name = "Leaderboard empty", widthDp = 390)
@Composable
private fun LeaderboardEmptyPreview() = ScreenPreview {
    LeaderboardSection(board = LeaderboardBoard.FRIENDS_WEEKLY, ui = LeaderboardUi.Loaded(emptyList(), null, null), onBoard = {}, onRetry = {})
}
