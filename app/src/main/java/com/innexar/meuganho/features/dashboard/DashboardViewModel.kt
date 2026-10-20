package com.innexar.meuganho.features.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.data.repository.DayOverview
import com.innexar.meuganho.domain.financial.CostModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val day: DayOverview? = null,
    val openShift: ShiftEntity? = null,
    val reviewCount: Int = 0,
    val hasVehicle: Boolean = true,
    val costModel: CostModel? = null,
    val thresholds: com.innexar.meuganho.domain.financial.RatingThresholds = com.innexar.meuganho.domain.financial.RatingThresholds(),
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    fun togglePause() {
        container.appScope.launch { container.togglePause(System.currentTimeMillis()) }
    }

    val state: StateFlow<DashboardState> = combine(
        container.statsRepository.observeToday(),
        container.shiftRepository.openShift,
        container.statsRepository.reviewCount,
        container.database.vehicleDao().observeActive(),
        kotlinx.coroutines.flow.combine(container.costModelProvider.costModel, container.settingsRepository.settings) { c, s -> c to s.thresholds },
    ) { day, shift, reviews, vehicle, (cost, thresholds) ->
        DashboardState(day, shift, reviews, vehicle != null, cost, thresholds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())
}
