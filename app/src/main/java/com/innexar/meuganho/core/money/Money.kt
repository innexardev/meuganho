package com.innexar.meuganho.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Dinheiro é sempre representado como Long em centavos.
 *
 * Double nunca é usado para valores monetários porque somas repetidas de
 * frações binárias geram erros de centavos que se acumulam no fechamento.
 * Taxas (R$/km, custo/km) podem ser Double, mas todo resultado vira centavos.
 */
object Money {

    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        return "${sign}R$ ${groupThousands(absolute / 100)},${(absolute % 100).toString().padStart(2, '0')}"
    }

    /** Formato curto para overlay e chips: "R$184". */
    fun formatShort(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val reais = (abs(cents) / 100.0).roundToLong()
        return "${sign}R$${groupThousands(reais)}"
    }

    /**
     * Interpreta valores digitados pelo usuário.
     * Aceita "28,50", "28.50", "1.234,56" e "28". Retorna null se inválido.
     */
    fun parseInput(text: String): Long? {
        var clean = text.replace("R$", "").replace(" ", "").trim()
        if (clean.isEmpty()) return null
        if (clean.contains(',')) {
            clean = clean.replace(".", "").replace(',', '.')
        }
        return try {
            BigDecimal(clean).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /** Converte centavos em texto editável sem símbolo: 2850 -> "28,50". */
    fun toInput(cents: Long): String =
        "${cents / 100},${(abs(cents) % 100).toString().padStart(2, '0')}"

    private fun groupThousands(value: Long): String =
        value.toString().reversed().chunked(3).joinToString(".").reversed()
}
