package com.innexar.meuganho.features.fuel

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.core.designsystem.components.EmptyState
import com.innexar.meuganho.core.designsystem.components.MetricTile
import com.innexar.meuganho.core.designsystem.components.MgCard
import com.innexar.meuganho.core.designsystem.components.MgScreen
import com.innexar.meuganho.core.designsystem.components.SectionTitle
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.money.Money
import com.innexar.meuganho.core.money.Units
import com.innexar.meuganho.core.time.DateFormats
import com.innexar.meuganho.core.ui.containerViewModel
import com.innexar.meuganho.domain.financial.ConsumptionCalculator

@Composable
fun FuelScreen(onBack: () -> Unit, onNew: () -> Unit) {
    val vm = containerViewModel { FuelViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    MgScreen("Abastecimentos", onBack, actions = {
        IconButton(onClick = onNew) { Icon(Icons.Filled.Add, contentDescription = "Novo abastecimento") }
    }) {
        MgCard(Modifier.fillMaxWidth()) {
            Row {
                MetricTile("Consumo real", state.observedKmPerLiter?.let { "${Units.decimal(it)} km/l" } ?: "—",
                    Modifier.weight(1f), sub = "${state.intervals.size} medições")
                MetricTile("Cadastrado", state.nominalKmPerLiter?.let { "${Units.decimal(it)} km/l" } ?: "—", Modifier.weight(1f))
            }
            if (state.observedKmPerLiter == null) {
                Text("O consumo real é medido entre dois abastecimentos de TANQUE CHEIO. " +
                    "Com ${ConsumptionCalculator.MIN_INTERVALS_FOR_TRUST} medições ele passa a ser usado nos cálculos.",
                    style = Mg.type.caption, color = Mg.colors.textSecondary)
            }
        }
        SectionTitle("Registros")
        if (state.logs.isEmpty()) EmptyState("Nenhum abastecimento", "Registre para medir o consumo real do carro.", action = "Registrar", onAction = onNew)
        state.logs.forEach { log ->
            MgCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("${DateFormats.day(log.at)} · ${log.odometerKm} km", style = Mg.type.body, color = Mg.colors.textPrimary)
                        Text("${log.fuelType.label} · ${Units.decimal(log.liters, 2)} L · ${Money.format(log.priceCentsPerLiter)}/L" +
                            if (log.fullTank) " · cheio" else " · parcial", style = Mg.type.caption, color = Mg.colors.textSecondary)
                    }
                    Text(Money.format(log.totalCents), style = Mg.type.metricSmall, color = Mg.colors.textPrimary)
                    IconButton(onClick = { vm.delete(log.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = Mg.colors.textSecondary) }
                }
            }
        }
    }
}
