package com.innexar.meuganho.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.innexar.meuganho.data.local.dao.DiagnosticSampleDao
import com.innexar.meuganho.data.local.dao.ExpenseDao
import com.innexar.meuganho.data.local.dao.FuelLogDao
import com.innexar.meuganho.data.local.dao.GoalDao
import com.innexar.meuganho.data.local.dao.OfferDao
import com.innexar.meuganho.data.local.dao.PlatformTotalDao
import com.innexar.meuganho.data.local.dao.ShiftDao
import com.innexar.meuganho.data.local.dao.SyncOutboxDao
import com.innexar.meuganho.data.local.dao.TripDao
import com.innexar.meuganho.data.local.dao.VehicleDao
import com.innexar.meuganho.data.local.entity.DiagnosticSampleEntity
import com.innexar.meuganho.data.local.entity.ExpenseEntity
import com.innexar.meuganho.data.local.entity.FuelLogEntity
import com.innexar.meuganho.data.local.entity.GoalEntity
import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.data.local.entity.PlatformTotalEntity
import com.innexar.meuganho.data.local.entity.ShiftEntity
import com.innexar.meuganho.data.local.entity.SyncOutboxEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.data.local.entity.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class, ShiftEntity::class, OfferEntity::class, TripEntity::class,
        FuelLogEntity::class, ExpenseEntity::class, GoalEntity::class, PlatformTotalEntity::class,
        DiagnosticSampleEntity::class, SyncOutboxEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MeuGanhoDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun shiftDao(): ShiftDao
    abstract fun offerDao(): OfferDao
    abstract fun tripDao(): TripDao
    abstract fun fuelLogDao(): FuelLogDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun goalDao(): GoalDao
    abstract fun platformTotalDao(): PlatformTotalDao
    abstract fun diagnosticSampleDao(): DiagnosticSampleDao
    abstract fun syncOutboxDao(): SyncOutboxDao

    companion object {
        fun build(context: Context): MeuGanhoDatabase =
            Room.databaseBuilder(context, MeuGanhoDatabase::class.java, "meuganho.db")
                // Nunca usar fallbackToDestructiveMigration: apagaria o histórico financeiro.
                // Toda mudança de schema exige uma Migration explícita em Migrations.kt.
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
