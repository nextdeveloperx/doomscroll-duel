package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.RIYA
import com.doomscrollduel.domain.challenge.ROHAN
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaitGateTest {
    @Test
    fun `the gate counts down 10 seconds and then is ready`() {
        val session = GateSession(openedAtElapsedMs = 100_000L)
        assertEquals(GateState.Counting(10_000L), session.state(100_000L))
        assertEquals(GateState.Counting(1L), session.state(109_999L))
        assertEquals(GateState.Ready, session.state(110_000L))
        assertEquals(GateState.Ready, session.state(500_000L))
    }

    @Test
    fun `dekhna hai is refused during the countdown and accepted after it`() {
        val session = GateSession(100_000L)
        assertNull(session.continued(105_000L))
        assertNull(session.continued(109_999L))
        val passed = session.continued(110_000L)!!
        assertEquals(GateState.Passed, passed.state(110_001L))
    }

    @Test
    fun `a passed session stays passed for the whole visit`() {
        val passed = GateSession(0L).continued(10_000L)!!
        assertEquals(GateState.Passed, passed.state(10_000_000L))
    }

    @Test
    fun `the session ends only after the grace period away`() {
        val session = GateSession(0L).seen(20_000L)
        assertFalse(session.hasEnded(20_000L + WaitGate.LEAVE_GRACE_MS))
        assertTrue(session.hasEnded(20_000L + WaitGate.LEAVE_GRACE_MS + 1))
    }

    @Test
    fun `seeing the screen again keeps the session alive`() {
        var session = GateSession(0L)
        for (t in listOf(2_000L, 4_000L, 6_000L, 8_000L)) session = session.seen(t)
        assertFalse(session.hasEnded(10_000L))
    }
}

class UnlockRequestsTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private fun ago(minutes: Long) = now.minusSeconds(minutes * 60)

    @Test
    fun `three requests are allowed in 24 hours, the fourth is not`() {
        assertEquals(3, UnlockCoordinator.remaining(emptyList(), now))
        assertEquals(1, UnlockCoordinator.remaining(listOf(ago(10), ago(100)), now))
        assertEquals(0, UnlockCoordinator.remaining(listOf(ago(10), ago(100), ago(1_000)), now))
    }

    @Test
    fun `the limit is a rolling 24 hours, not a calendar day`() {
        // Used at 23:50 yesterday and 00:10 today: both still count at noon.
        val times = listOf(ago(12 * 60 + 10), ago(11 * 60 + 50), ago(5))
        assertEquals(0, UnlockCoordinator.remaining(times, now))
        // A request 24h + 1min ago has dropped out.
        assertEquals(1, UnlockCoordinator.remaining(listOf(ago(24 * 60 + 1), ago(30), ago(20)), now))
        assertEquals(1, UnlockCoordinator.remaining(listOf(ago(24 * 60), ago(30), ago(20)), now)) // exactly 24h ago is out
    }

    @Test
    fun `every request counts whatever the friend did`() {
        // The count is just times; a denied or ignored request is in the list like any other.
        assertEquals(0, UnlockCoordinator.remaining(listOf(ago(5), ago(6), ago(7)), now))
    }

    @Test
    fun `when the next request becomes possible`() {
        assertNull(UnlockCoordinator.nextSlotAt(listOf(ago(5)), now))
        val full = listOf(ago(300), ago(100), ago(10))
        assertEquals(ago(300).plus(UnlockRules.QUOTA_WINDOW), UnlockCoordinator.nextSlotAt(full, now))
    }

    private val open = UnlockRequest("r1", ROHAN, AMAN, now)

    @Test
    fun `asking checks friend, pending and quota in that order`() {
        fun check(enabled: Boolean = true, buddy: com.doomscrollduel.domain.challenge.PlayerId? = AMAN, times: List<Instant> = emptyList(), pending: UnlockRequest? = null) =
            UnlockCoordinator.check(enabled, buddy, times, pending, now)
        assertEquals(AskCheck.OK, check())
        assertEquals(AskCheck.NO_BUDDY, check(enabled = false))
        assertEquals(AskCheck.NO_BUDDY, check(buddy = null))
        assertEquals(AskCheck.ALREADY_PENDING, check(pending = open))
        assertEquals(AskCheck.QUOTA_USED, check(times = listOf(ago(1), ago(2), ago(3))))
        // An answered or expired request no longer blocks a new one.
        assertEquals(AskCheck.OK, check(pending = open.copy(status = UnlockStatus.DENIED)))
        assertEquals(AskCheck.OK, check(pending = UnlockRequest("old", ROHAN, AMAN, ago(31))))
    }

    @Test
    fun `only the chosen friend can answer, once, in time`() {
        val ok = UnlockCoordinator.respond(open, AMAN, approve = true, at = now.plusSeconds(60)) as RespondResult.Done
        assertEquals(UnlockStatus.APPROVED, ok.request.status)
        assertEquals(now.plusSeconds(60), ok.request.respondedAt)
        val denied = UnlockCoordinator.respond(open, AMAN, approve = false, at = now.plusSeconds(60)) as RespondResult.Done
        assertEquals(UnlockStatus.DENIED, denied.request.status)

        assertEquals(RespondProblem.NOT_THE_FRIEND, (UnlockCoordinator.respond(open, RIYA, true, now) as RespondResult.Refused).problem)
        assertEquals(RespondProblem.NOT_THE_FRIEND, (UnlockCoordinator.respond(open, ROHAN, true, now) as RespondResult.Refused).problem)
        assertEquals(RespondProblem.NOT_PENDING, (UnlockCoordinator.respond(ok.request, AMAN, false, now.plusSeconds(90)) as RespondResult.Refused).problem)
        assertEquals(RespondProblem.EXPIRED, (UnlockCoordinator.respond(open, AMAN, true, open.expiresAt) as RespondResult.Refused).problem)
    }

    @Test
    fun `an unanswered request expires after 30 minutes`() {
        assertEquals(UnlockStatus.PENDING, UnlockCoordinator.expire(open, now.plusSeconds(29 * 60)).status)
        assertEquals(UnlockStatus.EXPIRED, UnlockCoordinator.expire(open, now.plusSeconds(30 * 60)).status)
        val approved = open.copy(status = UnlockStatus.APPROVED)
        assertEquals(UnlockStatus.APPROVED, UnlockCoordinator.expire(approved, now.plusSeconds(99 * 60)).status)
    }

    @Test
    fun `an approval that arrives late has less pass left, measured on the server clock`() {
        val full = UnlockRules.PASS_LENGTH.toMillis()
        assertEquals(full, UnlockCoordinator.passLengthOnArrival(1_000_000L, 1_000_000L))
        assertEquals(full - 5 * 60_000L, UnlockCoordinator.passLengthOnArrival(1_000_000L, 1_000_000L + 5 * 60_000L))
        assertEquals(0L, UnlockCoordinator.passLengthOnArrival(1_000_000L, 1_000_000L + 20 * 60_000L))
        assertEquals(full, UnlockCoordinator.passLengthOnArrival(1_000_000L, 999_000L)) // never more than 15 minutes
    }
}
