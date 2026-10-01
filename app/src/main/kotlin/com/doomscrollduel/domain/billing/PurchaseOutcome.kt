package com.doomscrollduel.domain.billing

/** What the server answered when the app sent it a purchase token. The app never decides this itself. */
data class ServerVerdict(val status: EntitlementStatus, val accessUntilMs: Long, val serverNowMs: Long)

/** The result of one buy or restore attempt, in a form the paywall can print. */
sealed interface PurchaseOutcome {
    /** The server confirmed Pro. */
    data class Verified(val status: EntitlementStatus, val accessUntilMs: Long) : PurchaseOutcome

    /** Play is still taking the payment (UPI, cash, carrier billing). Pro starts only after it is confirmed. */
    data object Pending : PurchaseOutcome

    /** The person closed the Play sheet. Not an error. */
    data object Cancelled : PurchaseOutcome

    /** Restore found no subscription on this Google account. */
    data object NothingToRestore : PurchaseOutcome

    /** Buying needs a signed-in account, because the purchase is tied to it. */
    data object NotSignedIn : PurchaseOutcome

    data object NoNetwork : PurchaseOutcome

    /** Play is missing or too old, or the plan is not available in this country. */
    data object StoreUnavailable : PurchaseOutcome

    /** The purchase belongs to another account, or is not Pro. Nothing was unlocked. */
    data object Rejected : PurchaseOutcome

    data class Failed(val detail: String) : PurchaseOutcome
}

object PurchaseOutcomes {
    private val GRANTING = setOf(EntitlementStatus.ACTIVE, EntitlementStatus.CANCELED, EntitlementStatus.GRACE)

    /** A verdict that grants Pro is Verified; a payment still pending is Pending; anything else unlocked nothing. */
    fun fromVerdict(v: ServerVerdict): PurchaseOutcome = when (v.status) {
        in GRANTING -> if (v.accessUntilMs > v.serverNowMs) PurchaseOutcome.Verified(v.status, v.accessUntilMs) else PurchaseOutcome.Rejected
        EntitlementStatus.PENDING -> PurchaseOutcome.Pending
        else -> PurchaseOutcome.Rejected
    }

    /**
     * Several tokens can come back from a restore (an old expired one and the live one). The best result wins:
     * Verified, then Pending, then the most informative failure, then NothingToRestore.
     */
    fun best(outcomes: List<PurchaseOutcome>): PurchaseOutcome =
        outcomes.firstOrNull { it is PurchaseOutcome.Verified }
            ?: outcomes.firstOrNull { it is PurchaseOutcome.Pending }
            ?: outcomes.firstOrNull { it is PurchaseOutcome.NoNetwork || it is PurchaseOutcome.NotSignedIn }
            ?: outcomes.firstOrNull { it is PurchaseOutcome.Rejected }
            ?: outcomes.firstOrNull()
            ?: PurchaseOutcome.NothingToRestore
}

/** What the paywall tells the person after an attempt. Cancelling the Play sheet says nothing. */
enum class PurchaseMessage { VERIFIED, PENDING, NOT_SIGNED_IN, NO_NETWORK, STORE_UNAVAILABLE, REJECTED, NOTHING_TO_RESTORE, FAILED }

fun PurchaseOutcome.toMessage(): PurchaseMessage? = when (this) {
    is PurchaseOutcome.Verified -> PurchaseMessage.VERIFIED
    PurchaseOutcome.Pending -> PurchaseMessage.PENDING
    PurchaseOutcome.Cancelled -> null
    PurchaseOutcome.NothingToRestore -> PurchaseMessage.NOTHING_TO_RESTORE
    PurchaseOutcome.NotSignedIn -> PurchaseMessage.NOT_SIGNED_IN
    PurchaseOutcome.NoNetwork -> PurchaseMessage.NO_NETWORK
    PurchaseOutcome.StoreUnavailable -> PurchaseMessage.STORE_UNAVAILABLE
    PurchaseOutcome.Rejected -> PurchaseMessage.REJECTED
    is PurchaseOutcome.Failed -> PurchaseMessage.FAILED
}
