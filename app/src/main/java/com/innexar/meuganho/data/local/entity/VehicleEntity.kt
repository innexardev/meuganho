package com.innexar.meuganho.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.innexar.meuganho.domain.model.FuelType
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val manufacturer: String,
    val model: String,
    val version: String = "",
    val year: Int,
    val fuelType: FuelType,
    val odometerKm: Int,
    val cityKmPerLiter: Double,
    val highwayKmPerLiter: Double,
    val averageKmPerLiter: Double,
    /** Só para FLEX: consumo médio com etanol (em geral ~70% da gasolina). */
    val ethanolKmPerLiter: Double? = null,
    /** Manutenção, pneus e óleo diluídos por km. */
    val maintenanceCentsPerKm: Double = 0.0,
    val depreciationCentsPerKm: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: Long,
)
