package com.doomscrollduel.feature.legal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import com.doomscrollduel.R
import com.doomscrollduel.core.common.openAccessibilitySettings
import com.doomscrollduel.core.common.openUrl
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon

/**
 * The Accessibility API prominent disclosure, shown before the permission is requested.
 *
 * Rules this screen keeps (Google Play "Prominent Disclosure and Consent"):
 *  - it is a full screen of its own, not a dialog inside another flow and not a link to a policy;
 *  - it says what the service does, what it does not do, and what happens to the data, in plain words;
 *  - the person must tap AGREE; there is no timer, no auto-close and no pre-selected answer;
 *  - NOT NOW and the Back button are the same thing: nothing is recorded and the permission is not requested;
 *  - AGREE and NOT NOW are the same size so neither is hidden.
 *
 * [reviewOnly] re-opens the same text from Settings for someone who already agreed: one button, nothing is requested.
 */
@Composable
fun DisclosureRoute(
    reviewOnly: Boolean,
    onClose: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DisclosureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    DisclosureScreen(
        reviewOnly = reviewOnly,
        onAgree = {
            // The agreement is saved first; only then is the system screen opened.
            if (viewModel.agree()) context.openAccessibilitySettings()
            onClose()
        },
        onNotNow = onClose,
        onOpenPrivacy = onOpenPrivacy,
        modifier = modifier,
    )
}

@Composable
fun DisclosureScreen(
    reviewOnly: Boolean,
    onAgree: () -> Unit,
    onNotNow: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back means "not now": the person is never forced into the permission.
    BackHandler(onBack = onNotNow)

    KitPage(
        modifier = modifier,
        bottomSpace = if (reviewOnly) 92.dp else 170.dp,
        bottom = {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reviewOnly) {
                    KitButton(stringResource(R.string.disc_back), onNotNow)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        KitButton(stringResource(R.string.disc_not_now), onNotNow, kind = KitButtonKind.Secondary, modifier = Modifier.weight(1f))
                        KitButton(stringResource(R.string.disc_agree), onAgree, modifier = Modifier.weight(1f))
                    }
                    NText(stringResource(R.string.disc_not_now_note), 12.sp, color = Neon.Muted, align = TextAlign.Center, lineHeight = 16.sp, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) {
        Spacer(Modifier.height(12.dp))
        NText(
            text = stringResource(R.string.disc_title),
            size = 28.sp,
            weight = FontWeight.Black,
            lineHeight = 34.sp,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        NText(stringResource(R.string.disc_intro), 15.sp, weight = FontWeight.Bold, lineHeight = 22.sp)

        Block(R.string.disc_does_title, Kit.Green, listOf(R.string.disc_does_1, R.string.disc_does_2, R.string.disc_does_3))
        Block(R.string.disc_not_title, Kit.Red, listOf(R.string.disc_not_1, R.string.disc_not_2, R.string.disc_not_3))
        Block(R.string.disc_data_title, Kit.Blue, listOf(R.string.disc_data_1, R.string.disc_data_2, R.string.disc_data_3))

        Spacer(Modifier.height(16.dp))
        // Reviewers and many users read English first, so the same promise is repeated in English.
        KitCard(fill = Brush.horizontalGradient(listOf(Kit.Violet.copy(alpha = 0.22f), Kit.Surface)), edge = Kit.Violet.copy(alpha = 0.4f)) {
            NText(
                text = stringResource(R.string.disc_english_title),
                size = 18.sp,
                weight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            NText(stringResource(R.string.disc_english_body), 14.sp, lineHeight = 20.sp)
        }

        Spacer(Modifier.height(8.dp))
        BasicText(
            text = stringResource(R.string.disc_privacy_link),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = com.doomscrollduel.core.designsystem.theme.Nunito,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Neon.VioletLight,
                textDecoration = TextDecoration.Underline,
            ),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onOpenPrivacy)
                .padding(vertical = 14.dp),
        )
    }
}

@Composable
private fun Block(titleRes: Int, accent: Color, lineRes: List<Int>) {
    Spacer(Modifier.height(16.dp))
    KitCard(radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Colour is only decoration: every block has a written heading and every line is plain text.
            Box(Modifier.height(22.dp).width(5.dp).clip(RoundedCornerShape(50)).background(accent))
            NText(
                text = stringResource(titleRes),
                size = 18.sp,
                weight = FontWeight.ExtraBold,
                modifier = Modifier.semantics { heading() },
            )
        }
        lineRes.forEach {
            Spacer(Modifier.height(8.dp))
            NText(stringResource(it), 14.sp, lineHeight = 20.sp)
        }
    }
}

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun DisclosurePreview() = ScreenPreview { DisclosureScreen(reviewOnly = false, onAgree = {}, onNotNow = {}, onOpenPrivacy = {}) }
