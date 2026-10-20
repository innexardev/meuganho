package com.innexar.meuganho.features.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.time.DateRange
import com.innexar.meuganho.data.local.entity.GoalEntity
import com.innexar.meuganho.domain.financial.GoalProgress
import com.innexar.meuganho.domain.model.GoalKind
import com.innexar.meuganho.domain.model.GoalPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalRow(val period: GoalPeriod, val goal: GoalEntity?, val progress: GoalProgress?)

class GoalsViewModel(private val container: AppContainer) : ViewModel() {
    private val stats = container.statsRepository

    val rows: StateFlow<List<GoalRow>> = combine(
        stats.observeGoals(),
        stats.observeSummary(DateRange.today()),
        stats.observeSummary(DateRange.thisWeek()),
        stats.observeSummary(DateRange.thisMonth()),
    ) { goals, day, week, month ->
        GoalPeriod.entries.map { period ->
            val goal = goals.firstOrNull { it.period == period }
            val summary = when (period) {
                GoalPeriod.DAILY -> day.first
                GoalPeriod.WEEKLY -> week.first
                GoalPeriod.MONTHLY -> month.first
            }
            GoalRow(period, goal, goal?.let { stats.progressFor(it, summary) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(period: GoalPeriod, kind: GoalKind, target: String, existing: GoalEntity?) {
        val cents = Money.parseInput(target)
        viewModelScope.launch {
            val dao = container.database.goalDao()
            if (cents == null || cents <= 0) {
                existing?.let { dao.delete(it.id) }
            } else {
                dao.upsert(GoalEntity(id = existing?.id ?: 0, period = period, kind = kind, targetCents = cents))
            }
        }
    }
}
