package com.innexar.meuganho.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.data.repository.DayOverview
import com.innexar.meuganho.domain.financial.CostModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DayDetailState(val day: DayOverview? = null, val cost: CostModel? = null)

class DayDetailViewModel(container: AppContainer, epochDay: Long) : ViewModel() {
    val state: StateFlow<DayDetailState> = combine(
        container.statsRepository.observeDay(LocalDate.ofEpochDay(epochDay)),
        container.costModelProvider.costModel,
    ) { day, cost -> DayDetailState(day, cost) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayDetailState())
}
