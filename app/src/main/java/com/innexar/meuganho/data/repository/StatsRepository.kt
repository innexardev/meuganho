package com.innexar.meuganho.data.repository

import com.innexar.meuganho.core.time.DateRange
import com.innexar.meuganho.data.local.dao.ExpenseDao
import com.innexar.meuganho.data.local.dao.FuelLogDao
import com.innexar.meuganho.data.local.dao.GoalDao
import com.innexar.meuganho.data.local.dao.OfferDao
import com.innexar.meuganho.data.local.dao.PlatformTotalDao
import com.innexar.meuganho.data.local.dao.ShiftDao
import com.innexar.meuganho.data.local.dao.TripDao
import com.innexar.meuganho.data.local.entity.GoalEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.mapper.toFacts
import com.innexar.meuganho.domain.financial.GoalCalculator
import com.innexar.meuganho.domain.financial.GoalProgress
import com.innexar.meuganho.domain.model.GoalKind
import com.innexar.meuganho.domain.model.GoalPeriod
import com.innexar.meuganho.domain.stats.PeriodSummary
import com.innexar.meuganho.domain.stats.PlatformReconciliation
import com.innexar.meuganho.domain.stats.Reconciliation
import com.innexar.meuganho.domain.stats.SummaryCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

data class DayOverview(
    val date: LocalDate,
    val summary: PeriodSummary,
    val trips: List<TripEntity>,
    val goal: GoalEntity?,
    val goalProgress: GoalProgress?,
    val reconciliation: List<PlatformReconciliation>,
)

/**
 * Estatísticas por período, recalculadas só quando o Room avisa que uma tabela
 * mudou (Flow). Não há consultas periódicas ao banco.
 *
 * [nowTicks] permite que telas visíveis atualizem o tempo do turno aberto;
 * sem assinantes, nada roda.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsRepository(
    private val tripDao: TripDao,
    private val shiftDao: ShiftDao,
    private val fuelLogDao: FuelLogDao,
    private val expenseDao: ExpenseDao,
    private val goalDao: GoalDao,
    private val platformTotalDao: PlatformTotalDao,
    private val offerDao: OfferDao,
    private val costs: CostModelProvider,
) {

    fun observeSummary(range: DateRange, nowTicks: Flow<Long> = minuteTicks()): Flow<Pair<PeriodSummary, List<TripEntity>>> =
        combine(
            tripDao.observeBetween(range.startMillis, range.endMillis),
            shiftDao.observeOverlapping(range.startMillis, range.endMillis),
            fuelLogDao.observeBetween(range.startMillis, range.endMillis),
            expenseDao.observeBetween(range.startMillis, range.endMillis),
            combine(costs.costModel, nowTicks) { cost, now -> cost to now },
        ) { trips, shifts, fuel, expenses, (cost, now) ->
            SummaryCalculator.summarize(
                range = range,
                trips = trips.map { it.toFacts() },
                shifts = shifts.map { it.toFacts() },
                fuelPurchasedCents = fuel.sumOf { it.totalCents },
                expensesCents = expenses.sumOf { it.amountCents },
                costModel = cost,
                now = now,
            ) to trips
        }

    fun observeDay(date: LocalDate, nowTicks: Flow<Long> = minuteTicks()): Flow<DayOverview> =
        combine(
            observeSummary(DateRange.day(date), nowTicks),
            goalDao.observeActive(),
            platformTotalDao.observeDay(date.toEpochDay()),
        ) { (summary, trips), goals, totals ->
            val goal = goals.firstOrNull { it.period == GoalPeriod.DAILY }
            val latestTotals = totals.groupBy { it.platform }.mapValues { (_, l) -> l.maxBy { it.capturedAt }.totalCents }
            DayOverview(
                date = date,
                summary = summary,
                trips = trips,
                goal = goal,
                goalProgress = goal?.let { progressFor(it, summary) },
                reconciliation = Reconciliation.of(summary.byPlatform, latestTotals),
            )
        }

    /** "Hoje" que vira sozinho à meia-noite: um único delay por dia, não polling. */
    fun observeToday(nowTicks: Flow<Long> = minuteTicks()): Flow<DayOverview> =
        todayDates().flatMapLatest { observeDay(it, nowTicks) }

    val reviewCount: Flow<Int> =
        combine(offerDao.observeNeedingReview(), tripDao.observeNeedingReview()) { o, t -> o.size + t.size }

    fun progressFor(goal: GoalEntity, summary: PeriodSummary): GoalProgress {
        val current = if (goal.kind == GoalKind.GROSS) summary.grossCents else summary.operationalResultCents
        return GoalCalculator.progress(current, goal.targetCents, summary.centsPerHourOnline)
    }

    fun observeGoals(): Flow<List<GoalEntity>> = goalDao.observeActive()

    companion object {
        /**
         * Desligado só nas capturas de tela dos testes: com o relógio virtual do Compose,
         * os temporizadores abaixo disparariam sem parar e a tela nunca "assentaria".
         */
        @Volatile var ticking: Boolean = true

        fun todayDates(): Flow<LocalDate> = flow {
            if (!ticking) {
                emit(LocalDate.now(ZoneId.systemDefault()))
                return@flow
            }
            while (true) {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                emit(today)
                val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                delay(nextMidnight - System.currentTimeMillis() + 1_000)
            }
        }

        /** Relógio de 1 min para telas visíveis (tempo online do turno aberto). */
        fun minuteTicks(): Flow<Long> = flow {
            if (!ticking) {
                emit(System.currentTimeMillis())
                return@flow
            }
            while (true) {
                emit(System.currentTimeMillis())
                delay(60_000)
            }
        }

        fun staticNow(): Flow<Long> = flow { emit(System.currentTimeMillis()) }
    }
}

fun Flow<Pair<PeriodSummary, List<TripEntity>>>.summaryOnly(): Flow<PeriodSummary> = map { it.first }
