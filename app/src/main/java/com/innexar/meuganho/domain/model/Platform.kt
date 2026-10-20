package com.innexar.meuganho.domain.model

/**
 * Plataformas suportadas. O pacote Android é a única ligação com o app externo;
 * todo o resto do sistema só enxerga este enum.
 */
enum class Platform(
    val label: String,
    val packageNames: Set<String>,
    /** A plataforma esconde o texto da oferta da Acessibilidade: ler pela imagem da tela (OCR). */
    val needsOcr: Boolean = false,
) {
    UBER("Uber", setOf("com.ubercab.driver")),
    NINETY_NINE("99", setOf("com.app99.driver"), needsOcr = true);

    companion object {
        fun fromPackage(packageName: String): Platform? =
            entries.firstOrNull { packageName in it.packageNames }

        val allPackages: Set<String> = entries.flatMap { it.packageNames }.toSet()
    }
}
