package com.innexar.meuganho.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations explícitas. O schema de cada versão é exportado em app/schemas/
 * (versionado no git) e cada Migration tem teste em MigrationSqlTest.
 *
 * Regra: nunca fallbackToDestructiveMigration. Lembrar de subir
 * BackupFile.CURRENT_VERSION se uma tabela do backup mudar.
 */
object Migrations {

    /** v1 -> v2 (app 0.1.1): fila de amostras de tela para calibração. Não mexe em dados existentes. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_1_2.forEach(db::execSQL)
        }
    }

    // SQL separado para ser verificado nos testes contra o schema exportado.
    val SQL_1_2 = listOf(
        """CREATE TABLE IF NOT EXISTS `diagnostic_samples` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `capturedAt` INTEGER NOT NULL,
            `platform` TEXT NOT NULL,
            `screenType` TEXT NOT NULL,
            `reason` TEXT NOT NULL,
            `parserVersion` TEXT NOT NULL,
            `sourceAppVersion` INTEGER NOT NULL,
            `sanitizedText` TEXT NOT NULL,
            `parseSummary` TEXT NOT NULL,
            `uploaded` INTEGER NOT NULL)""",
        "CREATE INDEX IF NOT EXISTS `index_diagnostic_samples_uploaded` ON `diagnostic_samples` (`uploaded`)",
        "CREATE INDEX IF NOT EXISTS `index_diagnostic_samples_capturedAt` ON `diagnostic_samples` (`capturedAt`)",
    )

    /** v2 -> v3 (app 0.2.0): região aproximada em ofertas/corridas + fila do banco de estatísticas. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_2_3.forEach(db::execSQL)
        }
    }

    val SQL_2_3 = listOf(
        "ALTER TABLE `offers` ADD COLUMN `cell` TEXT",
        "ALTER TABLE `trips` ADD COLUMN `endCell` TEXT",
        """CREATE TABLE IF NOT EXISTS `sync_outbox` (
            `kind` TEXT NOT NULL,
            `localId` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            PRIMARY KEY(`kind`, `localId`))""",
        // O histórico que já existe no aparelho também vai para o banco de estatísticas.
        "INSERT OR IGNORE INTO `sync_outbox` (`kind`, `localId`, `createdAt`) SELECT 'OFFER', `id`, 0 FROM `offers`",
        "INSERT OR IGNORE INTO `sync_outbox` (`kind`, `localId`, `createdAt`) SELECT 'TRIP', `id`, 0 FROM `trips`",
        "INSERT OR IGNORE INTO `sync_outbox` (`kind`, `localId`, `createdAt`) SELECT 'SHIFT', `id`, 0 FROM `shifts` WHERE `endedAt` IS NOT NULL",
    )

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
