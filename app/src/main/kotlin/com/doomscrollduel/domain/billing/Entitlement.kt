package com.doomscrollduel.domain.billing

/** The two Pro plans. Both are base plans of one Play subscription product. */
enum class ProPlan { MONTHLY, YEARLY }

/**
 * What the SERVER says about a person's subscription. Written only by the Cloud Function that checked the purchase
 * with the Google Play Developer API; the app reads it and never writes it.
 *
 * | status | meaning | Pro features |
 * |---|---|---|
 * | NONE | never subscribed | no |
 * | PENDING | payment started but not confirmed yet (slow UPI / cash payment) | no |
 * | ACTIVE | paid, renews on its own | yes |
 * | CANCELED | user switched off renewal, paid time is left | yes, until [Entitlement.accessUntilMs] |
 * | GRACE | renewal payment failed, Google is retrying | yes, until [Entitlement.accessUntilMs] |
 * | ON_HOLD | grace ran out, payment still failing | no |
 * | PAUSED | user paused the subscription in Play | no |
 * | EXPIRED | ended (also: refunded or revoked) | no |
 */
enum class EntitlementStatus { NONE, PENDING, ACTIVE, CANCELED, GRACE, ON_HOLD, PAUSED, EXPIRED }

/**
 * [accessUntilMs] is when Pro access ends (server clock, epoch ms). [verifiedAtMs] is when the server last checked
 * with Google. [autoRenewing] tells whether the plan will continue after [accessUntilMs].
 */
data class Entitlement(
    val status: EntitlementStatus = EntitlementStatus.NONE,
    val plan: ProPlan? = null,
    val accessUntilMs: Long = 0L,
    val autoRenewing: Boolean = false,
    val verifiedAtMs: Long = 0L,
) {
    companion object {
        val None = Entitlement()
    }
}

/** What the app shows and what it unlocks. One value for the whole app, from [Entitlements.view]. */
sealed interface ProView {
    val isPro: Boolean

    data object Free : ProView { override val isPro = false }

    /** Paid and renewing. */
    data class Active(val plan: ProPlan?, val renewsAtMs: Long) : ProView { override val isPro = true }

    /** Renewal is off; Pro ends at [endsAtMs]. Show "Pro khatam hone wala hai" and a way to resubscribe. */
    data class Ending(val plan: ProPlan?, val endsAtMs: Long) : ProView { override val isPro = true }

    /** Renewal payment failed but Google is still retrying. Pro keeps working; ask them to fix the payment. */
    data class GracePeriod(val plan: ProPlan?, val untilMs: Long) : ProView { override val isPro = true }

    /** Payment still failing after grace. Pro is off until they fix it; one tap to the Play payment screen. */
    data object OnHold : ProView { override val isPro = false }

    data object Paused : ProView { override val isPro = false }

    /** A payment is waiting for confirmation. No Pro yet, and no second purchase needed. */
    data object PaymentPending : ProView { override val isPro = false }

    /** Used to be Pro, ended. Show the offer again, softly. */
    data object Expired : ProView { override val isPro = false }

    /**
     * The phone's clock is more than a day BEHIND the last time the server checked, so the stored entitlement
     * cannot be trusted (turning the clock back would make Pro last for ever). Pro stays off until the phone is
     * online and the clock is right.
     */
    data object ClockSuspect : ProView { override val isPro = false }
}

object Entitlements {
    /** A clock this far behind the server's last check is treated as tampered. */
    const val CLOCK_SKEW_TOLERANCE_MS = 24L * 60 * 60 * 1000

    /**
     * An auto-renewing plan keeps working this long past its stored end, so a renewal the phone has not heard about
     * yet (offline, push delayed) does not switch Pro off for a paying user. Only applies to ACTIVE + auto-renewing.
     */
    const val RENEWAL_SLACK_MS = 3L * 24 * 60 * 60 * 1000

    fun view(e: Entitlement, nowMs: Long): ProView {
        val usable = e.status in setOf(EntitlementStatus.ACTIVE, EntitlementStatus.CANCELED, EntitlementStatus.GRACE)
        if (usable && nowMs < e.verifiedAtMs - CLOCK_SKEW_TOLERANCE_MS) return ProView.ClockSuspect
        return when (e.status) {
            EntitlementStatus.NONE -> ProView.Free
            EntitlementStatus.PENDING -> ProView.PaymentPending
            EntitlementStatus.ON_HOLD -> ProView.OnHold
            EntitlementStatus.PAUSED -> ProView.Paused
            EntitlementStatus.EXPIRED -> ProView.Expired
            EntitlementStatus.ACTIVE -> when {
                e.autoRenewing && nowMs < e.accessUntilMs + RENEWAL_SLACK_MS -> ProView.Active(e.plan, e.accessUntilMs)
                !e.autoRenewing && nowMs < e.accessUntilMs -> ProView.Ending(e.plan, e.accessUntilMs)
                else -> ProView.Expired
            }
            EntitlementStatus.CANCELED ->
                if (nowMs < e.accessUntilMs) ProView.Ending(e.plan, e.accessUntilMs) else ProView.Expired
            EntitlementStatus.GRACE ->
                if (nowMs < e.accessUntilMs) ProView.GracePeriod(e.plan, e.accessUntilMs) else ProView.OnHold
        }
    }

    /** Milliseconds until [view] could change by time alone, or null when it never will. For scheduling a re-check. */
    fun nextChangeInMs(e: Entitlement, nowMs: Long): Long? {
        val at = when (e.status) {
            EntitlementStatus.ACTIVE -> e.accessUntilMs + if (e.autoRenewing) RENEWAL_SLACK_MS else 0L
            EntitlementStatus.CANCELED, EntitlementStatus.GRACE -> e.accessUntilMs
            else -> return null
        }
        return (at - nowMs).takeIf { it > 0 }
    }
}
