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
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.domain.analytics.UsageDataChoice
import com.doomscrollduel.domain.analytics.UsageDataChoiceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

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
    KitCard(
        modifier = modifier,
        radius = 22.dp,
        fill = Brush.horizontalGradient(listOf(Kit.Blue.copy(alpha = 0.22f), Kit.Surface)),
        edge = Kit.Blue.copy(alpha = 0.4f),
    ) {
        NText(
            text = stringResource(R.string.usage_card_title),
            size = 17.sp,
            weight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(6.dp))
        NText(stringResource(R.string.usage_card_body), 13.sp, color = Neon.Muted, lineHeight = 19.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            KitButton(stringResource(R.string.usage_card_decline), onDecline, kind = KitButtonKind.Secondary, modifier = Modifier.weight(1f))
            KitButton(stringResource(R.string.usage_card_allow), onAllow, modifier = Modifier.weight(1f))
        }
    }
}

@Preview(widthDp = 390, heightDp = 320)
@Composable
private fun UsageDataCardPreview() = ScreenPreview { UsageDataCard(onAllow = {}, onDecline = {}) }
