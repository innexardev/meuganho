package com.innexar.meuganho.features.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.time.DateRange
import com.innexar.meuganho.data.mapper.toComparison
import com.innexar.meuganho.data.mapper.toFacts
import com.innexar.meuganho.domain.stats.AnalyticsCalculator
import com.innexar.meuganho.domain.stats.Bucket
import com.innexar.meuganho.domain.stats.EstimateVsReal
import com.innexar.meuganho.domain.stats.EstimateVsRealSummary
import com.innexar.meuganho.domain.stats.PeriodSummary
import com.innexar.meuganho.domain.stats.PlatformComparison
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalyticsState(
    val loaded: Boolean = false,
    val summary: PeriodSummary? = null,
    val byHour: List<Bucket> = emptyList(),
    val byWeekday: List<Bucket> = emptyList(),
    val platforms: List<PlatformComparison> = emptyList(),
    val estimateVsReal: EstimateVsRealSummary? = null,
)

/** Análises dos últimos 90 dias, calculadas no aparelho, sem enviar nada para servidor. */
class AnalyticsViewModel(container: AppContainer) : ViewModel() {
    private val range = DateRange.lastDays(90)
    private val db = container.database

    val state: StateFlow<AnalyticsState> = combine(
        container.statsRepository.observeSummary(range, com.innexar.meuganho.data.repository.StatsRepository.staticNow()),
        db.shiftDao().observeOverlapping(range.startMillis, range.endMillis),
    ) { (summary, trips), shifts ->
        val now = System.currentTimeMillis()
        val facts = trips.map { it.toFacts() }
        val shiftFacts = shifts.map { it.toFacts() }
        AnalyticsState(
            loaded = true,
            summary = summary,
            byHour = AnalyticsCalculator.byHourOfDay(facts, shiftFacts, now),
            byWeekday = AnalyticsCalculator.byDayOfWeek(facts, shiftFacts, now),
            platforms = AnalyticsCalculator.comparePlatforms(facts),
            estimateVsReal = EstimateVsReal.summarize(trips.mapNotNull { it.toComparison() }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())
}
