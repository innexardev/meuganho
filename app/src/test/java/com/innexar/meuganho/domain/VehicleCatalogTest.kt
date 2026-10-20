package com.innexar.meuganho.domain

import com.innexar.meuganho.data.local.entity.VehicleEntity
import com.innexar.meuganho.data.preferences.AppSettings
import com.innexar.meuganho.data.repository.CostModelProvider
import com.innexar.meuganho.domain.model.FuelType
import com.innexar.meuganho.domain.vehicle.VehicleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCatalogTest {
    @Test fun `catálogo coerente e com as chinesas`() {
        assertTrue(VehicleCatalog.manufacturers.containsAll(listOf("BYD", "GWM", "Caoa Chery", "Chevrolet", "Fiat")))
        VehicleCatalog.cars.forEach { car ->
            assertTrue(car.label, car.city > 0 && car.highway > 0)
            if (car.fuel == FuelType.FLEX) assertTrue(car.label, car.ethanolCity != null && car.ethanolCity!! < car.city)
        }
    }

    @Test fun `carro elétrico usa km por kWh e preço do kWh`() {
        val ev = VehicleEntity(manufacturer = "BYD", model = "Dolphin Mini", year = 2024, fuelType = FuelType.ELECTRIC,
            odometerKm = 0, cityKmPerLiter = 8.9, highwayKmPerLiter = 7.7, averageKmPerLiter = 8.4,
            maintenanceCentsPerKm = 5.0, depreciationCentsPerKm = 25.0, createdAt = 0)
        val cost = CostModelProvider.build(ev, emptyList(), AppSettings(electricityPriceCentsPerKwh = 95))
        // 84 km com 8,4 km/kWh = 10 kWh * R$ 0,95 = R$ 9,50
        assertEquals(950L, cost.fuelCostCents(84_000))
    }
}
