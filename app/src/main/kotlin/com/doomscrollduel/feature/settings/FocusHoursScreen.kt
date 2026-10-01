package com.doomscrollduel.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.BackButton
import com.doomscrollduel.core.designsystem.components.ChoiceChip
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.feature.paywall.proFeatureReason
import com.doomscrollduel.domain.blocking.FocusScheduleEditor
import com.doomscrollduel.domain.blocking.FocusSettings
import com.doomscrollduel.domain.blocking.TimeFormat
import com.doomscrollduel.domain.blocking.TimeRange
import com.doomscrollduel.domain.blocking.TimeRanges
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun FocusHoursRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FocusHoursViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    FocusHoursScreen(
        ui = ui,
        onBack = onBack,
        onSelectDay = viewModel::selectDay,
        onAdd = viewModel::addRange,
        onNudge = viewModel::nudge,
        onRemove = viewModel::remove,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

@Composable
fun FocusHoursScreen(
    ui: FocusUiState,
    onBack: () -> Unit,
    onSelectDay: (DayOfWeek) -> Unit,
    onAdd: () -> Unit,
    onNudge: (index: Int, moveStart: Boolean, deltaMinutes: Long) -> Unit,
    onRemove: (index: Int) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    val ranges = ui.focus.rangesOn(ui.selectedDay)

    DuelScreen(
        modifier = modifier,
        bottom = {
            ChunkyButton(
                text = stringResource(R.string.focus_add_range),
                onClick = onAdd,
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
            DuelText(text = stringResource(R.string.focus_title), style = DuelTheme.typography.title)
        }
        Spacer(Modifier.height(12.dp))
        DuelText(text = stringResource(R.string.focus_intro), style = DuelTheme.typography.bodyStrong)
        if (!ui.focus.enabled) {
            Spacer(Modifier.height(8.dp))
            DuelText(text = stringResource(R.string.focus_off_hint), style = DuelTheme.typography.caption, color = colors.orange)
        }

        ui.message?.let { message ->
            Spacer(Modifier.height(12.dp))
            ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = colors.red) {
                DuelText(text = messageText(message), style = DuelTheme.typography.bodyStrong, color = colors.onBright)
                Spacer(Modifier.height(8.dp))
                ChunkyButton(
                    text = stringResource(R.string.settings_message_ok),
                    onClick = onDismissMessage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DayOfWeek.entries.forEach { day ->
                ChoiceChip(
                    text = stringResource(day.labelRes()),
                    selected = day == ui.selectedDay,
                    onClick = { onSelectDay(day) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (ranges.isEmpty()) {
            DuelText(text = stringResource(R.string.focus_empty_day), style = DuelTheme.typography.body, color = colors.textMuted)
        }
        ranges.forEachIndexed { index, range ->
            RangeCard(
                range = range,
                dayLabel = stringResource(ui.selectedDay.labelRes()),
                onNudge = { moveStart, delta -> onNudge(index, moveStart, delta) },
                onRemove = { onRemove(index) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RangeCard(range: TimeRange, dayLabel: String, onNudge: (moveStart: Boolean, deltaMinutes: Long) -> Unit, onRemove: () -> Unit) {
    val step = TimeRanges.STEP_MINUTES
    ChunkyCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DuelText(
                text = stringResource(R.string.focus_range_label, TimeFormat.time12(range.start), TimeFormat.time12(range.end)),
                style = DuelTheme.typography.heading,
            )
            TimeStepper(stringResource(R.string.settings_bedtime_start), range.start, true, { onNudge(true, -step) }, { onNudge(true, step) })
            TimeStepper(stringResource(R.string.settings_bedtime_end), range.end, true, { onNudge(false, -step) }, { onNudge(false, step) })
            ChunkyButton(
                text = stringResource(R.string.focus_remove),
                onClick = onRemove,
                style = ChunkyButtonStyle.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun messageText(message: FocusMessage): String = when (message) {
    is FocusMessage.Invalid -> stringResource(
        when (message.problem) {
            FocusScheduleEditor.Problem.TOO_SHORT -> R.string.focus_problem_short
            FocusScheduleEditor.Problem.TOO_MANY_RANGES -> R.string.focus_problem_many
            FocusScheduleEditor.Problem.OVERLAPS -> R.string.focus_problem_overlap
        },
    )
    is FocusMessage.Running -> stringResource(R.string.focus_running, TimeFormat.words(message.remainingMs))
    FocusMessage.NeedsPro -> proFeatureReason(ProFeature.CUSTOM_SCHEDULES)
}

private fun DayOfWeek.labelRes(): Int = when (this) {
    DayOfWeek.MONDAY -> R.string.day_mon
    DayOfWeek.TUESDAY -> R.string.day_tue
    DayOfWeek.WEDNESDAY -> R.string.day_wed
    DayOfWeek.THURSDAY -> R.string.day_thu
    DayOfWeek.FRIDAY -> R.string.day_fri
    DayOfWeek.SATURDAY -> R.string.day_sat
    DayOfWeek.SUNDAY -> R.string.day_sun
}

private val PreviewFocus = FocusSettings(
    enabled = true,
    days = mapOf(
        DayOfWeek.MONDAY to listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(11, 0)), TimeRange(LocalTime.of(16, 0), LocalTime.of(19, 0))),
        DayOfWeek.FRIDAY to listOf(TimeRange(LocalTime.of(22, 0), LocalTime.of(2, 0))),
    ),
)

@Preview(name = "Focus hours Monday 390x844", widthDp = 390, heightDp = 844)
@Composable
private fun FocusHoursPreview() = ScreenPreview {
    FocusHoursScreen(FocusUiState(PreviewFocus, DayOfWeek.MONDAY, null), {}, {}, {}, { _, _, _ -> }, {}, {})
}

@Preview(name = "Focus hours empty day with problem", widthDp = 320, heightDp = 640)
@Composable
private fun FocusHoursEmptyPreview() = ScreenPreview {
    FocusHoursScreen(
        FocusUiState(PreviewFocus.copy(enabled = false), DayOfWeek.WEDNESDAY, FocusMessage.Invalid(FocusScheduleEditor.Problem.OVERLAPS)),
        {}, {}, {}, { _, _, _ -> }, {}, {},
    )
}
