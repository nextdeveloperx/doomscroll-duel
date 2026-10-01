package com.doomscrollduel.domain.challenge.nightpact

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.ChallengeId
import com.doomscrollduel.domain.challenge.GapCause
import com.doomscrollduel.domain.challenge.PlayerId
import com.doomscrollduel.domain.challenge.RIYA
import com.doomscrollduel.domain.challenge.ROHAN
import com.doomscrollduel.domain.challenge.TrackingGap
import com.doomscrollduel.domain.challenge.hours
import com.doomscrollduel.domain.challenge.minutes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightWindowTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant()

    @Test
    fun `window is 23 00 to 06 00 local`() {
        val (s, e) = NightWindow.of(LocalDate.of(2026, 10, 1), ist)
        assertEquals(at(ist, 2026, 10, 1, 23), s)
        assertEquals(at(ist, 2026, 10, 2, 6), e)
    }

    @Test
    fun `the boundaries belong to the right night`() {
        assertEquals(LocalDate.of(2026, 10, 1), NightWindow.nightOf(at(ist, 2026, 10, 1, 23, 0), ist)) // 23:00 is in
        assertEquals(LocalDate.of(2026, 10, 1), NightWindow.nightOf(at(ist, 2026, 10, 2, 0, 0), ist))
        assertEquals(LocalDate.of(2026, 10, 1), NightWindow.nightOf(at(ist, 2026, 10, 2, 5, 59), ist))
        assertNull(NightWindow.nightOf(at(ist, 2026, 10, 2, 6, 0), ist)) // 06:00 is out
        assertNull(NightWindow.nightOf(at(ist, 2026, 10, 1, 22, 59), ist))
        assertNull(NightWindow.nightOf(at(ist, 2026, 10, 1, 12, 0), ist))
    }

    @Test
    fun `a night on the first of the month and the new year`() {
        assertEquals(LocalDate.of(2026, 12, 31), NightWindow.nightOf(at(ist, 2027, 1, 1, 1, 0), ist))
    }

    @Test
    fun `a daylight saving night is an hour longer or shorter, not broken`() {
        val ny = ZoneId.of("America/New_York")
        val (s, e) = NightWindow.of(LocalDate.of(2026, 3, 7), ny) // clocks spring forward during this night
        assertEquals(6 * 60L, java.time.Duration.between(s, e).toMinutes()) // 23:00 to 06:00 is 7 hours, minus the lost hour
        val (s2, e2) = NightWindow.of(LocalDate.of(2026, 10, 31), ny) // clocks fall back
        assertEquals(8 * 60L, java.time.Duration.between(s2, e2).toMinutes())
    }

    @Test
    fun `different zones see different instants for the same night`() {
        val utc = ZoneId.of("UTC")
        assertEquals(
            java.time.Duration.ofMinutes(330),
            java.time.Duration.between(NightWindow.of(LocalDate.of(2026, 10, 1), ist).first, NightWindow.of(LocalDate.of(2026, 10, 1), utc).first),
        )
    }
}

class NightPactRulesTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val created = Instant.parse("2026-09-30T10:00:00Z")
    private val night1 = LocalDate.of(2026, 10, 1)

    private fun pact(): NightPact {
        var p = NightPact.create(ChallengeId("n1"), ROHAN, ist, listOf(AMAN to ist, RIYA to ist), created)!!
        p = (NightPactMachine.reduce(p, PactEvent.Accept(AMAN, created.plus(minutes(5)))) as PactTransition.Moved).pact
        p = (NightPactMachine.reduce(p, PactEvent.Accept(RIYA, created.plus(minutes(6)))) as PactTransition.Moved).pact
        return p
    }

    private fun clean() = NightReport(0)

    @Test
    fun `everyone clean keeps the night`() {
        val r = NightPactRules.evaluateNight(pact(), night1, mapOf(ROHAN to clean(), AMAN to clean(), RIYA to clean()))
        assertTrue(r.everyoneKept)
        assertTrue(r.verdicts.values.all { it == NightVerdict.Kept })
    }

    @Test
    fun `one counted reel breaks the night for that person and the pact`() {
        val r = NightPactRules.evaluateNight(pact(), night1, mapOf(ROHAN to clean(), AMAN to NightReport(1), RIYA to clean()))
        assertEquals(NightVerdict.Broken(1), r.verdicts[AMAN])
        assertFalse(r.everyoneKept)
        assertEquals(listOf(AMAN), r.breakers)
    }

    @Test
    fun `no report is unverified and breaks the night`() {
        val r = NightPactRules.evaluateNight(pact(), night1, mapOf(ROHAN to clean(), AMAN to clean()))
        assertEquals(NightVerdict.Unverified(UnverifiedReason.NO_REPORT), r.verdicts[RIYA])
        assertFalse(r.everyoneKept)
    }

    @Test
    fun `counter off for more than 10 minutes in the window is unverified`() {
        val (s, _) = NightWindow.of(night1, ist)
        val off = TrackingGap(s.plus(hours(2)), s.plus(hours(2)).plus(minutes(11)), GapCause.SERVICE_DISABLED)
        val short = TrackingGap(s.plus(hours(2)), s.plus(hours(2)).plus(minutes(10)), GapCause.SERVICE_DISABLED)
        val dead = TrackingGap(s, s.plus(hours(6)), GapCause.DEVICE_OFF_OR_UNKNOWN)
        assertEquals(NightVerdict.Unverified(UnverifiedReason.COUNTER_OFF), NightPactRules.verdict(NightReport(0, listOf(off)), NightWindow.of(night1, ist)))
        assertEquals(NightVerdict.Kept, NightPactRules.verdict(NightReport(0, listOf(short)), NightWindow.of(night1, ist)))
        assertEquals(NightVerdict.Kept, NightPactRules.verdict(NightReport(0, listOf(dead)), NightWindow.of(night1, ist)))
    }

    @Test
    fun `a counted reel is reported as broken even if the counter was also off`() {
        val (s, _) = NightWindow.of(night1, ist)
        val off = TrackingGap(s, s.plus(hours(3)), GapCause.SERVICE_DISABLED)
        assertEquals(NightVerdict.Broken(2), NightPactRules.verdict(NightReport(2, listOf(off)), NightWindow.of(night1, ist)))
    }

    @Test
    fun `someone who joins after the window starts takes part from the next night`() {
        val p = pact()
        val lateJoiner = PactMember(PlayerId("late"), ist, MemberStatus.Accepted(NightWindow.of(night1, ist).first.plusSeconds(1)))
        assertFalse(NightPactRules.participates(lateJoiner, night1))
        assertTrue(NightPactRules.participates(lateJoiner, night1.plusDays(1)))
        assertTrue(NightPactRules.participates(p.member(AMAN)!!, night1))
    }

    @Test
    fun `members in different time zones are judged in their own night`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val p = NightPact.create(ChallengeId("z"), ROHAN, ist, listOf(AMAN to tokyo), created)!!
        val accepted = (NightPactMachine.reduce(p, PactEvent.Accept(AMAN, created.plus(minutes(1)))) as PactTransition.Moved).pact
        val (tokyoStart, _) = NightWindow.of(night1, tokyo)
        val (istStart, _) = NightWindow.of(night1, ist)
        assertTrue(tokyoStart.isBefore(istStart)) // Tokyo's night starts first
        val r = NightPactRules.evaluateNight(accepted, night1, mapOf(ROHAN to clean(), AMAN to clean()))
        assertTrue(r.everyoneKept)
    }

    // ----- streak ------------------------------------------------------------------------------

    private fun rec(day: Long, kept: Boolean) = NightRecord(
        night1.plusDays(day),
        mapOf(ROHAN to NightVerdict.Kept, AMAN to if (kept) NightVerdict.Kept else NightVerdict.Broken(3)),
    )

    @Test
    fun `the streak counts nights in a row that everyone kept`() {
        assertEquals(PactStreak(3, 3), NightPactRules.streak(listOf(rec(0, true), rec(1, true), rec(2, true))))
    }

    @Test
    fun `one broken night resets the current streak but keeps the best`() {
        val s = NightPactRules.streak(listOf(rec(0, true), rec(1, true), rec(2, true), rec(3, false), rec(4, true)))
        assertEquals(PactStreak(current = 1, best = 3), s)
    }

    @Test
    fun `a broken last night means the streak is zero`() {
        assertEquals(0, NightPactRules.streak(listOf(rec(0, true), rec(1, false))).current)
    }

    @Test
    fun `a missing night in between resets the streak`() {
        val s = NightPactRules.streak(listOf(rec(0, true), rec(1, true), rec(3, true))) // night 2 is missing
        assertEquals(PactStreak(current = 1, best = 2), s)
    }

    @Test
    fun `records out of order give the same streak and no records give zero`() {
        assertEquals(NightPactRules.streak(listOf(rec(0, true), rec(1, true))), NightPactRules.streak(listOf(rec(1, true), rec(0, true))))
        assertEquals(PactStreak(0, 0), NightPactRules.streak(emptyList()))
    }

    @Test
    fun `a night with only one participant does not count`() {
        val alone = NightRecord(night1, mapOf(ROHAN to NightVerdict.Kept, AMAN to NightVerdict.NotParticipating))
        assertFalse(alone.everyoneKept)
    }

    @Test
    fun `a night is recorded once`() {
        val p = pact()
        val r = rec(0, true)
        val once = NightPactRules.recordNight(p, r, created)
        val twice = NightPactRules.recordNight(once, r.copy(verdicts = mapOf(ROHAN to NightVerdict.Broken(9))), created)
        assertEquals(1, twice.nights.size)
        assertEquals(once, twice)
    }

    @Test
    fun `the pact ends when fewer than two people are left`() {
        var p = pact()
        p = (NightPactMachine.reduce(p, PactEvent.Leave(AMAN, created.plus(hours(30)))) as PactTransition.Moved).pact
        assertTrue(p.state is PactState.Active) // Rohan and Riya remain
        p = (NightPactMachine.reduce(p, PactEvent.Leave(RIYA, created.plus(hours(31)))) as PactTransition.Moved).pact
        assertEquals(PactEndReason.TOO_FEW_MEMBERS, (p.state as PactState.Ended).reason)
    }

    // ----- lifecycle ---------------------------------------------------------------------------

    @Test
    fun `needs two or more friends and no duplicates`() {
        assertNull(NightPact.create(ChallengeId("x"), ROHAN, ist, emptyList(), created))
        assertNull(NightPact.create(ChallengeId("x"), ROHAN, ist, listOf(ROHAN to ist), created))
        assertNull(NightPact.create(ChallengeId("x"), ROHAN, ist, listOf(AMAN to ist, AMAN to ist), created))
        assertNotNull(NightPact.create(ChallengeId("x"), ROHAN, ist, listOf(AMAN to ist), created))
    }

    @Test
    fun `the first yes starts the pact and later yes joins from the next night`() {
        val p = NightPact.create(ChallengeId("x"), ROHAN, ist, listOf(AMAN to ist, RIYA to ist), created)!!
        val started = (NightPactMachine.reduce(p, PactEvent.Accept(AMAN, created.plus(minutes(5)))) as PactTransition.Moved).pact
        assertTrue(started.state is PactState.Active)
        val later = (NightPactMachine.reduce(started, PactEvent.Accept(RIYA, created.plus(hours(10)))) as PactTransition.Moved).pact
        assertEquals(3, later.activeMembers.size)
        // 10 hours after creation Riya says yes (01:30 IST on 1 Oct): she is in for the night of 1 Oct.
        assertTrue(NightPactRules.participates(later.member(RIYA)!!, night1))
    }

    @Test
    fun `invites expire after 24 hours`() {
        val p = NightPact.create(ChallengeId("x"), ROHAN, ist, listOf(AMAN to ist), created)!!
        val late = created.plus(hours(24))
        assertEquals(PactReject.INVITE_EXPIRED, (NightPactMachine.reduce(p, PactEvent.Accept(AMAN, late)) as PactTransition.Rejected).reason)
        assertEquals(PactReject.TOO_EARLY, (NightPactMachine.reduce(p, PactEvent.Expire(created.plus(hours(1)))) as PactTransition.Rejected).reason)
        assertTrue((NightPactMachine.reduce(p, PactEvent.Expire(late)) as PactTransition.Moved).pact.state is PactState.Expired)
    }

    @Test
    fun `everyone declining cancels the pact`() {
        var p = NightPact.create(ChallengeId("x"), ROHAN, ist, listOf(AMAN to ist, RIYA to ist), created)!!
        p = (NightPactMachine.reduce(p, PactEvent.Decline(AMAN, created)) as PactTransition.Moved).pact
        assertTrue(p.state is PactState.Pending)
        p = (NightPactMachine.reduce(p, PactEvent.Decline(RIYA, created)) as PactTransition.Moved).pact
        assertTrue(p.state is PactState.Cancelled)
    }

    @Test
    fun `terminal pacts refuse every event`() {
        val p = NightPact.create(ChallengeId("x"), ROHAN, ist, listOf(AMAN to ist), created)!!
        val cancelled = (NightPactMachine.reduce(p, PactEvent.Cancel(ROHAN, created)) as PactTransition.Moved).pact
        assertEquals(PactReject.ALREADY_FINAL, (NightPactMachine.reduce(cancelled, PactEvent.Accept(AMAN, created)) as PactTransition.Rejected).reason)
        assertEquals(PactReject.NOT_THE_CREATOR, (NightPactMachine.reduce(p, PactEvent.Cancel(AMAN, created)) as PactTransition.Rejected).reason)
    }
}
