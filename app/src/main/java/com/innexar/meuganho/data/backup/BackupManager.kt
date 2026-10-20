package com.innexar.meuganho.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.innexar.meuganho.BuildConfig
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import com.innexar.meuganho.data.local.MeuGanhoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Backup local (offline-first). O arquivo vai para onde o usuário escolher pelo
 * seletor do Android (Drive, pasta local...), sem backend próprio.
 */
class BackupManager(
    private val db: MeuGanhoDatabase,
    private val resolver: ContentResolver,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun exportJson(uri: Uri, now: Long): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val file = BackupFile(
                formatVersion = BackupFile.CURRENT_VERSION,
                exportedAt = now,
                appVersion = BuildConfig.VERSION_NAME,
                vehicles = db.vehicleDao().all(),
                shifts = db.shiftDao().all(),
                // Snapshots brutos ficam fora do backup: são temporários e de diagnóstico.
                offers = db.offerDao().all().map { it.copy(rawSnapshot = null) },
                trips = db.tripDao().all(),
                fuelLogs = db.fuelLogDao().all(),
                expenses = db.expenseDao().all(),
                goals = db.goalDao().all(),
                platformTotals = db.platformTotalDao().all(),
            )
            resolver.openOutputStream(uri, "wt")!!.use { it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray()) }
            file.trips.size
        }.onFailure { AppLog.e(LogCategory.DATABASE, "Falha ao exportar backup", it) }
    }

    /** Substitui TODOS os dados pelos do arquivo, numa única transação (tudo ou nada). */
    suspend fun importJson(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val text = resolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val file = json.decodeFromString(BackupFile.serializer(), text)
            require(file.formatVersion <= BackupFile.CURRENT_VERSION) {
                "Backup de versão ${file.formatVersion} é mais novo que este app"
            }
            db.withTransaction {
                val sql = db.openHelper.writableDatabase
                TABLES.forEach { sql.execSQL("DELETE FROM $it") }
                db.vehicleDao().insertAll(file.vehicles)
                db.shiftDao().insertAll(file.shifts)
                db.offerDao().insertAll(file.offers)
                db.tripDao().insertAll(file.trips)
                db.fuelLogDao().insertAll(file.fuelLogs)
                db.expenseDao().insertAll(file.expenses)
                db.goalDao().insertAll(file.goals)
                db.platformTotalDao().insertAll(file.platformTotals)
            }
            file.trips.size
        }.onFailure { AppLog.e(LogCategory.DATABASE, "Falha ao importar backup", it) }
    }

    suspend fun exportTripsCsv(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val trips = db.tripDao().all().sortedBy { it.acceptedAt }
            resolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { w ->
                w.write(TripCsv.HEADER)
                w.newLine()
                trips.forEach { w.write(TripCsv.row(it)); w.newLine() }
            }
            trips.size
        }.onFailure { AppLog.e(LogCategory.DATABASE, "Falha ao exportar CSV", it) }
    }

    private companion object {
        val TABLES = listOf("trips", "offers", "shifts", "fuel_logs", "expenses", "goals", "platform_totals", "vehicles")
    }
}
