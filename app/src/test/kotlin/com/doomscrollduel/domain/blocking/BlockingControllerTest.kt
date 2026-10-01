package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.lock.LockLength
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The edge cases the user asked for: app kill, reboot, clock change, time zone change, no network. */
class BlockingControllerTest {
    private val clock = FakeClock(wallMs = wall(IST, 2026, 10, 1, 14, 0))
    private val zone = FakeZone()
    private val settings = FakeSettings(BlockingSettings(strictLockEnabled = true, dailyLimit = 100))
    private val store = FakeStore()

    private fun controller() = BlockingController(settings, store, clock, zone)
    private val c = controller()

    private fun block(a: BlockAction) = a as BlockAction.Block

    private fun lockUp(controller: BlockingController = c) {
        assertTrue(controller.onReelCounted(100))
    }

    // ----- hitting the limit -------------------------------------------------------------------------------

    @Test
    fun `reels below the limit do not lock`() {
        assertFalse(c.onReelCounted(99))
        assertEquals(BlockAction.Allow, c.onReelScreenOpened())
        assertFalse(c.status().lockRunning)
    }

    @Test
    fun `the reel that reaches the limit starts the lock and the next screen is blocked`() {
        lockUp()
        val a = block(c.onReelScreenOpened())
        assertEquals(BlockReason.STRICT_LOCK, a.reason)
        assertEquals(hoursMs(10), a.remainingMs) // 14:00 to midnight
        assertTrue(c.status().lockRunning)
    }

    @Test
    fun `with the timer-lock off nothing locks`() {
        settings.settings = settings.settings.copy(strictLockEnabled = false)
        assertFalse(c.onReelCounted(500))
        assertEquals(BlockAction.Allow, c.onReelScreenOpened())
    }

    @Test
    fun `the lock counts down in real time and ends`() {
        lockUp()
        clock.pass(hoursMs(4))
        assertEquals(hoursMs(6), block(c.onReelScreenSeen()).remainingMs)
        clock.pass(hoursMs(6))
        assertEquals(BlockAction.Allow, c.onReelScreenSeen())
        assertFalse(c.status().lockRunning)
    }

    // ----- survive app kill, reboot, clock change ---------------------------------------------------------------------

    @Test
    fun `the lock survives the app being killed`() {
        lockUp()
        clock.pass(minutesMs(30))
        val afterKill = controller() // a brand new process reading the saved state
        assertEquals(hoursMs(10) - minutesMs(30), block(afterKill.onReelScreenOpened()).remainingMs)
    }

    @Test
    fun `time that passes while the app is dead still counts`() {
        lockUp()
        clock.pass(hours(3)) // the process is dead the whole time
        assertEquals(hoursMs(7), block(controller().onReelScreenOpened()).remainingMs)
    }

    private fun hours(h: Long) = hoursMs(h)

    @Test
    fun `the lock survives a reboot and uses the wall clock for the time the phone was off`() {
        lockUp()
        clock.pass(hoursMs(1))
        c.status() // saved state is older than this reading, on purpose
        clock.reboot(offMs = hoursMs(2))
        val afterBoot = controller()
        // 1h before the reboot + 2h off. The saved reading is from 14:00, so wall delta = 3h.
        assertEquals(hoursMs(7), block(afterBoot.onReelScreenOpened()).remainingMs)
    }

    @Test
    fun `the lock is still there after reboot even if no reel is opened`() {
        lockUp()
        clock.reboot(offMs = minutesMs(5))
        assertTrue(controller().isWatching())
        assertTrue(controller().status().lockRunning)
    }

    @Test
    fun `setting the phone clock forward does not unlock`() {
        lockUp()
        clock.pass(minutesMs(10))
        clock.userSetsClock(hoursMs(12)) // the user jumps to tomorrow
        assertEquals(hoursMs(10) - minutesMs(10), block(c.onReelScreenOpened()).remainingMs)
        assertTrue(c.status().lockRunning)
    }

