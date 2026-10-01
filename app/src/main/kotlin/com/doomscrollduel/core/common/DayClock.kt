package com.doomscrollduel.core.common

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart

/**
 * Emits today's local date now and again whenever it changes, so "today" screens and the counter
 * roll over at local midnight without the app being restarted.
 *
 * [externalChanges] should fire when the system clock or time zone is changed; each one restarts the
 * wait for midnight with the new offset.
 */
class DayClock(
    private val nowMillis: () -> Long,
    private val zone: () -> ZoneId,
    private val externalChanges: Flow<Unit> = emptyFlow(),
) {
    fun nowDate(): LocalDate = DayKeys.dateOf(nowMillis(), zone())

    @OptIn(ExperimentalCoroutinesApi::class)
    val today: Flow<LocalDate> = externalChanges
        .onStart { emit(Unit) }
        .flatMapLatest {
            flow {
                while (true) {
                    emit(nowDate())
                    delay(DayKeys.millisUntilNextDay(nowMillis(), zone()))
                }
            }
        }
        .distinctUntilChanged()
}
