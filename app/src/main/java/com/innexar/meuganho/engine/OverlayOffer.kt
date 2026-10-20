package com.innexar.meuganho.engine

import com.innexar.meuganho.domain.financial.OfferAnalysis
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform

/** Oferta analisada pronta para o overlay. */
data class OverlayOffer(
    val platform: Platform,
    val analysis: OfferAnalysis,
    val status: DataStatus,
    /** Circuit breaker aberto: a interface do app mudou, análise indisponível. */
    val parserTripped: Boolean,
    val shownAt: Long,
)
