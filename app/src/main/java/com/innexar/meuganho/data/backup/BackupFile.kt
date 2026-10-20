package com.innexar.meuganho.data.backup

import com.innexar.meuganho.data.local.entity.ExpenseEntity
import com.innexar.meuganho.data.local.entity.FuelLogEntity
import com.innexar.meuganho.data.local.entity.GoalEntity
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.data.local.entity.PlatformTotalEntity
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.local.entity.VehicleEntity
import kotlinx.serialization.Serializable

/**
 * Formato do backup. [formatVersion] sobe sempre que uma entidade mudar de forma
 * incompatível; [BackupManager] recusa arquivos de versão mais nova que a do app
 * e, quando necessário, converte versões antigas antes de importar.
 */
@Serializable
data class BackupFile(
    val formatVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val vehicles: List<VehicleEntity>,
    val shifts: List<ShiftEntity>,
    val offers: List<OfferEntity>,
    val trips: List<TripEntity>,
    val fuelLogs: List<FuelLogEntity>,
    val expenses: List<ExpenseEntity>,
    val goals: List<GoalEntity>,
    val platformTotals: List<PlatformTotalEntity>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
