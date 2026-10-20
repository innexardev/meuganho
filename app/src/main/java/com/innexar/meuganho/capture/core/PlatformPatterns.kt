package com.innexar.meuganho.capture.core

/**
 * Todas as palavras-chave de uma plataforma em um só lugar.
 * Quando o app mudar, a correção costuma ser só neste objeto + uma fixture nova.
 * Palavras sempre em minúsculas e SEM acento (comparadas com texto normalizado).
 */
data class PlatformPatterns(
    val offer: OfferPatterns,
    val screens: ScreenPatterns,
)

data class OfferPatterns(
    val acceptKeywords: List<String>,
    val pickupAnchors: List<String>,
    val tripAnchors: List<String>,
    /** Linhas com esses termos têm valores que NÃO são o preço (promoções, metas). */
    val promoKeywords: List<String>,
    val categories: List<String>,
    /** Fragmentos de viewId que indicam o campo de preço. */
    val priceViewIds: List<String>,
    /** Fragmentos de viewId de busca/viagem: sinal estrutural, mais forte que o texto. */
    val pickupViewIds: List<String> = emptyList(),
    val tripViewIds: List<String> = emptyList(),
)

data class ScreenPatterns(
    val toPickup: List<String>,
    val waiting: List<String>,
    val inTrip: List<String>,
    val completed: List<String>,
    val cancelled: List<String>,
    val dailyEarnings: List<String>,
    val idle: List<String>,
    /** Tela de histórico de ganhos (fonte oficial das corridas feitas). */
    val earningsHistory: List<String> = emptyList(),
)
