package com.doomscrollduel.data.prefs

import com.doomscrollduel.core.common.DayClock
import com.doomscrollduel.domain.blocking.ObservableBlockingSettings
import com.doomscrollduel.domain.repository.ReelLimitStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The daily limit in force today. It comes from the blocking settings so the brain, the streak and the timer-lock
 * always agree, and it moves to a raised limit by itself at midnight.
 */
@Singleton
class SettingsBackedReelLimitStore @Inject constructor(
    settings: ObservableBlockingSettings,
    dayClock: DayClock,
) : ReelLimitStore {
    override val limit: Flow<Int> =
        combine(settings.flow, dayClock.today) { s, day -> s.limitOn(day) }.distinctUntilChanged()
}
