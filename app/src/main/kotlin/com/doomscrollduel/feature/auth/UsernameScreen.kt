package com.doomscrollduel.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.domain.model.UsernameProblem
import com.doomscrollduel.domain.social.ClaimResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitBottomButton
import com.doomscrollduel.feature.common.KitField
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

@Composable
fun UsernameRoute(modifier: Modifier = Modifier, viewModel: UsernameViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    UsernameScreen(ui = ui, onText = viewModel::onTextChange, onSave = viewModel::save, modifier = modifier)
}

/** Second step after Google login: pick the name friends will search for. Checked live, claimed once. */
@Composable
fun UsernameScreen(
    ui: UsernameUi,
    onText: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KitPage(
        modifier = modifier,
        bottom = {
            KitBottomButton(
                text = stringResource(if (ui.saving) R.string.username_saving else R.string.username_save),
                onClick = onSave,
                enabled = ui.canSave,
            )
        },
    ) {
        Spacer(Modifier.height(28.dp))
        NText(
            text = stringResource(R.string.username_title),
            size = 32.sp,
            weight = FontWeight.Black,
            lineHeight = 38.sp,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        NText(stringResource(R.string.username_sub), 15.sp, color = Neon.Muted, lineHeight = 22.sp)
        Spacer(Modifier.height(28.dp))
        val (message, tone) = status(ui)
        KitField(
            value = ui.text,
            onValueChange = onText,
            label = stringResource(R.string.username_label),
            hint = stringResource(R.string.username_hint),
            enabled = !ui.saving,
            error = tone == Kit.Red,
        )
        Spacer(Modifier.height(12.dp))
        if (message != null) {
            // Read out as it changes, so the person typing with TalkBack hears "free hai" or "le liya gaya".
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                NText(message, 14.sp, color = tone, weight = FontWeight.Bold, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
internal fun status(ui: UsernameUi): Pair<String?, Color> {
    ui.saveProblem?.let {
        return when (it) {
            ClaimResult.NoNetwork -> stringResource(R.string.username_offline) to Kit.Orange
            else -> stringResource(R.string.username_save_failed) to Kit.Red
        }
    }
    return when (val c = ui.check) {
        UsernameCheck.Empty -> null to Neon.Muted
        is UsernameCheck.Invalid -> stringResource(c.problem.message()) to Kit.Orange
        UsernameCheck.Checking -> stringResource(R.string.username_checking) to Neon.Muted
        is UsernameCheck.Available -> stringResource(R.string.username_available, c.name) to Kit.Green
        UsernameCheck.Taken -> stringResource(R.string.username_taken) to Kit.Red
        UsernameCheck.Offline -> stringResource(R.string.username_offline) to Kit.Orange
        UsernameCheck.Error -> stringResource(R.string.username_error) to Kit.Orange
    }
}

private fun UsernameProblem.message(): Int = when (this) {
    UsernameProblem.TOO_SHORT -> R.string.username_p_too_short
    UsernameProblem.TOO_LONG -> R.string.username_p_too_long
    UsernameProblem.BAD_CHARACTERS -> R.string.username_p_bad_characters
    UsernameProblem.MUST_START_WITH_LETTER -> R.string.username_p_start
    UsernameProblem.BAD_SEPARATORS -> R.string.username_p_separators
    UsernameProblem.RESERVED -> R.string.username_p_reserved
}

@Preview(name = "Username free", widthDp = 390, heightDp = 640)
@Composable
private fun UsernamePreview() = ScreenPreview {
    UsernameScreen(ui = UsernameUi(text = "vikas_07", check = UsernameCheck.Available("vikas_07")), onText = {}, onSave = {})
}
