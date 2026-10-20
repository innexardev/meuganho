package com.innexar.meuganho.domain.model

/**
 * Estados da corrida. ACCEPTED não é um estado próprio: aceitar é o instante
 * da transição OFFERED -> TO_PICKUP e fica registrado em acceptedAt.
 */
enum class TripState(val label: String) {
    IDLE("Aguardando corrida"),
    OFFERED("Oferta na tela"),
    TO_PICKUP("Indo buscar"),
    WAITING_PASSENGER("Esperando passageiro"),
    IN_TRIP("Em corrida"),
    COMPLETED("Concluída"),
    CANCELLED("Cancelada"),
    REJECTED("Não aceita"),
    UNKNOWN("Indefinido");

    val isActiveTrip: Boolean get() = this == TO_PICKUP || this == WAITING_PASSENGER || this == IN_TRIP
}

/** Segmento de deslocamento para separar km vazio, até o passageiro e pago. */
enum class DriveSegment { EMPTY, PICKUP, PAID }
