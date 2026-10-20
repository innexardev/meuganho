package com.innexar.meuganho.domain.model

/**
 * Oferta normalizada. Uber e 99 convertem suas telas neste modelo e o motor
 * financeiro só conhece ele, nunca a plataforma. Nova plataforma = novo parser.
 */
data class RideOffer(
    val platform: Platform,
    val detectedAt: Long,
    val offeredCents: Captured<Long>,
    val pickupMeters: Captured<Int>,
    val pickupSeconds: Captured<Int>,
    val tripMeters: Captured<Int>,
    val tripSeconds: Captured<Int>,
    val surgeCents: Long? = null,
    val category: String? = null,
    val parserVersion: String,
    val sourceAppVersion: Long,
    val channel: CaptureChannel = CaptureChannel.ACCESSIBILITY,
    /** Alertas do parser que não pertencem a um campo (ex.: promoção na mesma tela). */
    val warnings: List<String> = emptyList(),
    /** Os números bateram com o R$/km exibido pelo próprio app (prova independente). */
    val confirmedByPlatform: Boolean = false,
) {
    val totalMeters: Int?
        get() = tripMeters.value?.let { it + (pickupMeters.value ?: 0) }

    val totalSeconds: Int?
        get() = tripSeconds.value?.let { it + (pickupSeconds.value ?: 0) }

    /** Campos críticos: sem eles a análise R$/km e R$/h não é confiável. */
    val criticalFields: Map<String, Captured<*>>
        get() = mapOf(
            "valor" to offeredCents,
            "km até passageiro" to pickupMeters,
            "tempo até passageiro" to pickupSeconds,
            "km da viagem" to tripMeters,
            "tempo da viagem" to tripSeconds,
        )

    /** Confiança da oferta = mínimo dos campos críticos, nunca a média. */
    val criticalConfidence: Confidence
        get() = Confidence.min(criticalFields.values.map { it.confidence })

    fun fingerprint(): String = listOf(
        platform.name,
        offeredCents.value,
        pickupMeters.value?.div(100),
        tripMeters.value?.div(100),
        tripSeconds.value?.div(60),
    ).joinToString("|")
}
