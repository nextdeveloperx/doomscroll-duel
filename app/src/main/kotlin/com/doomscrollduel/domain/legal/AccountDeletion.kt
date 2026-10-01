package com.doomscrollduel.domain.legal

/** What the person must do before the Delete button works: type the word and tick the box. No timer tricks. */
object DeleteConfirmation {
    const val WORD = "DELETE"

    fun wordMatches(typed: String): Boolean = typed.trim().equals(WORD, ignoreCase = true)

    fun canDelete(typed: String, understood: Boolean, busy: Boolean): Boolean = wordMatches(typed) && understood && !busy
}

/** How a deletion attempt ended, in a form the screen can print. */
enum class DeletionResult {
    /** Everything on the server is gone and the person is signed out. */
    DELETED,

    /** The server wants a sign-in from the last few minutes first. Nothing was deleted. */
    NEEDS_RECENT_LOGIN,

    NO_NETWORK,
    NOT_SIGNED_IN,

    /** Something failed part way. Nothing about the account is lost; trying again finishes the job. */
    FAILED,
}

/**
 * Things the person is told BEFORE confirming. Kept as data so the screen, the web page and the Play Console answers
 * say the same thing (see docs/play/account-deletion-page.md).
 */
object DeletionScope {
    val removed = listOf(
        "profile",
        "username",
        "friends_list",
        "daily_reel_counts_on_server",
        "duel_history",
        "unlock_requests",
        "coin_balance",
        "pro_entitlement_record",
        "purchase_token",
        "push_tokens",
        "dare_proofs",
    )

    /** Honest limits: what deleting the account does NOT do. */
    val notRemoved = listOf(
        "play_subscription_billing",
        "other_players_own_records",
        "data_on_this_phone_settings",
    )
}
