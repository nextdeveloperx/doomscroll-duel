package com.doomscrollduel.domain.challenge.dare

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.RIYA
import com.doomscrollduel.domain.challenge.ROHAN
import com.doomscrollduel.domain.challenge.T0
import com.doomscrollduel.domain.challenge.duel.DuelResult
import com.doomscrollduel.domain.challenge.duel.WinReason
import com.doomscrollduel.domain.challenge.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DareCatalogTest {
    @Test
    fun `ids are unique and lookups work`() {
        assertEquals(DareCatalog.all.size, DareCatalog.all.map { it.id }.toSet().size)
        DareCatalog.all.forEach { assertEquals(it, DareCatalog.byId(it.id)) }
        assertEquals(null, DareCatalog.byId("anything_the_user_types"))
    }

    @Test
    fun `every dare passes the safety rules`() {
        for (dare in DareCatalog.all) {
            assertEquals("${dare.id} breaks the safety rules: ${DareSafety.violations(dare)}", emptyList<String>(), DareSafety.violations(dare))
        }
    }

    @Test
    fun `the safety check really catches unsafe wording`() {
        val bad = Dare("x", DareCategory.SILLY, ProofType.PHOTO, "Walk outside and call a stranger.")
        assertTrue(DareSafety.violations(bad).containsAll(listOf("outside", "call", "stranger")))
        assertEquals(emptyList<String>(), DareSafety.violations(Dare("y", DareCategory.SILLY, ProofType.PHOTO, "Make a funny face.")))
    }

    @Test
    fun `video dares are limited to 10 seconds and there is a good spread`() {
        assertTrue(DareCatalog.all.size >= 12)
        assertEquals(DareCategory.entries.toSet(), DareCatalog.all.map { it.category }.toSet())
        assertTrue(DareCatalog.all.any { it.proof == ProofType.PHOTO })
        assertTrue(DareCatalog.all.any { it.proof == ProofType.VIDEO_10S })
    }
}

class DareStateMachineTest {
    private val win = DuelResult.Win(ROHAN, AMAN, 20, 70, WinReason.LOWER_COUNT) // Rohan won, Aman owes the dare
    private val dareId = "funny_face_selfie"
    private val videoDareId = "sing_chorus"

    private fun step(s: DareState, e: DareEvent): DareState = (DareStateMachine.reduce(s, e) as DareTransition.Moved).state
    private fun rejected(s: DareState, e: DareEvent) = (DareStateMachine.reduce(s, e) as DareTransition.Rejected).reason

    private fun photo(at: java.time.Instant) = ProofRef("proofs/a.jpg", ProofType.PHOTO, at)
    private fun video(at: java.time.Instant) = ProofRef("proofs/a.mp4", ProofType.VIDEO_10S, at)

    private fun awaitingPick() = step(DareState.WaitingForDuel, DareEvent.DuelEnded(win, T0))
    private fun awaitingProof(id: String = dareId) = step(awaitingPick(), DareEvent.PickDare(ROHAN, id, T0.plus(hours(1))))
    private fun submitted(at: java.time.Instant = T0.plus(hours(3))) =
        step(awaitingProof(), DareEvent.SubmitProof(AMAN, photo(at), at))

    // ----- start ------------------------------------------------------------------------------

    @Test
    fun `the winner picks and the loser owes the dare`() {
        val s = awaitingPick() as DareState.AwaitingPick
        assertEquals(ROHAN, s.winner)
        assertEquals(AMAN, s.loser)
        assertEquals(T0.plus(hours(24)), s.pickBy)
    }

    @Test
    fun `a tie or a mutual forfeit or a duel never played gives no dare`() {
        assertEquals(DareState.NoDare(NoDareReason.TIE), step(DareState.WaitingForDuel, DareEvent.DuelEnded(DuelResult.Tie(5), T0)))
        assertEquals(DareState.NoDare(NoDareReason.MUTUAL_FORFEIT), step(DareState.WaitingForDuel, DareEvent.DuelEnded(DuelResult.MutualForfeit, T0)))
        assertEquals(DareState.NoDare(NoDareReason.DUEL_NOT_PLAYED), step(DareState.WaitingForDuel, DareEvent.DuelEnded(null, T0)))
    }

    @Test
    fun `a player who lost by forfeit still owes the dare`() {
        val forfeit = DuelResult.Win(ROHAN, AMAN, 5, 0, WinReason.OPPONENT_FORFEIT)
        assertTrue(step(DareState.WaitingForDuel, DareEvent.DuelEnded(forfeit, T0)) is DareState.AwaitingPick)
    }

    // ----- picking ---------------------------------------------------------------------------

