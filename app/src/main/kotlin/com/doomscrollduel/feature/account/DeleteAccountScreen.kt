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
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.domain.legal.DeletionResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitField
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons
import com.doomscrollduel.core.designsystem.components.DuelIcon

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
    val working = ui.phase == DeletePhase.Working
    val done = ui.phase == DeletePhase.Done
    // While the job runs, Back would leave the person unsure what happened, so it is ignored until it ends.
    BackHandler(enabled = working) { }
    BackHandler(enabled = done, onBack = actions.onFinished)

    KitPage(
        modifier = modifier,
        title = stringResource(R.string.del_title),
        onBack = if (!working && !done) actions.onBack else null,
        bottomSpace = if (done) 92.dp else 170.dp,
        bottom = {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (done) {
                    KitButton(stringResource(R.string.del_done_action), actions.onFinished)
                } else {
                    KitButton(stringResource(R.string.del_button), actions.onDelete, enabled = ui.canDelete, kind = KitButtonKind.Danger)
                    KitButton(stringResource(R.string.del_cancel), actions.onBack, enabled = !working, kind = KitButtonKind.Secondary)
                }
            }
        },
    ) {
        when (val phase = ui.phase) {
            DeletePhase.Done -> {
                KitCard(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    fill = Brush.horizontalGradient(listOf(Kit.Green.copy(alpha = 0.2f), Kit.Green.copy(alpha = 0.2f))),
                    edge = Kit.Green.copy(alpha = 0.5f),
                ) {
                    NText(stringResource(R.string.del_done_title), 20.sp, weight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    NText(stringResource(R.string.del_done_body), 14.sp, weight = FontWeight.SemiBold, lineHeight = 20.sp)
                }
                if (ui.hasSubscription) {
                    Spacer(Modifier.height(12.dp))
                    SubscriptionCard(actions.onManageSubscription)
                }
            }
            DeletePhase.Working -> {
                KitCard(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    fill = Brush.horizontalGradient(listOf(Kit.Gold.copy(alpha = 0.2f), Kit.Gold.copy(alpha = 0.2f))),
                    edge = Kit.Gold.copy(alpha = 0.5f),
                ) {
                    NText(stringResource(R.string.del_working), 18.sp, weight = FontWeight.ExtraBold)
                }
            }
            else -> {
                if (phase is DeletePhase.Error) {
                    KitCard(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                        fill = Brush.horizontalGradient(listOf(Kit.Red.copy(alpha = 0.2f), Kit.Red.copy(alpha = 0.2f))),
                        edge = Kit.Red.copy(alpha = 0.5f),
                    ) {
                        NText(errorText(phase.result), 14.sp, weight = FontWeight.Bold, lineHeight = 19.sp)
                        Spacer(Modifier.height(10.dp))
                        KitButton(stringResource(R.string.paywall_msg_dismiss), actions.onDismissError, kind = KitButtonKind.Secondary)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                NText(stringResource(R.string.del_intro), 15.sp, weight = FontWeight.Bold, lineHeight = 22.sp)
                Spacer(Modifier.height(16.dp))
                ListCard(R.string.del_removed_title, Kit.Red, listOf(R.string.del_removed_1, R.string.del_removed_2, R.string.del_removed_3, R.string.del_removed_4))
                Spacer(Modifier.height(12.dp))
                ListCard(R.string.del_kept_title, Kit.Blue, listOf(R.string.del_kept_1, R.string.del_kept_2))
                if (ui.hasSubscription) {
                    Spacer(Modifier.height(12.dp))
                    SubscriptionCard(actions.onManageSubscription)
                }
                Spacer(Modifier.height(20.dp))
                KitField(
                    value = ui.typed,
                    onValueChange = actions.onTyped,
                    label = stringResource(R.string.del_confirm_label),
                    hint = stringResource(R.string.del_confirm_hint),
                )
                Spacer(Modifier.height(12.dp))
                CheckRow(checked = ui.understood, text = stringResource(R.string.del_check), onChange = actions.onUnderstood)
            }
        }
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
private fun ListCard(titleRes: Int, accent: Color, lines: List<Int>) {
    KitCard(radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.height(22.dp).width(5.dp).clip(RoundedCornerShape(50)).background(accent))
            NText(
                text = stringResource(titleRes),
                size = 17.sp,
                weight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
        }
        lines.forEach {
            Spacer(Modifier.height(8.dp))
            NText("•  " + stringResource(it), 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun SubscriptionCard(onManage: () -> Unit) {
    KitCard(
        fill = Brush.horizontalGradient(listOf(Kit.Orange.copy(alpha = 0.2f), Kit.Orange.copy(alpha = 0.2f))),
        edge = Kit.Orange.copy(alpha = 0.5f),
    ) {
        NText(stringResource(R.string.del_pro_title), 17.sp, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        NText(stringResource(R.string.del_pro_body), 14.sp, weight = FontWeight.SemiBold, lineHeight = 20.sp)
        Spacer(Modifier.height(10.dp))
        KitButton(stringResource(R.string.del_pro_action), onManage, kind = KitButtonKind.Secondary)
    }
}

/** A tick box with its text. The whole row is the touch target and TalkBack reads it as a checkbox. */
@Composable
private fun CheckRow(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (checked) Kit.Pink else Kit.Surface)
                .border(1.dp, if (checked) Kit.Pink else Kit.Violet.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) DuelIcon(NeonIcons.Check, tint = Color.White, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        NText(text, 14.sp, weight = FontWeight.Bold, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

private val NoActions = DeleteAccountActions({}, {}, {}, {}, {}, {}, {})

@Preview(widthDp = 390, heightDp = 1300)
@Composable
private fun DeleteFormPreview() = ScreenPreview { DeleteAccountScreen(DeleteAccountUiState(hasSubscription = true), NoActions) }

@Preview(widthDp = 390, heightDp = 700)
@Composable
private fun DeleteDonePreview() = ScreenPreview { DeleteAccountScreen(DeleteAccountUiState(phase = DeletePhase.Done, hasSubscription = true), NoActions) }
