package com.innexar.meuganho.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** Corrida como consta no histórico OFICIAL da plataforma (valor pago, duração, km). */
data class PlatformTripRecord(
    val platform: Platform,
    val date: LocalDate,
    val time: LocalTime,
    val valueCents: Long,
    val category: String,
    /** 0 = desconhecido (o extrato da 99 não traz duração nem km). */
    val durationSeconds: Int,
    val meters: Int,
    /** O horário é o do FIM/pagamento (extrato da 99), não o do início (histórico do Uber). */
    val timeIsEnd: Boolean = false,
    /** Corrida em dinheiro (99): o extrato só mostra a taxa descontada; o valor recebido fica desconhecido (valueCents = 0). */
    val cashFeeCents: Long? = null,
)
