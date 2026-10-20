package com.innexar.meuganho.data.preferences

import com.innexar.meuganho.domain.financial.RatingThresholds
import com.innexar.meuganho.domain.model.FuelType

/** Todas as preferências do app. Valores padrão são pontos de partida, não regras. */
/** Como a análise da oferta aparece na tela. */
enum class OfferDisplay(val label: String) { NOTIFICATION("Notificação"), PANEL("Painel flutuante"), BOTH("Os dois") }

data class AppSettings(
    val thresholds: RatingThresholds = RatingThresholds(),
    val fuelPriceCentsPerLiter: Long = 619,
    /** Preço do kWh para carro elétrico (recarga em casa ~R$ 0,95; pública ~R$ 2,00). */
    val electricityPriceCentsPerKwh: Long = 95,
    /** Para FLEX: qual combustível está no tanque agora (muda o consumo). */
    val currentFuel: FuelType = FuelType.GASOLINE,
    /** Usa o consumo medido nos abastecimentos quando houver histórico suficiente. */
    val useObservedConsumption: Boolean = true,
    /** Outros custos por km (seguro, documentação...) em centavos. */
    val otherCentsPerKm: Double = 0.0,
    val gpsEnabled: Boolean = true,
    val overlayEnabled: Boolean = true,
    /** Padrão: notificação no topo (não cobre o botão Aceitar, não precisa arrastar). */
    val offerDisplay: OfferDisplay = OfferDisplay.NOTIFICATION,
    val overlayX: Int = 24,
    val overlayY: Int = 320,
    val overlayExpanded: Boolean = false,
    val onboardingDone: Boolean = false,
    /** Quantos dias manter snapshots sanitizados de ofertas em revisão. */
    val snapshotRetentionDays: Int = 7,
    /** Envia ofertas, corridas e turnos (com região aproximada) ao banco de estatísticas. */
    val shareStats: Boolean = true,
    /** Envia amostras de tela SANITIZADAS ao servidor para calibrar os parsers. */
    val sendSamples: Boolean = true,
    /** Id aleatório da instalação (agrupa amostras). Não identifica a pessoa. Vazio até ser gerado. */
    val installId: String = "",
    val lastUpdateCheckAt: Long = 0,
    /** Recuperação única (0.3.1) das corridas marcadas como canceladas por engano. */
    val repairedCancelledTrips: Boolean = false,
)
