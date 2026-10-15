package com.doomscrollduel.feature.progress

import androidx.compose.foundation.background
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
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.domain.social.LeaderboardBoard
import com.doomscrollduel.domain.social.LeaderboardEntry
import com.doomscrollduel.domain.social.LeaderboardUi
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitAvatar
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

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
        Spacer(Modifier.height(10.dp))
        BoardSwitch(board, onBoard)
        Spacer(Modifier.height(10.dp))
        KitCard(padding = PaddingValues(14.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.entries.forEachIndexed { index, entry -> LeaderboardRow(rank = index + 1, entry = entry) }
        // "Find my position": I have a rank but I'm not in the visible list above (e.g. outside India's top 50).
        if (ui.myEntry != null && ui.myRank != null && ui.entries.none { it.isMe }) {
            Spacer(Modifier.height(2.dp))
            NText("· · ·", 16.sp, color = Neon.Muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))
            LeaderboardRow(rank = ui.myRank, entry = ui.myEntry)
        }
    }
}

@Composable
private fun LeaderboardRow(rank: Int, entry: LeaderboardEntry) {
    val medal = when (rank) {
        1 -> Kit.Gold
        2 -> Color(0xFFC7CEDB)
        3 -> Color(0xFFD08A5A)
        else -> null
    }
    val spoken = stringResource(R.string.leaderboard_row_description, rank, entry.displayName, entry.coins)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(if (entry.isMe) Modifier.background(Color(0x33FF4D8D)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (medal != null) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(medal), contentAlignment = Alignment.Center) {
                    NText(rank.toString(), 13.sp, color = Color(0xFF1A1430), weight = FontWeight.Black)
                }
            } else {
                NText(rank.toString(), 15.sp, color = Neon.Muted, weight = FontWeight.ExtraBold)
            }
        }
        KitAvatar(entry.displayName, size = 38.dp, tone = if (entry.isMe) Kit.Pink else Kit.Blue)
        Column(Modifier.weight(1f)) {
            NText(entry.displayName, 14.sp, weight = FontWeight.ExtraBold, maxLines = 1)
            NText("@${entry.username}", 11.sp, color = Neon.Muted, maxLines = 1)
        }
        NText(entry.coins.toString(), 15.sp, weight = FontWeight.Black, color = Kit.Gold)
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
