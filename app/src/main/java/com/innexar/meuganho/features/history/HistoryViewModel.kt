package com.innexar.meuganho.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.time.DateRange
import com.innexar.meuganho.domain.stats.PeriodSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class HistoryPeriod(val label: String) { TODAY("Hoje"), YESTERDAY("Ontem"), WEEK("Semana"), MONTH("Mês"), LAST30("30 dias"), CUSTOM("Período") }

data class DayRow(val date: LocalDate, val grossCents: Long, val trips: Int, val pending: Int)

data class HistoryState(val summary: PeriodSummary? = null, val days: List<DayRow> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val period = MutableStateFlow(HistoryPeriod.WEEK)
    val custom = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)

    val state: StateFlow<HistoryState> = combine(period, custom) { p, c -> p to c }.flatMapLatest { (p, c) ->
        container.statsRepository.observeSummary(rangeFor(p, c)).map { (summary, trips) ->
            val days = trips.groupBy { DateFormats.localDate(it.acceptedAt) }.map { (date, list) ->
                DayRow(
                    date = date,
                    grossCents = list.filter { it.status.countsInStats }.sumOf { it.effectiveCents ?: 0 },
                    trips = list.count { it.status.countsInStats },
                    pending = list.count { it.status.isPending },
                )
            }.sortedByDescending { it.date }
            HistoryState(summary, days)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    fun select(p: HistoryPeriod) {
        period.value = p
    }

    fun setCustom(from: LocalDate, to: LocalDate) {
        custom.value = from to to
        period.value = HistoryPeriod.CUSTOM
    }

    private fun rangeFor(p: HistoryPeriod, c: Pair<LocalDate, LocalDate>?): DateRange {
        val today = LocalDate.now()
        return when (p) {
            HistoryPeriod.TODAY -> DateRange.today()
            HistoryPeriod.YESTERDAY -> DateRange.day(today.minusDays(1))
            HistoryPeriod.WEEK -> DateRange.thisWeek()
            HistoryPeriod.MONTH -> DateRange.thisMonth()
            HistoryPeriod.LAST30 -> DateRange.lastDays(30)
            HistoryPeriod.CUSTOM -> c?.let { DateRange.days(it.first, it.second) } ?: DateRange.thisWeek()
        }
    }
}
