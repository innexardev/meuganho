package com.innexar.meuganho.app.navigation

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.innexar.meuganho.app.appContainer
import com.innexar.meuganho.core.designsystem.components.MgBackground
import com.innexar.meuganho.core.money.Units
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.innexar.meuganho.features.analytics.AnalyticsScreen
import com.innexar.meuganho.features.dashboard.DashboardScreen
import com.innexar.meuganho.features.diagnostics.DiagnosticsScreen
import com.innexar.meuganho.features.expenses.ExpensesScreen
import com.innexar.meuganho.features.fuel.FuelFormScreen
import com.innexar.meuganho.features.fuel.FuelScreen
import com.innexar.meuganho.features.goals.GoalsScreen
import com.innexar.meuganho.features.history.DayDetailScreen
import com.innexar.meuganho.features.history.HistoryScreen
import com.innexar.meuganho.features.more.MoreScreen
import com.innexar.meuganho.features.offers.OffersScreen
import com.innexar.meuganho.features.onboarding.PermissionsScreen
import com.innexar.meuganho.features.review.ReviewScreen
import com.innexar.meuganho.features.settings.SettingsScreen
import com.innexar.meuganho.features.shift.ShiftScreen
import com.innexar.meuganho.features.trips.ManualTripScreen
import com.innexar.meuganho.features.vehicle.VehicleScreen

private val leftTabs = listOf(
    NavTab(Routes.DASHBOARD, "Hoje", Icons.Filled.Home),
    NavTab(Routes.HISTORY, "Histórico", Icons.Filled.DateRange),
)
private val rightTabs = listOf(
    NavTab(Routes.ANALYTICS, "Análises", Icons.Filled.Star),
    NavTab(Routes.MORE, "Mais", Icons.Filled.Menu),
)
/** Destinos de nível superior: a cápsula aparece neles e ir até eles é troca de aba. */
private val tabRoutes = (leftTabs + rightTabs).map { it.route } + Routes.SHIFT

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val container = androidx.compose.ui.platform.LocalContext.current.appContainer
    val shift by container.shiftRepository.openShift.collectAsStateWithLifecycle(initialValue = null)
    val today by remember { container.statsRepository.observeToday() }.collectAsStateWithLifecycle(initialValue = null)
    MgBackground {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (route in tabRoutes) {
                CapsuleNav(
                    left = leftTabs, right = rightTabs, current = route,
                    shiftElapsed = shift?.let { Units.duration(today?.summary?.onlineSeconds ?: 0) },
                    onSelect = { nav.navigateToTab(it) },
                    onShift = { nav.navigateToTab(Routes.SHIFT) },
                )
            }
        },
    ) { padding ->
        // consumeWindowInsets: evita que as telas internas apliquem de novo o recuo das barras do sistema.
        NavHost(nav, startDestination = Routes.DASHBOARD, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            val back: () -> Unit = { nav.popBackStack() }
            // Ir para uma aba (por botão ou pela barra) é SEMPRE troca de aba. Empilhar uma aba em
            // cima de outra fazia a barra "restaurar" a pilha errada e prendia o usuário no Turno.
            val go: (String) -> Unit = { route -> if (route in tabRoutes) nav.navigateToTab(route) else nav.navigate(route) }
            composable(Routes.DASHBOARD) { DashboardScreen(navigate = go) }
            composable(Routes.SHIFT) { ShiftScreen(navigate = go) }
            composable(Routes.HISTORY) { HistoryScreen(navigate = go) }
            composable(Routes.MORE) { MoreScreen(navigate = go) }
            composable(Routes.OFFERS) { OffersScreen(onBack = back) }
            composable(Routes.REVIEW) { ReviewScreen(onBack = back) }
            composable(Routes.MANUAL_TRIP) { ManualTripScreen(onBack = back) }
            composable(Routes.DAY, arguments = listOf(navArgument("epochDay") { type = NavType.LongType })) {
                DayDetailScreen(epochDay = it.arguments?.getLong("epochDay") ?: 0L, onBack = back)
            }
            composable(Routes.FUEL) { FuelScreen(onBack = back, onNew = { nav.navigate(Routes.FUEL_NEW) }) }
            composable(Routes.FUEL_NEW) { FuelFormScreen(onBack = back) }
            composable(Routes.EXPENSES) { ExpensesScreen(onBack = back) }
            composable(Routes.VEHICLE) { VehicleScreen(onBack = back) }
            composable(Routes.GOALS) { GoalsScreen(onBack = back) }
            composable(Routes.ANALYTICS) { AnalyticsScreen(onBack = null) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = back, navigate = go) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = back) }
            composable(Routes.PERMISSIONS) { PermissionsScreen(onDone = back, onboarding = false) }
        }
    }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
