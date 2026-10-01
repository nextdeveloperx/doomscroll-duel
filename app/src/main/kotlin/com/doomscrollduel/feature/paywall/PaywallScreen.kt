package com.doomscrollduel.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doomscrollduel.R
import com.doomscrollduel.billing.StoreProblem
import com.doomscrollduel.billing.StoreState
import com.doomscrollduel.core.common.openSubscriptionManagement
import com.doomscrollduel.core.designsystem.components.BackButton
import com.doomscrollduel.core.designsystem.components.ChunkyButton
import com.doomscrollduel.core.designsystem.components.ChunkyButtonStyle
import com.doomscrollduel.core.designsystem.components.ChunkyCard
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.DuelIcons
import com.doomscrollduel.core.designsystem.components.DuelScreen
import com.doomscrollduel.core.designsystem.components.DuelText
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.core.designsystem.components.StatusPill
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.billing.PlanOffer
import com.doomscrollduel.domain.billing.PlanPricing
import com.doomscrollduel.domain.billing.ProFeature
import com.doomscrollduel.domain.billing.ProPlan
import com.doomscrollduel.domain.billing.ProView
import com.doomscrollduel.domain.billing.PurchaseMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class PaywallActions(
    val onBack: () -> Unit,
    val onSelectPlan: (ProPlan) -> Unit,
    val onBuy: () -> Unit,
    val onRestore: () -> Unit,
    val onRetryStore: () -> Unit,
    val onManage: () -> Unit,
    val onDismissMessage: () -> Unit,
)

@Composable
fun PaywallRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val actions = PaywallActions(
        onBack = onBack,
        onSelectPlan = viewModel::select,
        onBuy = { context.findActivity()?.let(viewModel::buy) },
        onRestore = viewModel::restore,
        onRetryStore = viewModel::retryStore,
        onManage = { context.openSubscriptionManagement() },
        onDismissMessage = viewModel::dismissMessage,
    )
    PaywallScreen(ui = ui, actions = actions, modifier = modifier)
}

