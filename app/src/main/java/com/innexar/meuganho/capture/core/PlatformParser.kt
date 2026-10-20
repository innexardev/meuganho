package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.Captured
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.RideOffer

/**
 * Contrato de um parser de plataforma. Versionado: ao reescrever para uma nova
 * interface do app, cria-se UberParserV2 mantendo a V1 e suas fixtures.
 */
interface PlatformParser {
    val platform: Platform
    val version: String

    fun classify(snapshot: ScreenSnapshot): ScreenType

    /** Todas as ofertas da tela (o Radar de Viagens mostra vários cartões ao mesmo tempo). */
    fun parseOffers(snapshot: ScreenSnapshot): List<RideOffer>

    /** Primeira oferta da tela (atalho para telas de oferta única e testes). */
    fun parseOffer(snapshot: ScreenSnapshot): RideOffer? = parseOffers(snapshot).firstOrNull()
    fun parseCompletedValue(snapshot: ScreenSnapshot): Captured<Long>?
    fun parseDailyTotal(snapshot: ScreenSnapshot): Captured<Long>?

    /** Corridas do histórico oficial da plataforma. [carriedDate]: dia visto na leitura anterior (lista rolada). */
    fun parseEarningsHistory(snapshot: ScreenSnapshot, carriedDate: java.time.LocalDate?): List<com.innexar.meuganho.domain.model.PlatformTripRecord> =
        emptyList()

    fun lastHistoryDate(snapshot: ScreenSnapshot): java.time.LocalDate? = null

    /** Total do dia que a plataforma mostra em telas comuns (mapa), para conferir o dia. */
    fun dayTracker(snapshot: ScreenSnapshot): Long? = null
}
