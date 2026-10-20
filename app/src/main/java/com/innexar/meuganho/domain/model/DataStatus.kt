package com.innexar.meuganho.domain.model

/**
 * Estado de confiabilidade de um dado capturado.
 *
 * Regra de ouro: só CONFIRMED e AUTO_ACCEPTED entram nas estatísticas.
 * NEEDS_REVIEW nunca é descartado em silêncio: aparece como "pendente" em
 * todos os totais, para não subestimar o dia.
 */
enum class DataStatus(val label: String) {
    CONFIRMED("Confirmado"),
    AUTO_ACCEPTED("Automático"),
    NEEDS_REVIEW("Revisar"),
    INVALID("Inválido"),
    DUPLICATE("Duplicado");

    val countsInStats: Boolean get() = this == CONFIRMED || this == AUTO_ACCEPTED
    val isPending: Boolean get() = this == NEEDS_REVIEW
}

/** O que aconteceu com a oferta depois de exibida. */
enum class OfferOutcome(val label: String) {
    PENDING("Exibida"),
    ACCEPTED("Aceita"),
    NOT_ACCEPTED("Não aceita"),
}

/** Origem do valor final de uma corrida, da mais fraca para a mais forte. */
enum class ValueSource(val label: String) {
    NONE("Sem valor"),
    OFFER_ESTIMATE("Estimativa da oferta"),
    COMPLETION_SCREEN("Tela de corrida concluída"),
    /** Subida do total do dia exibido pela plataforma logo após a corrida (valor pago, com dinâmica/gorjeta). */
    PLATFORM_TOTAL("Total do dia da plataforma"),
    USER("Informado por você"),
    /** Histórico de ganhos da própria plataforma: o valor efetivamente pago. */
    PLATFORM_HISTORY("Histórico da plataforma"),
}
