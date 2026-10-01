package com.doomscrollduel.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.core.common.openSubscriptionManagement
import com.doomscrollduel.core.designsystem.components.BackButton
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.ChunkyTextField
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.DuelIcons
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.legal.DeletionResult

class DeleteAccountActions(
    val onBack: () -> Unit,
    val onTyped: (String) -> Unit,
    val onUnderstood: (Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
    val onManageSubscription: () -> Unit,
    val onFinished: () -> Unit,
)

@Composable
fun DeleteAccountRoute(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val actions = DeleteAccountActions(
        onBack = onBack,
        onTyped = viewModel::onTyped,
        onUnderstood = viewModel::onUnderstood,
        onDelete = viewModel::delete,
        onDismissError = viewModel::dismissError,
        onManageSubscription = { context.openSubscriptionManagement() },
        onFinished = onFinished,
    )
    DeleteAccountScreen(ui, actions, modifier)
}

/**
 * Delete account. Three honest steps on one screen: read what goes (and what does not), type the word and tick the
 * box, then watch it run. Nothing is hidden behind a timer or a second "are you sure?" maze, and leaving is always easy
 * until the job is running.
 */
@Composable
fun DeleteAccountScreen(
    ui: DeleteAccountUiState,
    actions: DeleteAccountActions,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    val working = ui.phase == DeletePhase.Working
    val done = ui.phase == DeletePhase.Done
    // While the job runs, Back would leave the person unsure what happened, so it is ignored until it ends.
    BackHandler(enabled = working) { }
    BackHandler(enabled = done, onBack = actions.onFinished)

    DuelScreen(
        modifier = modifier,
        bottom = {
            when {
                done -> ChunkyButton(text = stringResource(R.string.del_done_action), onClick = actions.onFinished, modifier = Modifier.fillMaxWidth())
                else -> {
                    ChunkyButton(
                        text = stringResource(R.string.del_button),
                        onClick = actions.onDelete,
                        enabled = ui.canDelete,
                        style = ChunkyButtonStyle.Danger,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ChunkyButton(
                        text = stringResource(R.string.del_cancel),
                        onClick = actions.onBack,
                        enabled = !working,
                        style = ChunkyButtonStyle.Success,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!working && !done) BackButton(onClick = actions.onBack)
            DuelText(
                text = stringResource(R.string.del_title),
                style = DuelTheme.typography.title.copy(fontSize = 26.sp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
        }
        Spacer(Modifier.height(12.dp))

        when (val phase = ui.phase) {
            DeletePhase.Done -> {
                ChunkyCard(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }, fill = colors.green) {
                    DuelText(text = stringResource(R.string.del_done_title), style = DuelTheme.typography.heading, color = colors.onBright)
                    Spacer(Modifier.height(6.dp))
                    DuelText(text = stringResource(R.string.del_done_body), style = DuelTheme.typography.bodyStrong, color = colors.onBright)
                }
                if (ui.hasSubscription) SubscriptionCard(actions.onManageSubscription)
            }
            DeletePhase.Working -> {
                ChunkyCard(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, fill = colors.yellow) {
                    DuelText(text = stringResource(R.string.del_working), style = DuelTheme.typography.heading, color = colors.onBright)
                }
            }
            else -> {
                if (phase is DeletePhase.Error) {
                    ChunkyCard(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }, fill = colors.red) {
                        DuelText(text = errorText(phase.result), style = DuelTheme.typography.bodyStrong, color = colors.onBright)
                        Spacer(Modifier.height(8.dp))
                        ChunkyButton(text = stringResource(R.string.paywall_msg_dismiss), onClick = actions.onDismissError, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                }
                DuelText(text = stringResource(R.string.del_intro), style = DuelTheme.typography.bodyStrong)
                Spacer(Modifier.height(16.dp))
                ListCard(R.string.del_removed_title, colors.red, listOf(R.string.del_removed_1, R.string.del_removed_2, R.string.del_removed_3, R.string.del_removed_4))
                Spacer(Modifier.height(12.dp))
                ListCard(R.string.del_kept_title, colors.cyan, listOf(R.string.del_kept_1, R.string.del_kept_2))
                if (ui.hasSubscription) {
                    Spacer(Modifier.height(12.dp))
                    SubscriptionCard(actions.onManageSubscription)
                }
                Spacer(Modifier.height(16.dp))
                DuelText(text = stringResource(R.string.del_confirm_label), style = DuelTheme.typography.bodyStrong)
                Spacer(Modifier.height(8.dp))
                ChunkyTextField(
                    value = ui.typed,
                    onValueChange = actions.onTyped,
                    label = stringResource(R.string.del_confirm_label),
                    hint = stringResource(R.string.del_confirm_hint),
                )
                Spacer(Modifier.height(12.dp))
                CheckRow(checked = ui.understood, text = stringResource(R.string.del_check), onChange = actions.onUnderstood)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun errorText(result: DeletionResult): String = stringResource(
    when (result) {
        DeletionResult.NEEDS_RECENT_LOGIN -> R.string.del_err_recent_login
        DeletionResult.NO_NETWORK -> R.string.del_err_no_network
        DeletionResult.NOT_SIGNED_IN -> R.string.del_err_not_signed_in
        DeletionResult.FAILED, DeletionResult.DELETED -> R.string.del_err_failed
    },
)

@Composable
private fun ListCard(titleRes: Int, accent: androidx.compose.ui.graphics.Color, lines: List<Int>) {
    ChunkyCard(modifier = Modifier.fillMaxWidth()) {
        DuelText(
            text = stringResource(titleRes),
            style = DuelTheme.typography.heading.copy(fontSize = 20.sp),
            modifier = Modifier.semantics { heading() },
        )
        lines.forEach {
            Spacer(Modifier.height(8.dp))
            DuelText(text = "•  " + stringResource(it), style = DuelTheme.typography.body)
        }
    }
}

@Composable
private fun SubscriptionCard(onManage: () -> Unit) {
    val colors = DuelTheme.colors
    ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = colors.orange) {
        DuelText(text = stringResource(R.string.del_pro_title), style = DuelTheme.typography.heading.copy(fontSize = 20.sp), color = colors.onBright)
        Spacer(Modifier.height(6.dp))
        DuelText(text = stringResource(R.string.del_pro_body), style = DuelTheme.typography.bodyStrong, color = colors.onBright)
        Spacer(Modifier.height(10.dp))
        ChunkyButton(text = stringResource(R.string.del_pro_action), onClick = onManage, style = ChunkyButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

/** A tick box with its text. The whole row is the touch target and TalkBack reads it as a checkbox. */
@Composable
private fun CheckRow(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    val colors = DuelTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp)
            .heightIn(min = ChunkyMetrics.MinTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(28.dp)
                .clip(DuelTheme.shapes.chip)
                .background(if (checked) colors.yellow else colors.surface)
                .border(ChunkyMetrics.OutlineWidth, colors.outline, DuelTheme.shapes.chip),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) DuelIcon(DuelIcons.Check, tint = colors.onBright, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DuelText(text = text, style = DuelTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
    }
}

private val NoActions = DeleteAccountActions({}, {}, {}, {}, {}, {}, {})

@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun DeleteFormPreview() = ScreenPreview { DeleteAccountScreen(DeleteAccountUiState(hasSubscription = true), NoActions) }

@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun DeleteReadyPreview() = ScreenPreview { DeleteAccountScreen(DeleteAccountUiState(typed = "delete", understood = true), NoActions) }

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun DeleteErrorPreview() = ScreenPreview {
    DeleteAccountScreen(DeleteAccountUiState(phase = DeletePhase.Error(DeletionResult.NEEDS_RECENT_LOGIN)), NoActions)
}

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun DeleteDonePreview() = ScreenPreview { DeleteAccountScreen(DeleteAccountUiState(phase = DeletePhase.Done, hasSubscription = true), NoActions) }
