package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.lock.LockDecision
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingEngineTest {
    private val nowMs = wall(IST, 2026, 10, 1, 23, 30)
    private val s0 = sample(nowMs, 1_000_000L)
    private val window = ActiveWindow(WindowKind.BEDTIME, inst(wall(IST, 2026, 10, 1, 23, 0)), inst(wall(IST, 2026, 10, 2, 6, 0)))
    private val focus = ActiveWindow(WindowKind.FOCUS, inst(wall(IST, 2026, 10, 1, 23, 0)), inst(wall(IST, 2026, 10, 2, 0, 30)))
    private val lock = LockDecision.Block(Duration.ofMinutes(42))
    private val withFriend = BlockingSettings(friendUnlockEnabled = true, buddy = Buddy(AMAN, "Aman"))

    private fun input(
        settings: BlockingSettings = BlockingSettings(),
        lock: LockDecision = LockDecision.Allow,
        pass: FriendPass? = null,
        window: ActiveWindow? = null,
        gate: GateSession? = null,
        quota: Int = 3,
        pending: Boolean = false,
    ) = BlockingInput(settings, lock, pass, window, gate, nowElapsedMs = 50_000L, nowMs = nowMs, askQuotaLeft = quota, askPending = pending)

    private fun decide(i: BlockingInput) = BlockingEngine.decide(i)

    @Test
    fun `nothing on means allow`() {
        assertEquals(BlockAction.Allow, decide(input()))
    }

    @Test
    fun `the lock blocks with the time left`() {
        val a = decide(input(lock = lock)) as BlockAction.Block
        assertEquals(BlockReason.STRICT_LOCK, a.reason)
        assertEquals(minutesMs(42), a.remainingMs)
    }

    @Test
    fun `a window blocks with the time to its end`() {
        val a = decide(input(window = window)) as BlockAction.Block
        assertEquals(BlockReason.BEDTIME, a.reason)
        assertEquals(minutesMs(6 * 60 + 30), a.remainingMs)
        assertEquals(BlockReason.FOCUS, (decide(input(window = focus)) as BlockAction.Block).reason)
    }

    @Test
    fun `the lock beats a window and a window beats the gate`() {
        val gateOn = BlockingSettings(wait10Enabled = true)
        assertEquals(BlockReason.STRICT_LOCK, (decide(input(gateOn, lock = lock, window = window)) as BlockAction.Block).reason)
        assertTrue(decide(input(gateOn, window = window)) is BlockAction.Block)
        assertTrue(decide(input(gateOn)) is BlockAction.Gate)
    }

    @Test
    fun `a friend pass allows everything including a lock, a window and the gate`() {
        val pass = FriendPass(minutesMs(10), s0)
        val gateOn = BlockingSettings(wait10Enabled = true)
        assertEquals(BlockAction.Allow, decide(input(gateOn, lock = lock, pass = pass, window = window)))
    }

    @Test
    fun `an expired pass does nothing`() {
        val pass = FriendPass(0, s0)
        assertTrue(decide(input(lock = lock, pass = pass)) is BlockAction.Block)
    }

    // ----- the gate ------------------------------------------------------------------------------------

    @Test
    fun `the gate counts down, then is ready, then passes for the visit`() {
        val gateOn = BlockingSettings(wait10Enabled = true)
        val session = GateSession(openedAtElapsedMs = 45_000L)
        assertEquals(BlockAction.Gate(GateState.Counting(5_000L)), decide(input(gateOn, gate = session)))
        val ready = decide(input(gateOn, gate = GateSession(30_000L)))
        assertEquals(BlockAction.Gate(GateState.Ready), ready)
        assertEquals(BlockAction.Allow, decide(input(gateOn, gate = GateSession(30_000L, continuedAtElapsedMs = 41_000L))))
    }

    @Test
    fun `no session yet shows a full countdown`() {
        assertEquals(BlockAction.Gate(GateState.Counting(WaitGate.COUNTDOWN_MS)), decide(input(BlockingSettings(wait10Enabled = true))))
    }

    @Test
    fun `the gate is off by default`() {
        assertEquals(BlockAction.Allow, decide(input(gate = GateSession(0L))))
    }

    // ----- ask a friend --------------------------------------------------------------------------------

    @Test
    fun `the ask button needs friend unlock on, a friend, quota and no pending request`() {
        fun canAsk(i: BlockingInput) = (decide(i) as BlockAction.Block).canAskFriend
        assertTrue(canAsk(input(withFriend, lock = lock)))
        assertFalse(canAsk(input(BlockingSettings(), lock = lock)))
        assertFalse(canAsk(input(withFriend.copy(friendUnlockEnabled = false), lock = lock)))
        assertFalse(canAsk(input(withFriend, lock = lock, quota = 0)))
        assertFalse(canAsk(input(withFriend, lock = lock, pending = true)))
        assertTrue(canAsk(input(withFriend, window = window)))
        val a = decide(input(withFriend, lock = lock, quota = 2, pending = true)) as BlockAction.Block
        assertEquals(2, a.askQuotaLeft)
        assertTrue(a.askPending)
    }

    // ----- is anything watching? ---------------------------------------------------------------------------

    @Test
    fun `the service only looks at the screen when something could block`() {
        val zone = IST
        val now = inst(wall(IST, 2026, 10, 1, 12, 0))
        val idle = StrictLockState.Idle(java.time.LocalDate.of(2026, 10, 1))
        val locked = StrictLockState.Locked(java.time.LocalDate.of(2026, 10, 1), 0, hoursMs(1), 0, s0, 100)
        assertFalse(BlockingEngine.isWatching(BlockingSettings(), idle, null, now, zone))
        assertTrue(BlockingEngine.isWatching(BlockingSettings(wait10Enabled = true), idle, null, now, zone))
        assertTrue(BlockingEngine.isWatching(BlockingSettings(), locked, null, now, zone))
        assertTrue(BlockingEngine.isWatching(BlockingSettings(bedtime = BedtimeSettings(true)), idle, null, inst(wall(IST, 2026, 10, 1, 23, 30)), zone))
        assertFalse(BlockingEngine.isWatching(BlockingSettings(bedtime = BedtimeSettings(true)), idle, null, now, zone))
        // A running pass means nothing needs checking.
        assertFalse(BlockingEngine.isWatching(BlockingSettings(wait10Enabled = true), locked, FriendPass(1_000, s0), now, zone))
    }
}
