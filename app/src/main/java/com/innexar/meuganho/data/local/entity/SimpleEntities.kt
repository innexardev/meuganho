package com.innexar.meuganho.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.innexar.meuganho.domain.model.ExpenseCategory
import com.innexar.meuganho.domain.model.FuelType
import com.innexar.meuganho.domain.model.GoalKind
import com.innexar.meuganho.domain.model.GoalPeriod
import com.innexar.meuganho.domain.model.Platform
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "fuel_logs", indices = [Index("at")])
data class FuelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long?,
    val at: Long,
    val odometerKm: Int,
    val fuelType: FuelType,
    val priceCentsPerLiter: Long,
    val liters: Double,
    val totalCents: Long,
    val fullTank: Boolean,
    val note: String? = null,
)

@Serializable
@Entity(tableName = "expenses", indices = [Index("at")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val shiftId: Long?,
    val category: ExpenseCategory,
    val amountCents: Long,
    val note: String? = null,
)

@Serializable
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val period: GoalPeriod,
    val kind: GoalKind,
    val targetCents: Long,
    val isActive: Boolean = true,
)

/** Leitura do "Ganhos de hoje" exibido pela plataforma: o checksum do dia. */
@Serializable
@Entity(tableName = "platform_totals", indices = [Index(value = ["platform", "epochDay"])])
data class PlatformTotalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: Platform,
    val epochDay: Long,
    val totalCents: Long,
    val capturedAt: Long,
    val parserVersion: String,
)