    @Test
    fun `only the winner can pick and only from the catalog`() {
        val s = awaitingPick()
        assertEquals(DareReject.NOT_THE_WINNER, rejected(s, DareEvent.PickDare(AMAN, dareId, T0)))
        assertEquals(DareReject.NOT_IN_CATALOG, rejected(s, DareEvent.PickDare(ROHAN, "eat_chilli_powder", T0)))
        assertEquals(DareReject.NOT_IN_CATALOG, rejected(s, DareEvent.PickDare(ROHAN, "", T0)))
    }

    @Test
    fun `a pick gives the loser 24 hours`() {
        val s = awaitingProof() as DareState.AwaitingProof
        assertEquals(T0.plus(hours(25)), s.dueBy)
        assertEquals(0, s.submissions)
    }

    @Test
    fun `no pick within 24 hours means no dare`() {
        assertEquals(DareState.NoDare(NoDareReason.NO_PICK), step(awaitingPick(), DareEvent.Tick(T0.plus(hours(24)))))
        // Picking at the deadline is too late.
        assertEquals(DareReject.DEADLINE_PASSED, rejected(awaitingPick(), DareEvent.PickDare(ROHAN, dareId, T0.plus(hours(24)))))
        assertTrue(step(awaitingPick(), DareEvent.Tick(T0.plus(hours(23)))) is DareState.AwaitingPick)
    }

    // ----- proof and review ---------------------------------------------------------------------

    @Test
    fun `the loser submits proof of the right type and the winner approves`() {
        val s = submitted() as DareState.ProofSubmitted
        assertEquals(1, s.submissions)
        val done = step(s, DareEvent.Approve(ROHAN, T0.plus(hours(4)))) as DareState.Completed
        assertEquals(false, done.autoApproved)
        assertEquals(dareId, done.dare.id)
    }

    @Test
    fun `wrong proof type and the wrong person are refused`() {
        val s = awaitingProof()
        assertEquals(DareReject.WRONG_PROOF_TYPE, rejected(s, DareEvent.SubmitProof(AMAN, video(T0.plus(hours(2))), T0.plus(hours(2)))))
        assertEquals(DareReject.NOT_THE_LOSER, rejected(s, DareEvent.SubmitProof(ROHAN, photo(T0.plus(hours(2))), T0.plus(hours(2)))))
        val video = awaitingProof(videoDareId)
        assertEquals(DareReject.WRONG_PROOF_TYPE, rejected(video, DareEvent.SubmitProof(AMAN, photo(T0.plus(hours(2))), T0.plus(hours(2)))))
    }

    @Test
    fun `missing the 24 hour proof deadline fails the dare`() {
        val s = awaitingProof()
        assertEquals(DareReject.DEADLINE_PASSED, rejected(s, DareEvent.SubmitProof(AMAN, photo(T0.plus(hours(25))), T0.plus(hours(25)))))
        val failed = step(s, DareEvent.Tick(T0.plus(hours(25)))) as DareState.Failed
        assertEquals(DareFailReason.PROOF_DEADLINE_MISSED, failed.reason)
    }

    @Test
    fun `only the winner approves or rejects`() {
        val s = submitted()
        assertEquals(DareReject.NOT_THE_WINNER, rejected(s, DareEvent.Approve(AMAN, T0.plus(hours(4)))))
        assertEquals(DareReject.NOT_THE_WINNER, rejected(s, DareEvent.Reject(AMAN, T0.plus(hours(4)))))
        assertEquals(DareReject.NOT_THE_WINNER, rejected(s, DareEvent.Approve(RIYA, T0.plus(hours(4)))))
    }

    @Test
    fun `an unanswered proof is approved after 24 hours so the loser is never stuck`() {
        val s = submitted(T0.plus(hours(3))) as DareState.ProofSubmitted
        assertTrue(step(s, DareEvent.Tick(T0.plus(hours(26)))) is DareState.ProofSubmitted)
        val done = step(s, DareEvent.Tick(T0.plus(hours(27)))) as DareState.Completed
        assertEquals(true, done.autoApproved)
    }

    @Test
    fun `one rejection allows a second try, a second rejection fails the dare`() {
        val first = submitted() as DareState.ProofSubmitted
        val retry = step(first, DareEvent.Reject(ROHAN, T0.plus(hours(5)))) as DareState.AwaitingProof
        assertEquals(1, retry.submissions)
        // Plenty of the original 24 hours left, so the deadline is unchanged.
        assertEquals(first.dueBy, retry.dueBy)
        val second = step(retry, DareEvent.SubmitProof(AMAN, photo(T0.plus(hours(6))), T0.plus(hours(6)))) as DareState.ProofSubmitted
        assertEquals(2, second.submissions)
        val failed = step(second, DareEvent.Reject(ROHAN, T0.plus(hours(7)))) as DareState.Failed
        assertEquals(DareFailReason.REJECTED_TWICE, failed.reason)
    }

