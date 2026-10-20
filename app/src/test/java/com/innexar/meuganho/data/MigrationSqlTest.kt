package com.innexar.meuganho.data

import com.innexar.meuganho.data.local.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garante que o SQL escrito à mão na Migration 1->2 cria exatamente a tabela que o
 * Room espera no schema v2 exportado. Se divergir, o app quebraria ao atualizar.
 */
class MigrationSqlTest {
    private fun schema(version: Int): String =
        File("schemas/com.innexar.meuganho.data.local.MeuGanhoDatabase/$version.json").readText()

    private fun normalize(sql: String) = sql.replace(Regex("\\s+"), " ").replace("( ", "(").trim()

    @Test fun `migration 1 para 2 cria a tabela igual ao schema exportado`() {
        val v2 = schema(2)
        val expected = Regex(""""createSql":\s*"(CREATE TABLE IF NOT EXISTS `\$\{TABLE_NAME}` \([^"]*diagnostic[^"]*|CREATE TABLE IF NOT EXISTS `\$\{TABLE_NAME}` \(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `capturedAt`[^"]*)"""")
            .find(v2)!!.groupValues[1].replace("\${TABLE_NAME}", "diagnostic_samples")
        assertEquals(normalize(expected), normalize(Migrations.SQL_1_2[0]))
        // Índices também presentes no schema.
        assertTrue(v2.contains("index_diagnostic_samples_uploaded"))
        assertTrue(v2.contains("index_diagnostic_samples_capturedAt"))
    }

    @Test fun `migration 2 para 3 cria a fila igual ao schema e adiciona as colunas de região`() {
        val v3 = schema(3)
        assertTrue(v3.contains("CREATE TABLE IF NOT EXISTS `${'$'}{TABLE_NAME}` (`kind` TEXT NOT NULL, `localId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`kind`, `localId`))"))
        assertEquals(normalize("CREATE TABLE IF NOT EXISTS `sync_outbox` (`kind` TEXT NOT NULL, `localId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`kind`, `localId`))"),
            normalize(Migrations.SQL_2_3[2]))
        assertTrue(v3.contains("\"columnName\": \"cell\""))
        assertTrue(v3.contains("\"columnName\": \"endCell\""))
    }

    @Test fun `migration não altera tabelas da versão 1`() {
        Migrations.SQL_1_2.forEach { assertTrue(it.contains("diagnostic_samples")) }
    }
}
