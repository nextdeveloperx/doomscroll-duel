package com.doomscrollduel.blocking.unlock

import com.doomscrollduel.domain.challenge.PlayerId

/** The outcome of asking a friend for an unlock. */
sealed interface AskResult {
    /** Reached the server and the friend was notified. This one counts towards the 3 per 24 hours. */
    data class Sent(val requestId: String, val serverNowMs: Long) : AskResult

    /** No connection. Nothing reached the server, so it does not count. */
    data object NoNetwork : AskResult

    /** The server says 3 requests are already used in the last 24 hours. */
    data object QuotaUsed : AskResult

    data object AlreadyPending : AskResult

    /** The chosen person is not a friend any more. */
    data object NoBuddy : AskResult

    data object NotSignedIn : AskResult

    data object Failed : AskResult
}

enum class RespondOutcome { DONE, NO_NETWORK, EXPIRED, NOT_ALLOWED, FAILED }

/**
 * The server side of friend unlock (Cloud Functions `requestUnlock` and `respondUnlock`). The server enforces
 * the 3 per 24 hours, that only a friend can answer, the 30 minute expiry, and it sends the push notifications.
 */
interface UnlockRepository {
    suspend fun request(buddy: PlayerId): AskResult

    /** The friend taps Approve or Deny on the notification. */
    suspend fun respond(requestId: String, approve: Boolean): RespondOutcome

    /** Keeps the phone reachable for pushes: called when Firebase gives a new token. */
    suspend fun registerPushToken(token: String)
}
