package com.doomscrollduel.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doomscrollduel.R
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.tracking.health.TrackingIssue

/**
 * Warning card shown while something stops reels from being counted. Red when counting is off,
 * orange when the battery manager may stop it. Renders nothing for [TrackingIssue.NONE].
 */
@Composable
fun TrackingBanner(
    issue: TrackingIssue,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (issue == TrackingIssue.NONE) return
    val colors = DuelTheme.colors
    val needsConsent = issue == TrackingIssue.CONSENT_NEEDED
    val isOff = issue == TrackingIssue.ACCESSIBILITY_OFF || needsConsent
    ChunkyCard(
        modifier = modifier
            .fillMaxWidth()
            // Read out as soon as it appears, so a TalkBack user learns counting stopped.
            .semantics { liveRegion = LiveRegionMode.Assertive },
        fill = if (isOff) colors.red else colors.orange,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DuelText(
                text = stringResource(if (needsConsent) R.string.banner_consent_title else if (isOff) R.string.banner_off_title else R.string.banner_battery_title),
                style = DuelTheme.typography.heading,
                color = colors.onBright,
            )
            DuelText(
                text = stringResource(if (needsConsent) R.string.banner_consent_body else if (isOff) R.string.banner_off_body else R.string.banner_battery_body),
                style = DuelTheme.typography.bodyStrong,
                color = colors.onBright,
            )
            ChunkyButton(
                text = stringResource(if (needsConsent) R.string.banner_consent_action else if (isOff) R.string.banner_off_action else R.string.banner_battery_action),
                onClick = onFix,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "TrackingBanner", widthDp = 390)
@Composable
private fun TrackingBannerPreview() = DuelPreview {
    TrackingBanner(TrackingIssue.ACCESSIBILITY_OFF, onFix = {})
    TrackingBanner(TrackingIssue.CONSENT_NEEDED, onFix = {})
    TrackingBanner(TrackingIssue.BATTERY_RESTRICTED, onFix = {})
}
