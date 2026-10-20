package com.innexar.meuganho.domain.stats

import com.innexar.meuganho.domain.model.Platform
import kotlin.math.abs

data class PlatformReconciliation(
    val platform: Platform,
    val capturedCents: Long,
    val platformCents: Long?,
) {
    val differenceCents: Long? get() = platformCents?.let { it - capturedCents }

    /** Sem leitura do total da plataforma não há como reconciliar: null, não "ok". */
    val reconciled: Boolean? get() = differenceCents?.let { abs(it) <= TOLERANCE_CENTS }

    companion object {
        // Tolerância pequena: arredondamentos da própria plataforma.
        const val TOLERANCE_CENTS = 100L
    }
}

/**
 * Checksum do dia: a soma das corridas capturadas deve bater com o
 * "Ganhos de hoje" exibido pelo próprio app. Divergência revela corrida
 * perdida, gorjeta, dinâmica somada depois ou valor lido errado.
 */
object Reconciliation {
    fun of(byPlatform: Map<Platform, PlatformSlice>, platformTotals: Map<Platform, Long>): List<PlatformReconciliation> =
        Platform.entries.mapNotNull { platform ->
            val captured = byPlatform[platform]?.grossCents ?: 0
            val official = platformTotals[platform]
            if (captured == 0L && official == null) null else PlatformReconciliation(platform, captured, official)
        }
}
