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
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import com.doomscrollduel.R
import com.doomscrollduel.core.common.openAccessibilitySettings
import com.doomscrollduel.core.common.openUrl
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.theme.ChunkyMetrics
import com.doomscrollduel.core.designsystem.theme.DuelTheme

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
        onOpenPrivacy = { context.openUrl(context.getString(R.string.privacy_policy_url)) },
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
    val colors = DuelTheme.colors
    // Back means "not now": the person is never forced into the permission.
    BackHandler(onBack = onNotNow)

    DuelScreen(
        modifier = modifier,
        bottom = {
            if (reviewOnly) {
                ChunkyButton(text = stringResource(R.string.disc_back), onClick = onNotNow, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ChunkyButton(
                        text = stringResource(R.string.disc_not_now),
                        onClick = onNotNow,
                        style = ChunkyButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    ChunkyButton(
                        text = stringResource(R.string.disc_agree),
                        onClick = onAgree,
                        style = ChunkyButtonStyle.Success,
                        modifier = Modifier.weight(1f),
                    )
                }
                DuelText(
                    text = stringResource(R.string.disc_not_now_note),
                    style = DuelTheme.typography.caption,
                    color = colors.textMuted,
                )
            }
        },
    ) {
        DuelText(
            text = stringResource(R.string.disc_title),
            style = DuelTheme.typography.title.copy(fontSize = 26.sp),
            modifier = Modifier
                .padding(top = 20.dp)
                .semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        DuelText(text = stringResource(R.string.disc_intro), style = DuelTheme.typography.bodyStrong)

        Block(R.string.disc_does_title, colors.green, listOf(R.string.disc_does_1, R.string.disc_does_2, R.string.disc_does_3))
        Block(R.string.disc_not_title, colors.red, listOf(R.string.disc_not_1, R.string.disc_not_2, R.string.disc_not_3))
        Block(R.string.disc_data_title, colors.cyan, listOf(R.string.disc_data_1, R.string.disc_data_2, R.string.disc_data_3))

        Spacer(Modifier.height(16.dp))
        // Reviewers and many users read English first, so the same promise is repeated in English.
        ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = colors.lavender) {
            DuelText(
                text = stringResource(R.string.disc_english_title),
                style = DuelTheme.typography.heading.copy(fontSize = 20.sp),
                color = colors.onBright,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            DuelText(text = stringResource(R.string.disc_english_body), style = DuelTheme.typography.body, color = colors.onBright)
        }

        Spacer(Modifier.height(16.dp))
        DuelText(
            text = stringResource(R.string.disc_privacy_link),
            style = DuelTheme.typography.bodyStrong.copy(textDecoration = TextDecoration.Underline),
            color = colors.cyan,
            modifier = Modifier
                .sizeIn(minHeight = ChunkyMetrics.MinTouchTarget)
                .clickable(role = Role.Button, onClick = onOpenPrivacy)
                .padding(vertical = 12.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Block(titleRes: Int, accent: androidx.compose.ui.graphics.Color, lineRes: List<Int>) {
    val colors = DuelTheme.colors
    Spacer(Modifier.height(16.dp))
    ChunkyCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Colour is only decoration: every block has a written heading and every line is plain text.
            androidx.compose.foundation.layout.Box(
                Modifier
                    .height(24.dp)
                    .width(8.dp)
                    .background(accent),
            )
            DuelText(
                text = stringResource(titleRes),
                style = DuelTheme.typography.heading.copy(fontSize = 20.sp),
                modifier = Modifier.semantics { heading() },
            )
        }
        lineRes.forEach {
            Spacer(Modifier.height(8.dp))
            DuelText(text = stringResource(it), style = DuelTheme.typography.body, color = colors.text)
        }
    }
}

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun DisclosurePreview() = ScreenPreview { DisclosureScreen(reviewOnly = false, onAgree = {}, onNotNow = {}, onOpenPrivacy = {}) }

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun DisclosureReviewPreview() = ScreenPreview { DisclosureScreen(reviewOnly = true, onAgree = {}, onNotNow = {}, onOpenPrivacy = {}) }
