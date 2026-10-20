package com.innexar.meuganho.domain.validation

import com.innexar.meuganho.domain.model.DataStatus

/**
 * Decide o status de uma corrida concluída a partir das fontes de valor.
 *
 * A estimativa da oferta é usada como valor provisório quando a oferta era
 * confiável; a reconciliação diária com o total da plataforma pega o resto.
 * Diferença grande entre oferta e valor final indica leitura errada de um dos
 * dois lados, então vai para revisão.
 */
object TripValuePolicy {

    private const val MIN_FINAL_RATIO = 0.5
    private const val MAX_FINAL_RATIO = 2.5

    fun resolve(offerStatus: DataStatus?, estimatedCents: Long?, finalCents: Long?): StatusDecision {
        if (finalCents != null) {
            if (finalCents <= 0) return StatusDecision(DataStatus.NEEDS_REVIEW, listOf("Valor final zerado"))
            if (estimatedCents != null && estimatedCents > 0) {
                val ratio = finalCents.toDouble() / estimatedCents
                if (ratio < MIN_FINAL_RATIO || ratio > MAX_FINAL_RATIO) {
                    return StatusDecision(DataStatus.NEEDS_REVIEW, listOf("Valor final muito diferente da oferta"))
                }
            }
            return StatusDecision(DataStatus.AUTO_ACCEPTED, emptyList())
        }
        if (estimatedCents != null) {
            return if (offerStatus?.countsInStats == true) {
                StatusDecision(DataStatus.AUTO_ACCEPTED, listOf("Valor da oferta (tela final não lida)"))
            } else {
                StatusDecision(DataStatus.NEEDS_REVIEW, listOf("Oferta duvidosa e sem tela final"))
            }
        }
        return StatusDecision(DataStatus.NEEDS_REVIEW, listOf("Corrida sem valor capturado"))
    }
}
