package com.innexar.meuganho.data.backup

import com.innexar.meuganho.data.local.entity.TripEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** CSV com ";" e vírgula decimal: abre direto no Excel/Sheets em PT-BR. */
object TripCsv {
    const val HEADER = "data;hora;plataforma;estado;status;valor;origem_valor;estimado;km_pago;km_busca;min_corrida"

    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun row(t: TripEntity): String {
        val at = Instant.ofEpochMilli(t.acceptedAt).atZone(ZoneId.systemDefault())
        val minutes = if (t.startedAt != null && t.endedAt != null) ((t.endedAt - t.startedAt) / 60_000).toString() else ""
        return listOf(
            at.format(date), at.format(time), t.platform.label, t.state.label, t.status.label,
            money(t.effectiveCents), t.valueSource.label, money(t.estimatedCents),
            km(t.paidMeters), km(t.pickupMeters), minutes,
        ).joinToString(";")
    }

    private fun money(cents: Long?): String = cents?.let { "${it / 100},${(it % 100).toString().padStart(2, '0')}" } ?: ""
    private fun km(meters: Long): String = "${meters / 1000},${(meters % 1000 / 100)}"
}