    @Test
    fun `a rejection near the deadline still leaves 6 hours to resubmit`() {
        val late = T0.plus(hours(24))
        val s = step(awaitingProof(), DareEvent.SubmitProof(AMAN, photo(late), late)) as DareState.ProofSubmitted
        val rejectAt = T0.plus(hours(24)).plusSeconds(60)
        val retry = step(s, DareEvent.Reject(ROHAN, rejectAt)) as DareState.AwaitingProof
        assertEquals(rejectAt.plus(DareRules.RESUBMIT_MIN), retry.dueBy)
    }

    // ----- skip, waive, report --------------------------------------------------------------------

    @Test
    fun `the loser can skip before sending proof with no penalty`() {
        val before = step(awaitingPick(), DareEvent.Skip(AMAN, T0)) as DareState.Skipped
        assertEquals(null, before.dare)
        val after = step(awaitingProof(), DareEvent.Skip(AMAN, T0.plus(hours(2)))) as DareState.Skipped
        assertEquals(dareId, after.dare?.id)
        assertEquals(AMAN, after.by)
    }

    @Test
    fun `the loser cannot skip after sending proof and the winner cannot skip for them`() {
        assertEquals(DareReject.WRONG_STATE, rejected(submitted(), DareEvent.Skip(AMAN, T0.plus(hours(4)))))
        assertEquals(DareReject.NOT_THE_LOSER, rejected(awaitingProof(), DareEvent.Skip(ROHAN, T0)))
    }

    @Test
    fun `the winner can waive the dare at any point`() {
        assertTrue(step(awaitingPick(), DareEvent.Waive(ROHAN, T0)) is DareState.Skipped)
        assertTrue(step(awaitingProof(), DareEvent.Waive(ROHAN, T0)) is DareState.Skipped)
        assertTrue(step(submitted(), DareEvent.Waive(ROHAN, T0)) is DareState.Skipped)
        assertEquals(DareReject.NOT_THE_WINNER, rejected(awaitingProof(), DareEvent.Waive(AMAN, T0)))
    }

    @Test
    fun `either player can report once a dare or proof exists and it freezes the dare`() {
        for (reporter in listOf(ROHAN, AMAN)) {
            val reported = step(awaitingProof(), DareEvent.Report(reporter, ReportReason.UNSAFE_DARE, T0.plus(hours(2)))) as DareState.Reported
            assertEquals(reporter, reported.by)
            assertEquals(ReportReason.UNSAFE_DARE, reported.reason)
        }
        assertTrue(step(submitted(), DareEvent.Report(ROHAN, ReportReason.NUDITY, T0.plus(hours(4)))) is DareState.Reported)
    }

    @Test
    fun `a stranger cannot report and there is nothing to report before a dare exists`() {
        assertEquals(DareReject.NOT_A_PLAYER, rejected(awaitingProof(), DareEvent.Report(RIYA, ReportReason.OTHER, T0)))
        assertEquals(DareReject.WRONG_STATE, rejected(awaitingPick(), DareEvent.Report(AMAN, ReportReason.OTHER, T0)))
    }

    @Test
    fun `a reported dare cannot be approved afterwards`() {
        val reported = step(submitted(), DareEvent.Report(AMAN, ReportReason.HARASSMENT, T0.plus(hours(4))))
        assertEquals(DareReject.ALREADY_FINAL, rejected(reported, DareEvent.Approve(ROHAN, T0.plus(hours(5)))))
        assertEquals(DareReject.ALREADY_FINAL, rejected(reported, DareEvent.Tick(T0.plus(hours(99)))))
    }

    @Test
    fun `every terminal state refuses every event, so a dare cannot be settled twice`() {
        val done = step(submitted(), DareEvent.Approve(ROHAN, T0.plus(hours(4))))
        assertEquals(DareReject.ALREADY_FINAL, rejected(done, DareEvent.Approve(ROHAN, T0.plus(hours(5)))))
        assertEquals(DareReject.ALREADY_FINAL, rejected(done, DareEvent.Reject(ROHAN, T0.plus(hours(5)))))
        val noDare = DareState.NoDare(NoDareReason.TIE)
        assertEquals(DareReject.ALREADY_FINAL, rejected(noDare, DareEvent.PickDare(ROHAN, dareId, T0)))
    }

    @Test
    fun `events out of order are refused`() {
        assertEquals(DareReject.WRONG_STATE, rejected(DareState.WaitingForDuel, DareEvent.PickDare(ROHAN, dareId, T0)))
        assertEquals(DareReject.WRONG_STATE, rejected(awaitingPick(), DareEvent.SubmitProof(AMAN, photo(T0), T0)))
        assertEquals(DareReject.WRONG_STATE, rejected(awaitingProof(), DareEvent.Approve(ROHAN, T0)))
    }
}