    @Test
    fun `setting the clock forward also fails after the app is killed`() {
        lockUp()
        clock.pass(minutesMs(10))
        clock.userSetsClock(hoursMs(12))
        assertEquals(hoursMs(10) - minutesMs(10), block(controller().onReelScreenOpened()).remainingMs)
    }

    @Test
    fun `setting the clock backward does not lengthen the lock`() {
        lockUp()
        clock.pass(hoursMs(2))
        clock.userSetsClock(-hoursMs(5))
        assertEquals(hoursMs(8), block(c.onReelScreenOpened()).remainingMs)
    }

    @Test
    fun `a reboot with the clock set forward is the known gap, closed when a trusted time is known`() {
        // Without trusted time: the forged clock shortens the lock (documented limitation).
        lockUp()
        clock.reboot(offMs = hoursMs(1))
        clock.userSetsClock(hoursMs(12)) // forged past the end of the 10 hour lock
        assertEquals(BlockAction.Allow, controller().onReelScreenOpened())

        // With a trusted reading saved before the reboot and one after it, the forged phone clock is ignored.
        val clock2 = FakeClock(wallMs = wall(IST, 2026, 10, 1, 14, 0), trustedMs = wall(IST, 2026, 10, 1, 14, 0))
        val store2 = FakeStore()
        val c2 = BlockingController(settings, store2, clock2, zone)
        assertTrue(c2.onReelCounted(100))
        clock2.pass(minutesMs(1))
        // reboot: trusted time is re-learned from the server right after boot, the phone clock was forged +8h.
        val trustedAfter = clock2.trustedMs!! + hoursMs(1)
        clock2.reboot(offMs = hoursMs(1))
        clock2.userSetsClock(hoursMs(8))
        clock2.trustedMs = trustedAfter
        val after = BlockingController(settings, store2, clock2, zone)
        // 1 min + 1 h of trusted time passed: about 8h59m left, not unlocked.
        assertTrue(after.onReelScreenOpened() is BlockAction.Block)
    }

    // ----- time zone changes ---------------------------------------------------------------------------------------------

    @Test
    fun `a time zone change does not change a running lock`() {
        lockUp()
        clock.pass(hoursMs(1))
        zone.zone = ZoneId.of("Asia/Tokyo")
        assertEquals(hoursMs(9), block(c.onReelScreenOpened()).remainingMs)
        zone.zone = ZoneId.of("America/New_York")
        assertEquals(hoursMs(9), block(c.onReelScreenSeen()).remainingMs)
    }

    @Test
    fun `bedtime follows the new time zone`() {
        settings.settings = BlockingSettings(bedtime = BedtimeSettings(true))
        clock.wallMs = wall(IST, 2026, 10, 1, 21, 0) // 21:00 in India: daytime
        assertEquals(BlockAction.Allow, c.onReelScreenOpened())
        zone.zone = ZoneId.of("Asia/Tokyo") // the same instant is 00:30 there
        assertEquals(BlockReason.BEDTIME, block(c.onReelScreenSeen()).reason)
    }

    // ----- bedtime and focus ------------------------------------------------------------------------------------------------

    @Test
    fun `bedtime blocks from 11 PM and stops at 6 AM`() {
        settings.settings = BlockingSettings(bedtime = BedtimeSettings(true))
        clock.wallMs = wall(IST, 2026, 10, 1, 22, 59)
        assertEquals(BlockAction.Allow, c.onReelScreenOpened())
        clock.pass(minutesMs(2))
        assertEquals(BlockReason.BEDTIME, block(c.onReelScreenSeen()).reason)
        clock.wallMs = wall(IST, 2026, 10, 2, 5, 59)
        assertEquals(BlockReason.BEDTIME, block(c.onReelScreenSeen()).reason)
        clock.wallMs = wall(IST, 2026, 10, 2, 6, 0)
        assertEquals(BlockAction.Allow, c.onReelScreenSeen())
    }

