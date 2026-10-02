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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.doomscrollduel.core.designsystem.components.DuelIcon
import com.doomscrollduel.core.designsystem.components.ScreenPreview
import com.doomscrollduel.feature.common.Kit
import com.doomscrollduel.feature.common.KitButton
import com.doomscrollduel.feature.common.KitButtonKind
import com.doomscrollduel.feature.common.KitCard
import com.doomscrollduel.feature.common.KitIconDisc
import com.doomscrollduel.feature.common.KitPage
import com.doomscrollduel.feature.common.KitPill
import com.doomscrollduel.feature.settings.neon.NText
import com.doomscrollduel.feature.settings.neon.Neon
import com.doomscrollduel.feature.settings.neon.NeonIcons

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
    val isPro = ui.view.isPro
    val showOffers = !isPro && ui.view !is ProView.OnHold && ui.view !is ProView.Paused && ui.view !is ProView.PaymentPending
    val busy = ui.busy != PaywallBusy.NONE

    KitPage(
        modifier = modifier,
        title = stringResource(R.string.paywall_title),
        onBack = actions.onBack,
        bottomSpace = 250.dp,
        bottom = {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val ready = ui.store as? StoreState.Ready
                if (showOffers && ready != null) {
                    KitButton(
                        text = stringResource(if (ui.busy == PaywallBusy.BUYING) R.string.paywall_buy_busy else R.string.paywall_buy),
                        onClick = actions.onBuy,
                        enabled = !busy,
                        icon = NeonIcons.Crown,
                    )
                    // Right under the button, where Google Play expects the renewal terms to be visible.
                    NText(stringResource(R.string.paywall_cta_terms), 11.sp, color = Neon.Muted, align = TextAlign.Center, lineHeight = 14.sp, modifier = Modifier.fillMaxWidth())
                }
                if (!isPro || ui.view is ProView.Ending || ui.view is ProView.GracePeriod) {
                    // Always visible: someone who already paid (new phone, reinstall) must be able to find this at once.
                    KitButton(
                        text = stringResource(if (ui.busy == PaywallBusy.RESTORING) R.string.paywall_restore_busy else R.string.paywall_restore),
                        onClick = actions.onRestore,
                        enabled = !busy,
                        kind = KitButtonKind.Secondary,
                    )
                }
                if (ui.view !is ProView.Free && ui.view !is ProView.Expired && ui.view !is ProView.ClockSuspect) {
                    KitButton(
                        text = stringResource(
                            when (ui.view) {
                                is ProView.Ending -> R.string.paywall_status_ending_cta
                                is ProView.GracePeriod, ProView.OnHold -> R.string.paywall_status_grace_cta
                                else -> R.string.paywall_manage
                            },
                        ),
                        onClick = actions.onManage,
                    )
                }
            }
        },
    ) {
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
        NText(stringResource(R.string.paywall_free_keeps), 12.sp, color = Neon.Muted, lineHeight = 16.sp)

        if (showOffers) {
            SectionLabel(R.string.paywall_section_plans)
            when (val store = ui.store) {
                StoreState.Loading -> NText(stringResource(R.string.paywall_loading), 14.sp, color = Neon.Muted)
                is StoreState.Unavailable -> UnavailableCard(store.problem, actions.onRetryStore)
                is StoreState.Ready -> PlanCards(store.offers, ui.selected, actions.onSelectPlan)
            }
        }

        Spacer(Modifier.height(20.dp))
        NText(stringResource(R.string.paywall_fine_print), 12.sp, color = Neon.Muted, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        // Said plainly on purpose: Pro does not touch the in-app currency, and it cannot be bought.
        NText(stringResource(R.string.paywall_virtual_promise), 12.sp, color = Neon.Muted, lineHeight = 16.sp)
    }
}

// ----- pieces ------------------------------------------------------------------------------------

@Composable
private fun HeroCard(trigger: ProFeature?) {
    KitCard(
        radius = 26.dp,
        fill = Brush.horizontalGradient(listOf(Kit.Pink, Kit.VioletDeep)),
        edge = Color.Transparent,
        padding = PaddingValues(20.dp),
    ) {
        KitIconDisc(NeonIcons.Crown, Color.White, size = 48.dp)
        Spacer(Modifier.height(12.dp))
        NText(stringResource(R.string.paywall_hero_title), 24.sp, weight = FontWeight.Black, lineHeight = 29.sp)
        Spacer(Modifier.height(6.dp))
        NText(
            text = if (trigger != null) proFeatureReason(trigger) else stringResource(R.string.paywall_hero_sub),
            size = 15.sp,
            weight = FontWeight.SemiBold,
            lineHeight = 21.sp,
        )
    }
}