@Composable
fun PaywallScreen(
    ui: PaywallUiState,
    actions: PaywallActions,
    modifier: Modifier = Modifier,
) {
    val colors = DuelTheme.colors
    val isPro = ui.view.isPro
    val showOffers = !isPro && ui.view !is ProView.OnHold && ui.view !is ProView.Paused && ui.view !is ProView.PaymentPending
    val busy = ui.busy != PaywallBusy.NONE

    DuelScreen(
        modifier = modifier,
        bottom = {
            val ready = ui.store as? StoreState.Ready
            if (showOffers && ready != null) {
                ChunkyButton(
                    text = stringResource(if (ui.busy == PaywallBusy.BUYING) R.string.paywall_buy_busy else R.string.paywall_buy),
                    onClick = actions.onBuy,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!isPro || ui.view is ProView.Ending || ui.view is ProView.GracePeriod) {
                // Always visible: someone who already paid (new phone, reinstall) must be able to find this at once.
                ChunkyButton(
                    text = stringResource(if (ui.busy == PaywallBusy.RESTORING) R.string.paywall_restore_busy else R.string.paywall_restore),
                    onClick = actions.onRestore,
                    enabled = !busy,
                    style = ChunkyButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (ui.view !is ProView.Free && ui.view !is ProView.Expired && ui.view !is ProView.ClockSuspect) {
                ChunkyButton(
                    text = stringResource(
                        when (ui.view) {
                            is ProView.Ending -> R.string.paywall_status_ending_cta
                            is ProView.GracePeriod, ProView.OnHold -> R.string.paywall_status_grace_cta
                            else -> R.string.paywall_manage
                        },
                    ),
                    onClick = actions.onManage,
                    style = ChunkyButtonStyle.Success,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            BackButton(onClick = actions.onBack)
            DuelText(
                text = stringResource(R.string.paywall_title),
                style = DuelTheme.typography.title.copy(fontSize = 26.sp),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))

        ui.message?.let {
            MessageCard(it, actions.onDismissMessage)
            Spacer(Modifier.height(16.dp))
        }

        HeroCard(trigger = ui.trigger)
        Spacer(Modifier.height(16.dp))
        StatusCard(ui.view)

        SectionLabel(R.string.paywall_section_benefits)
        BenefitsCard()
        Spacer(Modifier.height(8.dp))
        DuelText(text = stringResource(R.string.paywall_free_keeps), style = DuelTheme.typography.caption, color = colors.textMuted)

        if (showOffers) {
            SectionLabel(R.string.paywall_section_plans)
            when (val store = ui.store) {
                StoreState.Loading -> DuelText(text = stringResource(R.string.paywall_loading), style = DuelTheme.typography.body, color = colors.textMuted)
                is StoreState.Unavailable -> UnavailableCard(store.problem, actions.onRetryStore)
                is StoreState.Ready -> PlanCards(store.offers, ui.selected, actions.onSelectPlan)
            }
        }

        Spacer(Modifier.height(20.dp))
        DuelText(text = stringResource(R.string.paywall_fine_print), style = DuelTheme.typography.caption, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        // Said plainly on purpose: Pro does not touch the in-app currency, and it cannot be bought.
        DuelText(text = stringResource(R.string.paywall_virtual_promise), style = DuelTheme.typography.caption, color = colors.textMuted)
        Spacer(Modifier.height(16.dp))
    }
}

// ----- pieces ------------------------------------------------------------------------------------

@Composable
private fun HeroCard(trigger: ProFeature?) {
    val colors = DuelTheme.colors
    ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = colors.pink) {
        DuelText(
            text = stringResource(R.string.paywall_hero_title),
            style = DuelTheme.typography.heading,
            color = colors.onBright,
        )
        Spacer(Modifier.height(4.dp))
        DuelText(
            text = if (trigger != null) proFeatureReason(trigger) else stringResource(R.string.paywall_hero_sub),
            style = DuelTheme.typography.bodyStrong,
            color = colors.onBright,
        )
    }
}

/** What is going on with the subscription right now. Nothing is shown for a plain free user. */
@Composable
private fun StatusCard(view: ProView) {
    val colors = DuelTheme.colors
    val zone = remember { ZoneId.systemDefault() }
    fun date(ms: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))

    val (fill, text) = when (view) {
        ProView.Free -> return
        is ProView.Active -> colors.green to stringResource(R.string.paywall_status_active, date(view.renewsAtMs))
        is ProView.Ending -> colors.orange to stringResource(R.string.paywall_status_ending, date(view.endsAtMs))
        is ProView.GracePeriod -> colors.orange to stringResource(R.string.paywall_status_grace, date(view.untilMs))
        ProView.OnHold -> colors.red to stringResource(R.string.paywall_status_hold)
        ProView.Paused -> colors.orange to stringResource(R.string.paywall_status_paused)
        ProView.PaymentPending -> colors.cyan to stringResource(R.string.paywall_status_pending)
        ProView.Expired -> colors.lavender to stringResource(R.string.paywall_status_expired)
        ProView.ClockSuspect -> colors.red to stringResource(R.string.paywall_status_clock)
    }
    ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = fill) {
        DuelText(text = text, style = DuelTheme.typography.bodyStrong, color = colors.onBright)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun BenefitsCard() {
    ChunkyCard(modifier = Modifier.fillMaxWidth()) {
        val items = listOf(
            R.string.benefit_unlimited_duels,
            R.string.benefit_squad,
            R.string.benefit_strict_lock,
            R.string.benefit_analytics,
            R.string.benefit_schedules,
            R.string.benefit_skins,
        )
        items.forEachIndexed { index, res ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DuelIcon(DuelIcons.Check, tint = DuelTheme.colors.green, contentDescription = null, modifier = Modifier.size(22.dp))
                DuelText(text = stringResource(res), style = DuelTheme.typography.bodyStrong, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PlanCards(offers: List<PlanOffer>, selected: ProPlan, onSelect: (ProPlan) -> Unit) {
    val saving = PlanPricing.yearlySavingPercent(offers)
    val perMonth = PlanPricing.yearlyPerMonthText(offers, Locale.getDefault())
    // Yearly first: it is the better deal and says so honestly (the percentage comes from Play's own prices).
    offers.sortedByDescending { it.plan == ProPlan.YEARLY }.forEachIndexed { index, offer ->
        if (index > 0) Spacer(Modifier.height(12.dp))
        PlanCard(
            offer = offer,
            selected = offer.plan == selected,
            savingPercent = if (offer.plan == ProPlan.YEARLY) saving else null,
            perMonth = if (offer.plan == ProPlan.YEARLY) perMonth else null,
            onClick = { onSelect(offer.plan) },
        )
    }
}

@Composable
private fun PlanCard(offer: PlanOffer, selected: Boolean, savingPercent: Int?, perMonth: String?, onClick: () -> Unit) {
    val colors = DuelTheme.colors
    val ink = if (selected) colors.onBright else colors.text
    val name = stringResource(if (offer.plan == ProPlan.YEARLY) R.string.plan_yearly else R.string.plan_monthly)
    val price = stringResource(if (offer.plan == ProPlan.YEARLY) R.string.plan_per_year else R.string.plan_per_month, offer.formattedPrice)
    val extra = perMonth?.let { stringResource(R.string.plan_yearly_per_month, it) }.orEmpty()
    val spoken = stringResource(R.string.plan_card_description, name, price, extra)

    ChunkyCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected; contentDescription = spoken },
        fill = if (selected) colors.yellow else colors.surface,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.clearAndSetSemantics { }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DuelText(text = name, style = DuelTheme.typography.heading, color = ink, modifier = Modifier.weight(1f))
                if (savingPercent != null) {
                    StatusPill(text = stringResource(R.string.plan_saving, savingPercent), fill = colors.green)
                }
            }
            Spacer(Modifier.height(4.dp))
            DuelText(text = price, style = DuelTheme.typography.bodyStrong, color = ink)
            if (perMonth != null) DuelText(text = extra, style = DuelTheme.typography.caption, color = ink)
            if (selected) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuelIcon(DuelIcons.Check, tint = ink, contentDescription = null, modifier = Modifier.size(18.dp))
                    DuelText(text = stringResource(R.string.plan_selected), style = DuelTheme.typography.captionStrong, color = ink)
                }
            }
        }
    }
}

@Composable
private fun UnavailableCard(problem: StoreProblem, onRetry: () -> Unit) {
    val colors = DuelTheme.colors
    ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = colors.surface) {
        DuelText(
            text = stringResource(
                when (problem) {
                    StoreProblem.PLAY_MISSING -> R.string.store_problem_play_missing
                    StoreProblem.NO_NETWORK -> R.string.store_problem_no_network
                    StoreProblem.NO_PLANS -> R.string.store_problem_no_plans
                },
            ),
            style = DuelTheme.typography.bodyStrong,
        )
        Spacer(Modifier.height(10.dp))
        ChunkyButton(text = stringResource(R.string.paywall_retry), onClick = onRetry, style = ChunkyButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MessageCard(message: PurchaseMessage, onDismiss: () -> Unit) {
    val colors = DuelTheme.colors
    val good = message == PurchaseMessage.VERIFIED || message == PurchaseMessage.PENDING
    ChunkyCard(modifier = Modifier.fillMaxWidth(), fill = if (good) colors.green else colors.red) {
        DuelText(
            text = stringResource(
                when (message) {
                    PurchaseMessage.VERIFIED -> R.string.paywall_msg_verified
                    PurchaseMessage.PENDING -> R.string.paywall_msg_pending
                    PurchaseMessage.NOT_SIGNED_IN -> R.string.paywall_msg_not_signed_in
                    PurchaseMessage.NO_NETWORK -> R.string.paywall_msg_no_network
                    PurchaseMessage.STORE_UNAVAILABLE -> R.string.paywall_msg_store_unavailable
                    PurchaseMessage.REJECTED -> R.string.paywall_msg_rejected
                    PurchaseMessage.NOTHING_TO_RESTORE -> R.string.paywall_msg_nothing_to_restore
                    PurchaseMessage.FAILED -> R.string.paywall_msg_failed
                },
            ),
            style = DuelTheme.typography.bodyStrong,
            color = colors.onBright,
        )
        Spacer(Modifier.height(8.dp))
        ChunkyButton(text = stringResource(R.string.paywall_msg_dismiss), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionLabel(textRes: Int) {
    DuelText(
        text = stringResource(textRes),
        style = DuelTheme.typography.captionStrong,
        color = DuelTheme.colors.textMuted,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ----- previews ----------------------------------------------------------------------------------

private val PreviewOffers = listOf(
    PlanOffer(ProPlan.MONTHLY, "₹99.00", 99_000_000, "INR", "m"),
    PlanOffer(ProPlan.YEARLY, "₹799.00", 799_000_000, "INR", "y"),
)
private val NoActions = PaywallActions({}, {}, {}, {}, {}, {}, {})

private fun previewState(
    view: ProView = ProView.Free,
    store: StoreState = StoreState.Ready(PreviewOffers),
    selected: ProPlan = ProPlan.YEARLY,
    busy: PaywallBusy = PaywallBusy.NONE,
    message: PurchaseMessage? = null,
    trigger: ProFeature? = null,
) = PaywallUiState(store, view, selected, busy, message, trigger)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PaywallFreePreview() = ScreenPreview { PaywallScreen(previewState(trigger = ProFeature.SQUAD_BATTLE), NoActions) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PaywallMonthlySelectedPreview() = ScreenPreview { PaywallScreen(previewState(selected = ProPlan.MONTHLY), NoActions) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PaywallOfflinePreview() = ScreenPreview { PaywallScreen(previewState(store = StoreState.Unavailable(StoreProblem.NO_NETWORK)), NoActions) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PaywallProPreview() = ScreenPreview { PaywallScreen(previewState(view = ProView.Active(ProPlan.YEARLY, 1_900_000_000_000L)), NoActions) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PaywallGracePreview() = ScreenPreview { PaywallScreen(previewState(view = ProView.GracePeriod(ProPlan.MONTHLY, 1_900_000_000_000L)), NoActions) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PaywallPendingPreview() = ScreenPreview { PaywallScreen(previewState(message = PurchaseMessage.PENDING, busy = PaywallBusy.NONE), NoActions) }
