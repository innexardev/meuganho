package com.innexar.meuganho.features.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innexar.meuganho.app.AppContainer
import com.innexar.meuganho.data.local.entity.VehicleEntity
import com.innexar.meuganho.domain.model.FuelType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Formulário em texto: conversão e validação acontecem só ao salvar. */
data class VehicleForm(
    val manufacturer: String = "",
    val model: String = "",
    val version: String = "",
    val year: String = "",
    val fuelType: FuelType = FuelType.FLEX,
    val odometer: String = "",
    val city: String = "",
    val highway: String = "",
    val average: String = "",
    val ethanol: String = "",
    val maintenance: String = "",
    val depreciation: String = "",
    /** Consumos vieram do catálogo (estimativa Inmetro), não do motorista. */
    val fromCatalog: Boolean = false,
)

class VehicleViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.vehicleDao()
    private var existing: VehicleEntity? = null

    private val _form = MutableStateFlow(VehicleForm())
    val form: StateFlow<VehicleForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            dao.active()?.let { v ->
                existing = v
                _form.value = VehicleForm(
                    v.manufacturer, v.model, v.version, v.year.toString(), v.fuelType, v.odometerKm.toString(),
                    v.cityKmPerLiter.fmt(), v.highwayKmPerLiter.fmt(), v.averageKmPerLiter.fmt(), v.ethanolKmPerLiter?.fmt().orEmpty(),
                    v.maintenanceCentsPerKm.div(100).fmt(), v.depreciationCentsPerKm.div(100).fmt(),
                )
            }
        }
    }

    fun update(transform: (VehicleForm) -> VehicleForm) {
        _form.value = transform(_form.value)
    }

    /** Preenche com a versão de catálogo; ano e odômetro digitados são preservados. */
    fun applyCatalog(car: com.innexar.meuganho.domain.vehicle.CatalogCar) {
        val cat = com.innexar.meuganho.domain.vehicle.VehicleCatalog
        _form.value = _form.value.copy(
            manufacturer = car.manufacturer,
            model = car.model,
            version = car.version,
            fuelType = car.fuel,
            city = car.city.fmt(),
            highway = car.highway.fmt(),
            average = "%.1f".format(cat.mixed(car.city, car.highway)).replace('.', ','),
            ethanol = if (car.ethanolCity != null && car.ethanolHighway != null) {
                "%.1f".format(cat.mixed(car.ethanolCity, car.ethanolHighway)).replace('.', ',')
            } else {
                ""
            },
            maintenance = (car.carClass.maintenanceCentsPerKm / 100).fmt(),
            depreciation = (car.carClass.depreciationCentsPerKm / 100).fmt(),
            fromCatalog = true,
        )
    }

    fun save(onSaved: () -> Unit, onError: (String) -> Unit) {
        val f = _form.value
        val city = f.city.num()
        val highway = f.highway.num()
        // Consumo médio: informado, ou 60% cidade / 40% estrada (perfil típico de app).
        val average = f.average.num() ?: if (city != null && highway != null) city * 0.6 + highway * 0.4 else city
        if (f.model.isBlank()) return onError("Informe o modelo")
        if (average == null || average <= 0) return onError("Informe o consumo (km/l)")
        viewModelScope.launch {
            dao.upsert(
                VehicleEntity(
                    id = existing?.id ?: 0,
                    manufacturer = f.manufacturer.trim(),
                    model = f.model.trim(),
                    version = f.version.trim(),
                    year = f.year.toIntOrNull() ?: 0,
                    fuelType = f.fuelType,
                    odometerKm = f.odometer.toIntOrNull() ?: 0,
                    cityKmPerLiter = city ?: average,
                    highwayKmPerLiter = highway ?: average,
                    averageKmPerLiter = average,
                    ethanolKmPerLiter = f.ethanol.num(),
                    // Campos em R$/km na tela, centavos/km no banco.
                    maintenanceCentsPerKm = (f.maintenance.num() ?: 0.0) * 100,
                    depreciationCentsPerKm = (f.depreciation.num() ?: 0.0) * 100,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                )
            )
            onSaved()
        }
    }

    private fun String.num(): Double? = replace(',', '.').toDoubleOrNull()
    private fun Double.fmt(): String = toString().removeSuffix(".0").replace('.', ',')
}
