package com.innexar.meuganho.capture.core

import java.text.Normalizer

data class MoneyMatch(val cents: Long, val isAddition: Boolean, val start: Int, val end: Int)

/**
 * Extração de números de texto de tela no formato brasileiro (e inglês como fallback).
 * Toda regex de dinheiro, distância e tempo do projeto vive aqui.
 */
object TextPatterns {

    private val diacritics = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")
    private val money = Regex("""(\+\s*)?R\$\s*(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{2}))?""")
    private val distance = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m)(?![a-z])""")
    // "1 h 5 min", "1h05" e o formato real do Uber "1 h e 25 min".
    private val hourMinutes = Regex("""(\d+)\s*h(?:oras?|r)?\s*(?:e\s*)?(\d{1,2})\s*(?:min)?(?![a-z0-9])""")
    private val minutes = Regex("""(\d+)\s*min""")
    private val hoursOnly = Regex("""(\d+)\s*h(?:oras?|r)?(?![a-z0-9])""")
    // "R$1,58/km aprox." e também "R$1/km aprox." (visto em amostra real, sem centavos).
    private val platformRate = Regex("""R\$\s*(\d+)(?:,(\d{2}))?\s*/\s*km""")
    private val range = Regex("""R\$\s*[\d.,]+\s*(?:-|–|a|até)\s*R\$""")

    /** Minúsculas, sem acentos, espaços normalizados (inclui NBSP usado em valores). */
    // Marcas invisíveis de direção do texto (o Uber as põe no histórico: "X⁩ · ⁨18 min").
    private val bidi = Regex("[\u200E\u200F\u2066-\u2069]")

    // OCR lê "R$" como "RS"/"R5"/"RŞ" (amostras reais da 99: "RS1,93/km", "RS134,58 >").
    // Só com centavos: "RS2395" (vírgula perdida) NÃO vira R$ 2.395 — fica ilegível e a próxima leitura resolve.
    private val ocrCurrency = Regex("""\bR[S5Ş]\s?(?=\d{1,3}(?:\.\d{3})*,\d{2})""")

    /** Corrige erros típicos do OCR antes do parser (só leituras por imagem). */
    fun fixOcr(text: String): String = text.replace(ocrCurrency) { "R\$" }

    fun normalize(text: String): String =
        Normalizer.normalize(text.replace(' ', ' ').lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace(spaces, " ")
            .trim()

    fun moneyIn(text: String): List<MoneyMatch> {
        val clean = text.replace(' ', ' ')
        return money.findAll(clean).map { m ->
            val reais = m.groupValues[2].replace(".", "").toLong()
            val cents = m.groupValues[3].ifEmpty { "0" }.toLong()
            MoneyMatch(reais * 100 + cents, m.groupValues[1].isNotEmpty(), m.range.first, m.range.last + 1)
        }.toList()
    }

    /** "R$1,58/km aprox." exibido pelo próprio app -> 158. Serve de prova dos nossos números. */
    fun platformRatePerKm(text: String): Long? =
        platformRate.find(text.replace('\u00A0', ' '))?.let { it.groupValues[1].toLong() * 100 + (it.groupValues[2].ifEmpty { "0" }).toLong() }

    /** Linha com tempo E distância, ex.: "7 min (2.8 km)". */
    fun hasTimeAndDistance(normalized: String): Boolean =
        durationSeconds(normalized) != null && distanceMeters(normalized) != null

    fun isPriceRange(text: String): Boolean = range.containsMatchIn(text.replace(' ', ' '))

    /** Linha que é "só" um preço, ex.: "R$ 28,50". Sinal forte de ser o valor principal. */
    fun isStandalonePrice(text: String): Boolean {
        val clean = text.replace(' ', ' ').trim()
        val matches = moneyIn(clean)
        return matches.size == 1 && !matches[0].isAddition &&
            clean.removeRange(matches[0].start, matches[0].end).isBlank()
    }

    /** Distância em metros no texto já normalizado. "1,2 km" -> 1200, "800 m" -> 800. */
    fun distanceMeters(normalized: String): Int? {
        val m = distance.find(normalized) ?: return null
        val number = parseDecimal(m.groupValues[1]) ?: return null
        return if (m.groupValues[2] == "km") (number * 1000).toInt() else number.toInt()
    }

    /** Duração em segundos: "15 min", "1 h 5 min", "1h05", "2 horas". */
    fun durationSeconds(normalized: String): Int? {
        hourMinutes.find(normalized)?.let {
            return it.groupValues[1].toInt() * 3600 + it.groupValues[2].toInt() * 60
        }
        minutes.find(normalized)?.let { return it.groupValues[1].toInt() * 60 }
        hoursOnly.find(normalized)?.let { return it.groupValues[1].toInt() * 3600 }
        return null
    }

    fun containsAny(normalized: String, keywords: List<String>): Boolean = keywords.any { normalized.contains(it) }

    // "1.2" (inglês) e "1,2" (português) são decimais; "1.200" é milhar.
    private fun parseDecimal(raw: String): Double? {
        val sep = raw.indexOfAny(charArrayOf('.', ','))
        val normalized = if (sep >= 0 && raw.length - sep - 1 == 3 && raw[sep] == '.') {
            raw.replace(".", "")
        } else {
            raw.replace(',', '.')
        }
        return normalized.toDoubleOrNull()
    }
}
