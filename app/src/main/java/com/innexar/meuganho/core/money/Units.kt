package com.innexar.meuganho.core.money

import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

/** Formatação de distância, tempo e taxas no padrão brasileiro. */
object Units {

    fun km(meters: Long): String = String.format(PT_BR, "%.1f km", meters / 1000.0)

    fun km(meters: Int): String = km(meters.toLong())

    /** "28 min" para durações curtas, "1h05" acima de uma hora. */
    fun duration(seconds: Long): String {
        val totalMinutes = (seconds + 30) / 60
        if (totalMinutes < 60) return "$totalMinutes min"
        return "${totalMinutes / 60}h${(totalMinutes % 60).toString().padStart(2, '0')}"
    }

    fun duration(seconds: Int): String = duration(seconds.toLong())

    fun perKm(centsPerKm: Long): String = "${Money.format(centsPerKm)}/km"

    fun perHour(centsPerHour: Long): String = "${Money.format(centsPerHour)}/h"

    /** Para blocos cujo rótulo já diz "R$/KM": só o valor, sem repetir a unidade ("R$ 1,36"). */
    fun perKmValue(centsPerKm: Long): String = Money.format(centsPerKm)

    /** Para blocos cujo rótulo já diz "R$/HORA": reais inteiros ("R$ 84"), leitura rápida no carro. */
    fun perHourValue(centsPerHour: Long): String = Money.formatShort(centsPerHour).replace("R$", "R$ ")

    /** "1 corrida", "2 corridas". */
    fun count(n: Int, singular: String, plural: String = singular + "s"): String = "$n ${if (n == 1) singular else plural}"

    fun decimal(value: Double, digits: Int = 1): String = String.format(PT_BR, "%.${digits}f", value)

    fun percent(ratio: Double): String = "${(ratio * 100).toInt()}%"
}
