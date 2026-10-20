package com.innexar.meuganho.capture.core

/** Limpezas de leituras por imagem (Android e iPhone usam as mesmas regras; ver ios/Shared/OfferReader.swift). */
object OcrCleanup {
    /**
     * Reserva caso a notificação do MeuGanho ainda apareça na imagem: título "99 · R$ 6,10 …" seguido das
     * nossas métricas ("R$ 6,52/km R$92/h", "R$ 1,53/min" ou "/mi."). Some daqui antes do parser.
     */
    fun withoutOwnNotification(lines: List<ScreenLine>): List<ScreenLine> {
        val title = Regex("""^(★.*)?(99|uber)\W{0,3}R\$\s?\d+,\d{2}""", RegexOption.IGNORE_CASE)
        val metric = Regex("""R\$\s?\d+(,\d+)?/(h|min|mi)|/km\s+R\$|R\$\s\d+,\d{2}/km""")
        val out = mutableListOf<ScreenLine>()
        var skipping = false
        for (l in lines) {
            val t = l.text.trim()
            if (title.containsMatchIn(t)) { skipping = true; continue }
            if (skipping && metric.containsMatchIn(t)) continue
            skipping = false
            out += l
        }
        return out
    }

}
