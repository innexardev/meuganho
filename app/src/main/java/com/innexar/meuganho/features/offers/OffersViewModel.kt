package com.innexar.meuganho.features.offers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.domain.financial.OfferAnalysis
import com.innexar.meuganho.domain.financial.OfferAnalyzer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class OfferRow(val offer: OfferEntity, val analysis: OfferAnalysis?)

class OffersViewModel(container: AppContainer) : ViewModel() {
    val rows: StateFlow<List<OfferRow>> = combine(
        container.database.offerDao().observeRecent(150),
        container.costModelProvider.costModel,
        container.settingsRepository.settings,
    ) { offers, cost, settings ->
        offers.map { o ->
            val analysis = o.effectiveCents?.let { value ->
                OfferAnalyzer.analyze(
                    valueCents = value,
                    totalMeters = o.totalMeters?.toLong(),
                    totalSeconds = o.totalSeconds?.toLong(),
                    reliable = o.status.countsInStats,
                    costModel = cost,
                    thresholds = settings.thresholds,
                )
            }
            OfferRow(o, analysis)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