/** What is going on with the subscription right now. Nothing is shown for a plain free user. */
@Composable
private fun StatusCard(view: ProView) {
    val zone = remember { ZoneId.systemDefault() }
    fun date(ms: Long): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(zone))

    val (tone, text) = when (view) {
        ProView.Free -> return
        is ProView.Active -> Kit.Green to stringResource(R.string.paywall_status_active, date(view.renewsAtMs))
        is ProView.Ending -> Kit.Orange to stringResource(R.string.paywall_status_ending, date(view.endsAtMs))
        is ProView.GracePeriod -> Kit.Orange to stringResource(R.string.paywall_status_grace, date(view.untilMs))
        ProView.OnHold -> Kit.Red to stringResource(R.string.paywall_status_hold)
        ProView.Paused -> Kit.Orange to stringResource(R.string.paywall_status_paused)
        ProView.PaymentPending -> Kit.Blue to stringResource(R.string.paywall_status_pending)
        ProView.Expired -> Kit.Violet to stringResource(R.string.paywall_status_expired)
        ProView.ClockSuspect -> Kit.Red to stringResource(R.string.paywall_status_clock)
    }
    KitCard(
        fill = Brush.horizontalGradient(listOf(tone.copy(alpha = 0.2f), tone.copy(alpha = 0.2f))),
        edge = tone.copy(alpha = 0.5f),
    ) {
        NText(text, 14.sp, weight = FontWeight.Bold, lineHeight = 19.sp)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun BenefitsCard() {
    KitCard(padding = PaddingValues(16.dp)) {
        val items = listOf(
            R.string.benefit_unlimited_duels,
            R.string.benefit_squad,
            R.string.benefit_strict_lock,
            R.string.benefit_analytics,
            R.string.benefit_schedules,
            R.string.benefit_skins,
        )
        items.forEachIndexed { index, res ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KitIconDisc(NeonIcons.Check, Kit.Green, size = 28.dp)
                NText(stringResource(res), 14.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f), lineHeight = 19.sp)
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
    val name = stringResource(if (offer.plan == ProPlan.YEARLY) R.string.plan_yearly else R.string.plan_monthly)
    val price = stringResource(if (offer.plan == ProPlan.YEARLY) R.string.plan_per_year else R.string.plan_per_month, offer.formattedPrice)
    val extra = perMonth?.let { stringResource(R.string.plan_yearly_per_month, it) }.orEmpty()
    val spoken = stringResource(R.string.plan_card_description, name, price, extra)

    KitCard(
        modifier = Modifier.semantics { this.selected = selected; contentDescription = spoken },
        radius = 22.dp,
        fill = if (selected) Brush.horizontalGradient(listOf(Kit.Gold.copy(alpha = 0.22f), Kit.Surface)) else Brush.verticalGradient(listOf(Kit.Surface, Kit.Surface)),
        edge = if (selected) Kit.Gold else Kit.Edge,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.clearAndSetSemantics { }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NText(name, 18.sp, weight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                if (savingPercent != null) KitPill(stringResource(R.string.plan_saving, savingPercent), tone = Kit.Green, filled = true)
            }
            Spacer(Modifier.height(4.dp))
            NText(price, 15.sp, weight = FontWeight.Bold)
            if (perMonth != null) NText(extra, 12.sp, color = Neon.Muted)
            if (selected) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuelIcon(NeonIcons.Check, tint = Kit.Gold, contentDescription = null, modifier = Modifier.size(18.dp))
                    NText(stringResource(R.string.plan_selected), 12.sp, color = Kit.Gold, weight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun UnavailableCard(problem: StoreProblem, onRetry: () -> Unit) {
    KitCard {
        NText(
            text = stringResource(
                when (problem) {
                    StoreProblem.PLAY_MISSING -> R.string.store_problem_play_missing
                    StoreProblem.NO_NETWORK -> R.string.store_problem_no_network
                    StoreProblem.NO_PLANS -> R.string.store_problem_no_plans
                },
            ),
            size = 14.sp,
            weight = FontWeight.Bold,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(10.dp))
        KitButton(stringResource(R.string.paywall_retry), onRetry, kind = KitButtonKind.Secondary)
    }
}

@Composable
private fun MessageCard(message: PurchaseMessage, onDismiss: () -> Unit) {
    val good = message == PurchaseMessage.VERIFIED || message == PurchaseMessage.PENDING
    val tone = if (good) Kit.Green else Kit.Red
    KitCard(
        fill = Brush.horizontalGradient(listOf(tone.copy(alpha = 0.2f), tone.copy(alpha = 0.2f))),
        edge = tone.copy(alpha = 0.5f),
    ) {
        NText(
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
            size = 14.sp,
            weight = FontWeight.Bold,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(10.dp))
        KitButton(stringResource(R.string.paywall_msg_dismiss), onDismiss, kind = KitButtonKind.Secondary)
    }
}

@Composable
private fun SectionLabel(textRes: Int) {
    NText(
        text = stringResource(textRes),
        size = 15.sp,
        weight = FontWeight.ExtraBold,
        color = Neon.VioletLight,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
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
private fun PaywallProPreview() = ScreenPreview { PaywallScreen(previewState(view = ProView.Active(ProPlan.YEARLY, 1_900_000_000_000L)), NoActions) }
