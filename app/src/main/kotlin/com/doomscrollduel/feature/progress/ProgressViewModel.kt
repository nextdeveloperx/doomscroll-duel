package com.doomscrollduel.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscrollduel.core.common.DayClock
import com.doomscrollduel.domain.repository.ReelLimitStore
import com.doomscrollduel.domain.repository.ReelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ProgressMode { DAILY, WEEKLY }

/**
 * What the Progress chart shows. [values] is 24 numbers (one per clock hour) in DAILY mode and 7 numbers
 * (one per day, oldest first) in WEEKLY mode. [offset] is how far back from today the person has gone:
 * 0 = today / the last 7 days, -1 = yesterday / the 7 days before that, and so on.
 */
data class ProgressUi(
    val mode: ProgressMode = ProgressMode.DAILY,
    val offset: Int = 0,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate = LocalDate.now(),
    val values: List<Int> = List(24) { 0 },
    val limit: Int = ReelLimitStore.DEFAULT,
    val canGoBack: Boolean = true,
) {
    val total: Int get() = values.sum()
    val canGoNext: Boolean get() = offset < 0

    /** Index of the busiest hour or day, or null when nothing was counted. */
    val peakIndex: Int? get() = values.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: ReelRepository,
    limits: ReelLimitStore,
    dayClock: DayClock,
) : ViewModel() {

    private val mode = MutableStateFlow(ProgressMode.DAILY)
    private val offset = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<ProgressUi> = combine(mode, offset, dayClock.today, limits.limit) { m, o, today, limit ->
        Query(m, o, today, limit)
    }.flatMapLatest { q ->
        when (q.mode) {
            ProgressMode.DAILY -> {
                val day = q.today.plusDays(q.offset.toLong())
                repository.observeHourly(day).map { hours ->
                    ProgressUi(q.mode, q.offset, day, day, hours, q.limit, canGoBack = q.offset > -MAX_BACK_DAYS)
                }
            }
            ProgressMode.WEEKLY -> {
                val end = q.today.plusDays(7L * q.offset)
                val start = end.minusDays(6)
                repository.observeDailyTotals(start).map { totals ->
                    val days = (0..6).map { totals[start.plusDays(it.toLong())] ?: 0 }
                    ProgressUi(q.mode, q.offset, start, end, days, q.limit, canGoBack = q.offset > -MAX_BACK_WEEKS)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUi())

    fun setMode(next: ProgressMode) {
        if (mode.value == next) return
        mode.value = next
        offset.value = 0
    }

    fun back() { if (ui.value.canGoBack) offset.value -= 1 }

    fun next() { if (offset.value < 0) offset.value += 1 }

    private data class Query(val mode: ProgressMode, val offset: Int, val today: LocalDate, val limit: Int)

    private companion object {
        const val MAX_BACK_DAYS = 60
        const val MAX_BACK_WEEKS = 8
    }
}
