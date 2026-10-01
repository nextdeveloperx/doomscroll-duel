package com.doomscrollduel.feature.duel.create

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.brain.BrainOwner
import com.doomscrollduel.core.designsystem.brain.BrainState
import com.doomscrollduel.core.designsystem.brain.BrainView
import com.doomscrollduel.core.designsystem.components.BackButton
import com.doomscrollduel.core.designsystem.components.ChoiceChip
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.HardShadowText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.components.StepperButton
import com.doomscrollduel.core.designsystem.components.StepperKind
import com.doomscrollduel.core.designsystem.components.VsBadge
import com.doomscrollduel.core.designsystem.components.scaled
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.feature.FakeData

enum class DurationOption(val hours: Int) {
    SIX_HOURS(6),
    ONE_DAY(24),
    SEVEN_DAYS(168),
}

val StakeOptions = listOf(25, 50, 100)

const val MinReelLimit = 10
const val MaxReelLimit = 500
const val ReelLimitStep = 10

@Immutable
data class NewDuelUiState(
    val friends: List<String>,
    val selectedFriendIndex: Int,
    val reelLimit: Int,
    val duration: DurationOption,
    val stakeCoins: Int,
) {
    val friendName: String get() = friends.getOrElse(selectedFriendIndex) { friends.firstOrNull().orEmpty() }
}

/** Holds the form while the screen is open; survives rotation and process death. */
@Composable
fun NewDuelRoute(
    onBack: () -> Unit,
    onSend: (NewDuelUiState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = FakeData.newDuel
    var friendIndex by rememberSaveable { mutableIntStateOf(initial.selectedFriendIndex) }
    var limit by rememberSaveable { mutableIntStateOf(initial.reelLimit) }
    var durationOrdinal by rememberSaveable { mutableIntStateOf(initial.duration.ordinal) }
    var stake by rememberSaveable { mutableIntStateOf(initial.stakeCoins) }
    val state = initial.copy(
        selectedFriendIndex = friendIndex,
        reelLimit = limit,
        duration = DurationOption.entries[durationOrdinal],
        stakeCoins = stake,
    )

    NewDuelScreen(
        state = state,
        onFriendSelected = { friendIndex = it },
        onLimitChange = { limit = it.coerceIn(MinReelLimit, MaxReelLimit) },
        onDurationSelected = { durationOrdinal = it.ordinal },
        onStakeSelected = { stake = it },
        onBack = onBack,
        onSend = { onSend(state) },
        modifier = modifier,
    )
}

@Composable
fun NewDuelScreen(
    state: NewDuelUiState,
    onFriendSelected: (Int) -> Unit,
    onLimitChange: (Int) -> Unit,
    onDurationSelected: (DurationOption) -> Unit,
    onStakeSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DuelScreen(
        modifier = modifier,
        bottom = {
            ChunkyButton(
                text = stringResource(R.string.nd_send),
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackButton(onClick = onBack)
            DuelText(text = stringResource(R.string.nd_title), style = DuelTheme.typography.title)
        }

        VersusHeader(friendName = state.friendName)

        SectionLabel(R.string.nd_section_friend)
        ChipRow {
            state.friends.forEachIndexed { index, friend ->
                ChoiceChip(
                    text = friend,
                    selected = index == state.selectedFriendIndex,
                    onClick = { onFriendSelected(index) },
                )
            }
        }

        SectionLabel(R.string.nd_section_limit)
        LimitStepper(limit = state.reelLimit, onLimitChange = onLimitChange)

        SectionLabel(R.string.nd_section_time)
        ChipRow {
            DurationOption.entries.forEach { option ->
                ChoiceChip(
                    text = stringResource(option.labelRes()),
                    selected = option == state.duration,
                    onClick = { onDurationSelected(option) },
                )
            }
        }

        SectionLabel(R.string.nd_section_stake)
        ChipRow {
            StakeOptions.forEach { coins ->
                ChoiceChip(
                    text = stringResource(R.string.nd_stake_option, coins),
                    selected = coins == state.stakeCoins,
                    onClick = { onStakeSelected(coins) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        ChunkyCard(modifier = Modifier.fillMaxWidth()) {
            DuelText(
                text = stringResource(R.string.nd_explain, state.reelLimit, state.stakeCoins, state.friendName),
                style = DuelTheme.typography.bodyStrong,
            )
            Spacer(Modifier.height(6.dp))
            DuelText(
                text = stringResource(R.string.nd_virtual_note),
                style = DuelTheme.typography.caption,
                color = DuelTheme.colors.textMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun DurationOption.labelRes(): Int = when (this) {
    DurationOption.SIX_HOURS -> R.string.nd_duration_6h
    DurationOption.ONE_DAY -> R.string.nd_duration_24h
    DurationOption.SEVEN_DAYS -> R.string.nd_duration_7d
}

@Composable
private fun VersusHeader(friendName: String) {
    val colors = DuelTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        HeaderBrain(
            name = stringResource(R.string.label_you),
            nameColor = colors.pink,
            owner = BrainOwner.YOU,
        )
        VsBadge(Modifier.padding(horizontal = 8.dp))
        HeaderBrain(name = friendName, nameColor = colors.cyan, owner = BrainOwner.OPPONENT)
    }
}

@Composable
private fun HeaderBrain(name: String, nameColor: Color, owner: BrainOwner) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BrainView(BrainState.HAPPY, Modifier.width(112.dp.scaled()), owner)
        DuelText(
            text = name,
            style = DuelTheme.typography.bodyStrong,
            color = nameColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 120.dp),
        )
    }
}

@Composable
private fun SectionLabel(textRes: Int) {
    DuelText(
        text = stringResource(textRes),
        style = DuelTheme.typography.captionStrong,
        color = DuelTheme.colors.textMuted,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/** Chips scroll sideways instead of wrapping, so narrow phones never push the layout around. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun LimitStepper(limit: Int, onLimitChange: (Int) -> Unit) {
    val spoken = stringResource(R.string.nd_limit_value, limit)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        StepperButton(
            kind = StepperKind.Decrement,
            onClick = { onLimitChange(limit - ReelLimitStep) },
            contentDescription = stringResource(R.string.nd_limit_decrease),
            enabled = limit > MinReelLimit,
        )
        HardShadowText(
            text = limit.toString(),
            fontSize = 64.sp.scaled(),
            modifier = Modifier
                .widthIn(min = 140.dp.scaled())
                .semantics { contentDescription = spoken },
        )
        StepperButton(
            kind = StepperKind.Increment,
            onClick = { onLimitChange(limit + ReelLimitStep) },
            contentDescription = stringResource(R.string.nd_limit_increase),
            enabled = limit < MaxReelLimit,
        )
    }
}

@Preview(name = "New duel 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun NewDuelPreview() = ScreenPreview {
    NewDuelScreen(
        state = FakeData.newDuel,
        onFriendSelected = {}, onLimitChange = {}, onDurationSelected = {}, onStakeSelected = {},
        onBack = {}, onSend = {},
    )
}

@Preview(name = "New duel small 320x640", widthDp = 320, heightDp = 640)
@Composable
private fun NewDuelSmallPreview() = ScreenPreview {
    NewDuelScreen(
        state = FakeData.newDuel.copy(selectedFriendIndex = 2, reelLimit = 250, duration = DurationOption.SEVEN_DAYS, stakeCoins = 100),
        onFriendSelected = {}, onLimitChange = {}, onDurationSelected = {}, onStakeSelected = {},
        onBack = {}, onSend = {},
    )
}
