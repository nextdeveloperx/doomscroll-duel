package com.doomscrollduel.domain.billing

import com.doomscrollduel.domain.blocking.BlockingSettings
import com.doomscrollduel.domain.blocking.FocusScheduleEditor
import com.doomscrollduel.domain.blocking.FocusSettings
import com.doomscrollduel.domain.blocking.SettingChange
import com.doomscrollduel.domain.blocking.TimeRange
import com.doomscrollduel.domain.challenge.lock.LockLength
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingGateTest {
    private val base = BlockingSettings()
    private val r1 = TimeRange(LocalTime.of(16, 0), LocalTime.of(18, 0))
    private val r2 = TimeRange(LocalTime.of(20, 0), LocalTime.of(21, 0))

    private fun focusWith(vararg ranges: TimeRange): FocusSettings {
        var f = FocusSettings(enabled = true)
        for (r in ranges) f = (FocusScheduleEditor.add(f, DayOfWeek.MONDAY, r) as FocusScheduleEditor.Result.Ok).focus
        return f
    }

    @Test fun `switching strict lock on needs Pro, off does not`() {
        assertEquals(ProFeature.STRICT_LOCK, SettingGate.required(SettingChange.StrictLockEnabled(true), base))
        assertNull(SettingGate.required(SettingChange.StrictLockEnabled(false), base))
        assertEquals(Access.NeedsPro(ProFeature.STRICT_LOCK), SettingGate.check(SettingChange.StrictLockEnabled(true), base, isPro = false))
        assertEquals(Access.Allowed, SettingGate.check(SettingChange.StrictLockEnabled(true), base, isPro = true))
    }

    @Test fun `focus on needs Pro, off does not`() {
        assertEquals(ProFeature.CUSTOM_SCHEDULES, SettingGate.required(SettingChange.FocusEnabled(true), base))
        assertNull(SettingGate.required(SettingChange.FocusEnabled(false), base))
    }

    @Test fun `adding or moving a focus range needs Pro`() {
        val current = base.copy(focus = focusWith(r1))
        assertEquals(ProFeature.CUSTOM_SCHEDULES, SettingGate.required(SettingChange.FocusChanged(focusWith(r1, r2)), current))
        assertEquals(ProFeature.CUSTOM_SCHEDULES, SettingGate.required(SettingChange.FocusChanged(focusWith(r2)), current))
    }

    @Test fun `removing focus ranges stays free so Pro ending never traps anyone`() {
        val current = base.copy(focus = focusWith(r1, r2))
        assertNull(SettingGate.required(SettingChange.FocusChanged(focusWith(r1)), current))
        assertNull(SettingGate.required(SettingChange.FocusChanged(FocusSettings(enabled = true)), current))
    }

    @Test fun `everything else is free`() {
        listOf(
            SettingChange.Wait10Enabled(true),
            SettingChange.BedtimeEnabled(true),
            SettingChange.FriendUnlockEnabled(true),
            SettingChange.DailyLimitChanged(100),
            SettingChange.LockLengthChanged(LockLength.Hours(1)),
        ).forEach { assertNull(it.toString(), SettingGate.required(it, base)) }
    }
}
