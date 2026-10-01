package com.doomscrollduel.feature.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.analytics.UsageDataChoice
import com.doomscrollduel.domain.analytics.UsageDataChoiceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class UsageDataPromptViewModel @Inject constructor(private val store: UsageDataChoiceStore) : ViewModel() {
    val choice: StateFlow<UsageDataChoice> = store.choice

    fun answer(allowed: Boolean) = store.set(if (allowed) UsageDataChoice.ALLOWED else UsageDataChoice.DECLINED)
}

/**
 * The one-time question about anonymous analytics and crash reports. Both answers are the same size and equally easy;
 * "Nahi" is not hidden and nothing in the app changes if it is chosen. It is asked on Home, never as a blocking screen.
 */
@Composable
fun UsageDataCard(onAllow: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DuelTheme.colors
    ChunkyCard(modifier = modifier.fillMaxWidth(), fill = colors.cyan) {
        DuelText(
            text = stringResource(R.string.usage_card_title),
            style = DuelTheme.typography.heading.copy(fontSize = 20.sp),
            color = colors.onBright,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        DuelText(text = stringResource(R.string.usage_card_body), style = DuelTheme.typography.body, color = colors.onBright)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ChunkyButton(
                text = stringResource(R.string.usage_card_decline),
                onClick = onDecline,
                style = ChunkyButtonStyle.Secondary,
                modifier = Modifier.weight(1f),
            )
            ChunkyButton(
                text = stringResource(R.string.usage_card_allow),
                onClick = onAllow,
                style = ChunkyButtonStyle.Success,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 320)
@Composable
private fun UsageDataCardPreview() = ScreenPreview { UsageDataCard(onAllow = {}, onDecline = {}) }
