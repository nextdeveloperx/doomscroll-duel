package com.doomscrollduel.domain.blocking

import com.doomscrollduel.domain.challenge.AMAN
import com.doomscrollduel.domain.challenge.lock.LockLength
import com.doomscrollduel.domain.challenge.lock.StrictLockState
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPolicyTest {
    private val today = LocalDate.of(2026, 10, 1)
    private val nowMs = wall(IST, 2026, 10, 1, 23, 30)
    private val sample = sample(nowMs, 1_000_000L)
    private val idle = StrictLockState.Idle(today)
    private val locked = StrictLockState.Locked(today, nowMs, hoursMs(10), 0L, sample, 100)
    private val buddy = Buddy(AMAN, "Aman")

    private val base = BlockingSettings(strictLockEnabled = true, friendUnlockEnabled = true, buddy = buddy)
    private val bedtimeWindow = ActiveWindow(WindowKind.BEDTIME, inst(wall(IST, 2026, 10, 1, 23, 0)), inst(wall(IST, 2026, 10, 2, 6, 0)))
    private val focusWindow = ActiveWindow(WindowKind.FOCUS, inst(wall(IST, 2026, 10, 1, 22, 0)), inst(wall(IST, 2026, 10, 2, 0, 0)))

    private fun apply(change: SettingChange, s: BlockingSettings = base, lock: StrictLockState = idle, window: ActiveWindow? = null) =
        SettingsPolicy.apply(s, change, lock, window, today, nowMs)

    private fun applied(r: ChangeResult) = (r as ChangeResult.Applied).settings
    private fun refused(r: ChangeResult) = r as ChangeResult.Refused

    // ----- the timer-lock cannot be disabled while it runs ---------------------------------------------

    @Test
    fun `the timer-lock can be switched off when it is not running`() {
        assertEquals(false, applied(apply(SettingChange.StrictLockEnabled(false))).strictLockEnabled)
    }

    @Test
    fun `the timer-lock cannot be switched off while it runs`() {
        val r = refused(apply(SettingChange.StrictLockEnabled(false), lock = locked))
        assertEquals(ChangeBlocker.LOCK_RUNNING, r.blocker)
        assertEquals(hoursMs(10), r.untilMs)
    }

    @Test
    fun `it can always be switched on, also while locked`() {
        val off = base.copy(strictLockEnabled = false)
        assertEquals(true, applied(apply(SettingChange.StrictLockEnabled(true), off, lock = locked)).strictLockEnabled)
    }

    @Test
    fun `the lock length cannot change while locked`() {
        assertEquals(ChangeBlocker.LOCK_RUNNING, refused(apply(SettingChange.LockLengthChanged(LockLength.Hours(1)), lock = locked)).blocker)
        assertEquals(LockLength.Hours(1), applied(apply(SettingChange.LockLengthChanged(LockLength.Hours(1)))).lockLength)
    }

    // ----- the daily limit ---------------------------------------------------------------------------------

    @Test
    fun `lowering the limit is immediate and raising it starts tomorrow`() {
        val lowered = applied(apply(SettingChange.DailyLimitChanged(60)))
        assertEquals(60, lowered.limitOn(today))
        val raised = applied(apply(SettingChange.DailyLimitChanged(300)))
        assertEquals(100, raised.limitOn(today))
        assertEquals(300, raised.limitOn(today.plusDays(1)))
    }

    @Test
    fun `the limit cannot change while locked and must be 10 to 1000`() {
        assertEquals(ChangeBlocker.LOCK_RUNNING, refused(apply(SettingChange.DailyLimitChanged(500), lock = locked)).blocker)
        assertEquals(ChangeBlocker.OUT_OF_RANGE, refused(apply(SettingChange.DailyLimitChanged(9))).blocker)
        assertEquals(ChangeBlocker.OUT_OF_RANGE, refused(apply(SettingChange.DailyLimitChanged(1_001))).blocker)
    }

    // ----- friend unlock: loosening is refused while something is running ---------------------------------------

    @Test
    fun `friend unlock needs a friend first`() {
        val noBuddy = base.copy(buddy = null, friendUnlockEnabled = false)
        assertEquals(ChangeBlocker.NEEDS_BUDDY, refused(apply(SettingChange.FriendUnlockEnabled(true), noBuddy)).blocker)
    }

    @Test
    fun `turning friend unlock on is refused while locked or in a window, turning it off is allowed`() {
        val off = base.copy(friendUnlockEnabled = false)
        assertEquals(ChangeBlocker.LOCK_RUNNING, refused(apply(SettingChange.FriendUnlockEnabled(true), off, lock = locked)).blocker)
        assertEquals(ChangeBlocker.WINDOW_RUNNING, refused(apply(SettingChange.FriendUnlockEnabled(true), off, window = bedtimeWindow)).blocker)
        assertEquals(false, applied(apply(SettingChange.FriendUnlockEnabled(false), lock = locked, window = bedtimeWindow)).friendUnlockEnabled)
    }

    @Test
    fun `the friend cannot be swapped in the middle of a lock or window`() {
        val other = Buddy(com.doomscrollduel.domain.challenge.RIYA, "Riya")
        assertEquals(ChangeBlocker.LOCK_RUNNING, refused(apply(SettingChange.BuddyChanged(other), lock = locked)).blocker)
        assertEquals(ChangeBlocker.WINDOW_RUNNING, refused(apply(SettingChange.BuddyChanged(other), window = focusWindow)).blocker)
        assertEquals(other, applied(apply(SettingChange.BuddyChanged(other))).buddy)
    }

    @Test
    fun `removing the friend also switches friend unlock off`() {
        val s = applied(apply(SettingChange.BuddyChanged(null)))
        assertEquals(false, s.friendUnlockEnabled)
        assertEquals(null, s.buddy)
    }

    // ----- bedtime and focus ----------------------------------------------------------------------------------

    @Test
    fun `bedtime can be switched on any time and off only outside its window`() {
        val off = base.copy(bedtime = BedtimeSettings(false))
        assertTrue(applied(apply(SettingChange.BedtimeEnabled(true), off, window = bedtimeWindow)).bedtime.enabled)
        val on = base.copy(bedtime = BedtimeSettings(true))
        assertEquals(ChangeBlocker.WINDOW_RUNNING, refused(apply(SettingChange.BedtimeEnabled(false), on, window = bedtimeWindow)).blocker)
        assertEquals(false, applied(apply(SettingChange.BedtimeEnabled(false), on, window = null)).bedtime.enabled)
    }

    @Test
    fun `the bedtime hours cannot be edited while bedtime is running`() {
        val range = TimeRange(LocalTime.of(23, 30), LocalTime.of(5, 0))
        assertEquals(ChangeBlocker.WINDOW_RUNNING, refused(apply(SettingChange.BedtimeRangeChanged(range), window = bedtimeWindow)).blocker)
        assertEquals(range, applied(apply(SettingChange.BedtimeRangeChanged(range))).bedtime.range)
    }

    @Test
    fun `a focus window does not lock the bedtime settings and the other way round`() {
        val both = base.copy(bedtime = BedtimeSettings(true), focus = FocusSettings(true))
        assertEquals(false, applied(apply(SettingChange.BedtimeEnabled(false), both, window = focusWindow)).bedtime.enabled)
        assertEquals(false, applied(apply(SettingChange.FocusEnabled(false), both, window = bedtimeWindow)).focus.enabled)
        assertEquals(ChangeBlocker.WINDOW_RUNNING, refused(apply(SettingChange.FocusEnabled(false), both, window = focusWindow)).blocker)
    }

    @Test
    fun `the focus schedule cannot be edited while a focus window runs and keeps its on-off state`() {
        val schedule = FocusSettings(true, mapOf(java.time.DayOfWeek.MONDAY to listOf(TimeRange(LocalTime.of(9, 0), LocalTime.of(10, 0)))))
        assertEquals(ChangeBlocker.WINDOW_RUNNING, refused(apply(SettingChange.FocusChanged(schedule), window = focusWindow)).blocker)
        val s = applied(apply(SettingChange.FocusChanged(schedule.copy(enabled = true)), base.copy(focus = FocusSettings(enabled = false))))
        assertEquals(false, s.focus.enabled) // saving a schedule does not switch focus on by itself
        assertEquals(1, s.focus.days.size)
    }

    @Test
    fun `the wait-10 gate can always be changed`() {
        assertEquals(true, applied(apply(SettingChange.Wait10Enabled(true), lock = locked, window = bedtimeWindow)).wait10Enabled)
        assertEquals(false, applied(apply(SettingChange.Wait10Enabled(false), base.copy(wait10Enabled = true), lock = locked)).wait10Enabled)
    }

    @Test
    fun `a refusal never changes the settings object`() {
        val before = base
        apply(SettingChange.StrictLockEnabled(false), before, lock = locked)
        assertEquals(base, before)
    }
}
