package com.innexar.meuganho.capture.core

import com.innexar.meuganho.capture.ninetynine.NinetyNinePatterns
import com.innexar.meuganho.capture.ninetynine.NinetyNineParserV1
import com.innexar.meuganho.capture.uber.UberParserV1
import com.innexar.meuganho.capture.uber.UberPatterns
import com.innexar.meuganho.domain.model.Platform

/** Qual versão de parser está ativa para cada plataforma. Trocar versão = mudar uma linha. */
object ParserRegistry {
    private val parsers: Map<Platform, PlatformParser> = mapOf(
        Platform.UBER to UberParserV1(),
        Platform.NINETY_NINE to NinetyNineParserV1(),
    )

    private val patterns: Map<Platform, PlatformPatterns> = mapOf(
        Platform.UBER to UberPatterns.v1,
        Platform.NINETY_NINE to NinetyNinePatterns.v1,
    )

    fun forPlatform(platform: Platform): PlatformParser = parsers.getValue(platform)

    fun sanitizerKeywords(platform: Platform): List<String> = SnapshotSanitizer.keywordsOf(patterns.getValue(platform))
}
