package com.innexar.meuganho.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.innexar.meuganho.capture.core.ScreenType
import com.innexar.meuganho.domain.model.Platform

/**
 * Amostra de tela para calibração dos parsers: texto JÁ SANITIZADO + o que o
 * parser entendeu dele. Fila local até o envio; não entra no backup.
 */
@Entity(tableName = "diagnostic_samples", indices = [Index("uploaded"), Index("capturedAt")])
data class DiagnosticSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val platform: Platform,
    val screenType: ScreenType,
    /** Motivo da amostragem: SCREEN, OFFER, OFFER_UNPARSED, UNKNOWN. */
    val reason: String,
    val parserVersion: String,
    val sourceAppVersion: Long,
    val sanitizedText: String,
    /** Resumo do que o parser extraiu (JSON curto), para comparar com o texto. */
    val parseSummary: String,
    val uploaded: Boolean = false,
)