    @Test
    fun `bedtime blocks even with the timer-lock switched off and the limit not reached`() {
        settings.settings = BlockingSettings(strictLockEnabled = false, bedtime = BedtimeSettings(true))
        clock.wallMs = wall(IST, 2026, 10, 2, 1, 0)
        assertEquals(BlockReason.BEDTIME, block(c.onReelScreenOpened()).reason)
    }

    // ----- the wait-10 gate -------------------------------------------------------------------------------------------------

    private fun gateOn() {
        settings.settings = BlockingSettings(wait10Enabled = true)
    }

    @Test
    fun `opening a reel screen shows the gate and dekhna hai only works after 10 seconds`() {
        gateOn()
        assertEquals(BlockAction.Gate(GateState.Counting(10_000L)), c.onReelScreenOpened())
        clock.pass(5_000L)
        assertFalse(c.continueGate())
        assertEquals(BlockAction.Gate(GateState.Counting(5_000L)), c.onReelScreenSeen())
        clock.pass(5_000L)
        assertEquals(BlockAction.Gate(GateState.Ready), c.onReelScreenSeen())
        assertTrue(c.continueGate())
        assertEquals(BlockAction.Allow, c.onReelScreenSeen())
    }

    @Test
    fun `the gate cannot be skipped by changing the phone clock`() {
        gateOn()
        c.onReelScreenOpened()
        clock.pass(2_000L)
        clock.userSetsClock(hoursMs(1))
        assertFalse(c.continueGate())
    }

    @Test
    fun `leaving for a moment keeps the pass, leaving for longer brings the gate back`() {
        gateOn()
        c.onReelScreenOpened()
        clock.pass(10_000L)
        assertTrue(c.continueGate())
        c.onReelScreenSeen() // the service re-checks the open screen from time to time
        c.onReelScreenClosed()
        clock.pass(1_500L)
        assertEquals(BlockAction.Allow, c.onReelScreenOpened()) // quick back and forth

        c.onReelScreenClosed()
        clock.pass(WaitGate.LEAVE_GRACE_MS + 1_000L)
        assertEquals(BlockAction.Gate(GateState.Counting(10_000L)), c.onReelScreenOpened())
    }

    @Test
    fun `continuing without an open gate does nothing`() {
        gateOn()
        assertFalse(c.continueGate())
    }

    // ----- friend unlock ---------------------------------------------------------------------------------------------------------

    private fun withFriend() {
        settings.settings = settings.settings.copy(friendUnlockEnabled = true, buddy = Buddy(AMAN, "Aman"))
    }

    @Test
    fun `a friend approval lifts the lock for 15 minutes and then it comes back`() {
        withFriend()
        lockUp()
        assertTrue(block(c.onReelScreenOpened()).canAskFriend)
        c.onUnlockRequested("r1")
        assertTrue(block(c.onReelScreenSeen()).askPending)
        assertTrue(c.onFriendApproved("r1", UnlockRules.PASS_LENGTH.toMillis()))
        assertEquals(BlockAction.Allow, c.onReelScreenSeen())
        clock.pass(minutesMs(14))
        assertEquals(BlockAction.Allow, c.onReelScreenSeen())
        clock.pass(minutesMs(1) + 1)
        val back = block(c.onReelScreenSeen())
        assertEquals(BlockReason.STRICT_LOCK, back.reason)
        // The lock kept running underneath the pass.
        assertEquals(hoursMs(10) - minutesMs(15) - 1, back.remainingMs)
    }

    @Test
    fun `a pass survives the app being killed`() {
        withFriend()
        lockUp()
        c.onFriendApproved("r1", UnlockRules.PASS_LENGTH.toMillis())
        clock.pass(minutesMs(5))
        assertEquals(BlockAction.Allow, controller().onReelScreenOpened())
        clock.pass(minutesMs(11))
        assertTrue(controller().onReelScreenOpened() is BlockAction.Block)
    }

