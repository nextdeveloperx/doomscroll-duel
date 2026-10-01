package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.PlayerId
import java.time.Duration
import java.time.Instant

/**
 * FRIEND UNLOCK. The user asks ONE chosen friend. The friend gets a push notification with Approve and Deny.
 * Approval gives a 15 minute pass that lifts the timer-lock and any bedtime or focus block.
 *
 * Limits:
 *  - at most 3 requests in any rolling 24 hours (not calendar days: moving the time zone cannot give 3 more).
 *    Every request counts, whether the friend approves, denies or ignores it.
 *  - one request open at a time.
 *  - a request not answered within 30 minutes expires.
 *
 * The server enforces all of this (Cloud Function `requestUnlock`); this is the same logic for the phone, so the
 * button can be hidden when it would be refused. A request that could not be sent (no network) never reached
 * the server, so it is not counted.
 */
object UnlockRules {
    const val MAX_REQUESTS_PER_WINDOW = 3
    val QUOTA_WINDOW: Duration = Duration.ofHours(24)
    val PASS_LENGTH: Duration = Duration.ofMinutes(15)
    val REQUEST_TTL: Duration = Duration.ofMinutes(30)
}

enum class UnlockStatus { PENDING, APPROVED, DENIED, EXPIRED }

data class UnlockRequest(
    val id: String,
    val from: PlayerId,
    val to: PlayerId,
    val requestedAt: Instant,
    val status: UnlockStatus = UnlockStatus.PENDING,
    val respondedAt: Instant? = null,
) {
    val expiresAt: Instant get() = requestedAt.plus(UnlockRules.REQUEST_TTL)
}

enum class AskCheck {
    OK,

    /** Friend unlock is off or no friend is chosen. */
    NO_BUDDY,

    /** 3 requests already used in the last 24 hours. */
    QUOTA_USED,

    /** A request is still waiting for an answer. */
    ALREADY_PENDING,
}

enum class RespondProblem { NOT_THE_FRIEND, NOT_PENDING, EXPIRED }

sealed interface RespondResult {
    data class Done(val request: UnlockRequest) : RespondResult
    data class Refused(val problem: RespondProblem) : RespondResult
}

object UnlockCoordinator {

    /** Requests made in the last 24 hours, given the times of earlier requests. */
    fun usedInWindow(requestTimes: List<Instant>, now: Instant): Int {
        val since = now.minus(UnlockRules.QUOTA_WINDOW)
        return requestTimes.count { it.isAfter(since) && !it.isAfter(now) }
    }

    fun remaining(requestTimes: List<Instant>, now: Instant): Int =
        (UnlockRules.MAX_REQUESTS_PER_WINDOW - usedInWindow(requestTimes, now)).coerceAtLeast(0)

    /** When the oldest request in the window falls out, i.e. when one more request becomes possible. Null if there is room now. */
    fun nextSlotAt(requestTimes: List<Instant>, now: Instant): Instant? {
        val inWindow = requestTimes.filter { it.isAfter(now.minus(UnlockRules.QUOTA_WINDOW)) }.sorted()
        return if (inWindow.size < UnlockRules.MAX_REQUESTS_PER_WINDOW) null
        else inWindow[inWindow.size - UnlockRules.MAX_REQUESTS_PER_WINDOW].plus(UnlockRules.QUOTA_WINDOW)
    }

    fun check(
        friendUnlockEnabled: Boolean,
        buddy: PlayerId?,
        requestTimes: List<Instant>,
        pendingRequest: UnlockRequest?,
        now: Instant,
    ): AskCheck = when {
        !friendUnlockEnabled || buddy == null -> AskCheck.NO_BUDDY
        isOpen(pendingRequest, now) -> AskCheck.ALREADY_PENDING
        remaining(requestTimes, now) == 0 -> AskCheck.QUOTA_USED
        else -> AskCheck.OK
    }

    private fun isOpen(request: UnlockRequest?, now: Instant) =
        request != null && request.status == UnlockStatus.PENDING && now.isBefore(request.expiresAt)

    /** Only the friend the request was sent to can answer, once, before it expires. */
    fun respond(request: UnlockRequest, by: PlayerId, approve: Boolean, at: Instant): RespondResult = when {
        by != request.to -> RespondResult.Refused(RespondProblem.NOT_THE_FRIEND)
        request.status != UnlockStatus.PENDING -> RespondResult.Refused(RespondProblem.NOT_PENDING)
        !at.isBefore(request.expiresAt) -> RespondResult.Refused(RespondProblem.EXPIRED)
        else -> RespondResult.Done(
            request.copy(status = if (approve) UnlockStatus.APPROVED else UnlockStatus.DENIED, respondedAt = at),
        )
    }

    /** Marks an unanswered request expired once its 30 minutes are up. */
    fun expire(request: UnlockRequest, now: Instant): UnlockRequest =
        if (request.status == UnlockStatus.PENDING && !now.isBefore(request.expiresAt)) request.copy(status = UnlockStatus.EXPIRED) else request

    /**
     * How much of the 15 minute pass is left when the approval reaches the phone. The friend may have approved a
     * while ago (the phone was offline), and that time is already used up. Both times come from the SERVER clock, so
     * the phone's clock is not involved. Zero means the approval arrived too late to be worth anything.
     */
    fun passLengthOnArrival(approvedAtServerMs: Long, serverNowMs: Long): Long {
        val used = (serverNowMs - approvedAtServerMs).coerceAtLeast(0L)
        return (UnlockRules.PASS_LENGTH.toMillis() - used).coerceIn(0L, UnlockRules.PASS_LENGTH.toMillis())
    }
}
