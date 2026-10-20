package com.innexar.meuganho.app.navigation

/** Rotas de navegação. Strings simples: o app tem poucas telas e argumentos triviais. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val SHIFT = "shift"
    const val HISTORY = "history"
    const val MORE = "more"
    const val OFFERS = "offers"
    const val REVIEW = "review"
    const val MANUAL_TRIP = "manual_trip"
    const val DAY = "day/{epochDay}"
    const val FUEL = "fuel"
    const val FUEL_NEW = "fuel/new"
    const val EXPENSES = "expenses"
    const val VEHICLE = "vehicle"
    const val GOALS = "goals"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val PERMISSIONS = "permissions"

    fun day(epochDay: Long) = "day/$epochDay"
}