    @Test
    fun `changing the phone clock cannot extend a pass`() {
        withFriend()
        lockUp()
        c.onFriendApproved("r1", UnlockRules.PASS_LENGTH.toMillis())
        clock.pass(minutesMs(14))
        clock.userSetsClock(-hoursMs(3))
        clock.pass(minutesMs(2))
        assertTrue(c.onReelScreenSeen() is BlockAction.Block)
    }

    @Test
    fun `an approval that arrives too late gives nothing`() {
        withFriend()
        lockUp()
        assertFalse(c.onFriendApproved("r1", 0L))
        assertTrue(c.onReelScreenOpened() is BlockAction.Block)
    }

    @Test
    fun `an approval is ignored when friend unlock was switched off`() {
        settings.settings = settings.settings.copy(friendUnlockEnabled = false)
        lockUp()
        assertFalse(c.onFriendApproved("r1", UnlockRules.PASS_LENGTH.toMillis()))
    }

    @Test
    fun `three requests a day then the button is gone, until the oldest falls out after 24 hours`() {
        withFriend()
        lockUp()
        for (i in 1..3) {
            assertTrue(block(c.onReelScreenSeen()).canAskFriend)
            c.onUnlockRequested("r$i")
            c.onUnlockResolved("r$i") // the friend answered or ignored it
            clock.pass(minutesMs(31))
        }
        val a = block(c.onReelScreenSeen())
        assertEquals(0, a.askQuotaLeft)
        assertFalse(a.canAskFriend)
        // 24h+ after the first request (the lock itself is long over by then, so read the quota from status).
        clock.pass(hoursMs(23)) // now 24h33m after the first request and 24h02m after the second: both are out
        assertEquals(2, c.status().askQuotaLeft)
    }

    @Test
    fun `while a request waits there is no second ask, and it expires after 30 minutes`() {
        withFriend()
        lockUp()
        c.onUnlockRequested("r1")
        assertFalse(block(c.onReelScreenSeen()).canAskFriend)
        clock.pass(minutesMs(31))
        assertTrue(block(c.onReelScreenSeen()).canAskFriend) // the first request expired, 2 left
        assertEquals(2, block(c.onReelScreenSeen()).askQuotaLeft)
    }

    @Test
    fun `no network means no request was sent so the quota is not used`() {
        withFriend()
        lockUp()
        // The phone tried and failed: the service never calls onUnlockRequested.
        assertEquals(3, block(c.onReelScreenOpened()).askQuotaLeft)
        assertTrue(block(c.onReelScreenSeen()).canAskFriend)
    }

    @Test
    fun `the request history survives a restart`() {
        withFriend()
        lockUp()
        c.onUnlockRequested("r1")
        c.onUnlockResolved("r1")
        assertEquals(2, block(controller().onReelScreenOpened()).askQuotaLeft)
    }

    @Test
    fun `peek decides without starting a gate session`() {
        gateOn()
        assertEquals(BlockAction.Gate(GateState.Counting(WaitGate.COUNTDOWN_MS)), c.peek())
        clock.pass(30_000L)
        // No session was started by peek, so it still shows a fresh countdown instead of "ready".
        assertEquals(BlockAction.Gate(GateState.Counting(WaitGate.COUNTDOWN_MS)), c.peek())
    }

    @Test
    fun `a resolved id that is not the pending one changes nothing`() {
        withFriend()
        lockUp()
        c.onUnlockRequested("r1")
        c.onUnlockResolved("other")
        assertTrue(c.status().askPending)
    }

    // ----- settings -----------------------------------------------------------------------------------------------------------------

    @Test
    fun `the lock cannot be switched off from settings while it runs, and can after`() {
        lockUp()
        val refused = c.change(SettingChange.StrictLockEnabled(false)) as ChangeResult.Refused
        assertEquals(ChangeBlocker.LOCK_RUNNING, refused.blocker)
        assertTrue(settings.settings.strictLockEnabled)
        clock.pass(hoursMs(10))
        assertTrue(c.change(SettingChange.StrictLockEnabled(false)) is ChangeResult.Applied)
        assertFalse(settings.settings.strictLockEnabled)
    }

