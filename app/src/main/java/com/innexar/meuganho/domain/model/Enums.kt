package com.innexar.meuganho.domain.model

enum class FuelType(val label: String) {
    GASOLINE("Gasolina"),
    ETHANOL("Etanol"),
    FLEX("Flex"),
    DIESEL("Diesel"),
    CNG("GNV"),
    /** Elétrico: "consumo" em km/kWh e preço em R$/kWh. */
    ELECTRIC("Elétrico"),
}

enum class ExpenseCategory(val label: String) {
    FOOD("Alimentação"),
    PARKING("Estacionamento"),
    TOLL("Pedágio"),
    WASH("Lavagem"),
    MAINTENANCE("Manutenção"),
    PHONE("Celular/Internet"),
    FEES("Taxas"),
    OTHER("Outros"),
}

enum class GoalPeriod(val label: String) { DAILY("Diária"), WEEKLY("Semanal"), MONTHLY("Mensal") }

enum class GoalKind(val label: String) { GROSS("Bruta"), NET("Líquida") }

enum class OfferRating(val label: String, val symbol: String) {
    EXCELLENT("EXCELENTE", "▲"),
    ACCEPTABLE("ACEITÁVEL", "●"),
    POOR("RUIM", "▼"),

    // Nunca exibimos um veredito baseado em dado incerto.
    UNCERTAIN("INCERTO", "?"),
}
