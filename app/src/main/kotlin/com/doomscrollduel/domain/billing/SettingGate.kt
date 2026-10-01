package com.doomscrollduel.domain.billing

import com.doomscrollduel.domain.blocking.BlockingSettings
import com.doomscrollduel.domain.blocking.FocusSettings
import com.doomscrollduel.domain.blocking.SettingChange

/**
 * Which Settings changes need Pro. The idea is that Pro never TAKES AWAY a protection a person already set up:
 * a free player whose Pro ended keeps their running Strict Lock and focus schedule, can switch them off (when the
 * commitment rule allows) and can trim a schedule, but cannot switch them on again or add and edit ranges.
 *
 * | Change | Needs Pro |
 * |---|---|
 * | Strict timer-lock ON | yes (STRICT_LOCK) |
 * | Strict timer-lock OFF, length, limit | no |
 * | Focus hours ON, or any change that adds or moves a range | yes (CUSTOM_SCHEDULES) |
 * | Focus hours OFF, or removing ranges | no |
 * | Bedtime (default 11 PM to 6 AM and its range), Wait-10, friend unlock | no |
 */
object SettingGate {
    fun required(change: SettingChange, current: BlockingSettings): ProFeature? = when (change) {
        is SettingChange.StrictLockEnabled -> if (change.on) ProFeature.STRICT_LOCK else null
        is SettingChange.FocusEnabled -> if (change.on) ProFeature.CUSTOM_SCHEDULES else null
        is SettingChange.FocusChanged -> if (onlyRemoves(current.focus, change.focus)) null else ProFeature.CUSTOM_SCHEDULES
        else -> null
    }

    fun check(change: SettingChange, current: BlockingSettings, isPro: Boolean): Access {
        val needed = required(change, current) ?: return Access.Allowed
        return FreeLimits.check(needed, isPro)
    }

    /** True when every range in [next] already exists in [old]: nothing was added or moved. */
    private fun onlyRemoves(old: FocusSettings, next: FocusSettings): Boolean =
        next.days.all { (day, ranges) -> ranges.all { it in old.rangesOn(day) } }
}