    @Test
    fun `a change that is refused after an app restart is still refused`() {
        lockUp()
        clock.pass(minutesMs(1))
        assertTrue(controller().change(SettingChange.StrictLockEnabled(false)) is ChangeResult.Refused)
    }

    @Test
    fun `switching the lock off is refused the same way after a reboot`() {
        lockUp()
        clock.reboot(offMs = minutesMs(10))
        assertTrue(controller().change(SettingChange.StrictLockEnabled(false)) is ChangeResult.Refused)
    }

    @Test
    fun `a changed setting is saved`() {
        val before = settings.saves
        assertTrue(c.change(SettingChange.Wait10Enabled(true)) is ChangeResult.Applied)
        assertEquals(before + 1, settings.saves)
        assertTrue(settings.settings.wait10Enabled)
    }

    @Test
    fun `a refused change saves nothing`() {
        lockUp()
        val before = settings.saves
        c.change(SettingChange.StrictLockEnabled(false))
        assertEquals(before, settings.saves)
    }

    // ----- saving and waking ---------------------------------------------------------------------------------------------------------

    @Test
    fun `plain time passing does not rewrite the saved state`() {
        lockUp()
        val saves = store.saves
        repeat(20) {
            clock.pass(minutesMs(5))
            c.status()
            c.isWatching()
        }
        assertEquals(saves, store.saves)
    }

    @Test
    fun `starting and ending the lock are saved`() {
        lockUp()
        assertTrue(store.saved.lock is StrictLockState.Locked)
        clock.pass(hoursMs(10))
        c.status()
        assertTrue(store.saved.lock is StrictLockState.Idle)
    }

    @Test
    fun `nothing watching nothing scheduled still wakes at midnight to roll the day`() {
        assertFalse(c.isWatching())
        assertEquals(hoursMs(10), c.nextWakeDelayMs())
    }

    @Test
    fun `the next wake is the soonest of lock end, pass end, window and midnight`() {
        settings.settings = settings.settings.copy(lockLength = LockLength.Hours(2), bedtime = BedtimeSettings(true))
        lockUp()
        assertEquals(hoursMs(2), c.nextWakeDelayMs()) // lock ends before 23:00 and before midnight
        clock.pass(minutesMs(110)) // 15:50, lock has 10 minutes left
        assertEquals(minutesMs(10), c.nextWakeDelayMs())
        clock.pass(minutesMs(10))
        assertEquals(hoursMs(7), c.nextWakeDelayMs()) // 16:00 -> bedtime starts at 23:00
    }

    @Test
    fun `the next wake is never sooner than a second or later than a day`() {
        lockUp()
        clock.pass(hoursMs(10) - 200)
        assertEquals(BlockingController.MIN_WAKE_MS, c.nextWakeDelayMs())
        val far = BlockingController(FakeSettings(), FakeStore(), FakeClock(wall(IST, 2026, 10, 1, 0, 0, 1)), zone)
        assertTrue(far.nextWakeDelayMs()!! <= BlockingController.MAX_WAKE_MS)
    }

    @Test
    fun `a pending request makes the next wake its expiry`() {
        withFriend()
        lockUp()
        c.onUnlockRequested("r1")
        assertEquals(UnlockRules.REQUEST_TTL.toMillis(), c.nextWakeDelayMs())
    }

    @Test
    fun `status reports the pass the window and the lock for the settings screen`() {
        withFriend()
        lockUp()
        c.onFriendApproved("r1", UnlockRules.PASS_LENGTH.toMillis())
        val s = c.status()
        assertTrue(s.lockRunning)
        assertEquals(hoursMs(10), s.lockRemainingMs)
        assertNotNull(s.pass)
        assertNull(s.window)
    }
}
