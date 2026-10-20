package com.innexar.meuganho.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.innexar.meuganho.app.MeuGanhoApp
import com.innexar.meuganho.core.designsystem.theme.Mg
import com.innexar.meuganho.core.designsystem.theme.MeuGanhoTheme
import com.innexar.meuganho.data.repository.StatsRepository
import com.innexar.meuganho.domain.financial.CostModel
import com.innexar.meuganho.domain.financial.OfferAnalyzer
import com.innexar.meuganho.domain.financial.RatingThresholds
import com.innexar.meuganho.domain.model.DataStatus
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.engine.OverlayOffer
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
import com.innexar.meuganho.platform.overlay.OverlayFormatter
import com.innexar.meuganho.platform.overlay.OverlayView
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Renderiza as telas reais (banco preenchido) no tamanho de um Galaxy A32 e salva
 * PNGs em app/build/screenshots para revisão visual, sem emulador.
 * Rodar: ./gradlew :app:testDebugUnitTest --tests '*ScreensTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xhdpi")
class ScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun seed() {
        StatsRepository.ticking = false
        SeedData.fill(ApplicationProvider.getApplicationContext<MeuGanhoApp>().container)
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            MeuGanhoTheme { com.innexar.meuganho.core.designsystem.components.MgBackground { content() } }
        }
        compose.saveScreen(name)
    }

    private val none: () -> Unit = {}
    private val go: (String) -> Unit = {}

    @Test fun s00_app() = shot("00_app_hoje") { com.innexar.meuganho.app.navigation.AppNavHost() }
    @Test fun s01_hoje() = shot("01_hoje") { DashboardScreen(go) }
    @Test fun s02_turno() = shot("02_turno_ativo") { ShiftScreen(go) }
    @Test fun s03_historico() = shot("03_historico") { HistoryScreen(go) }
    @Test fun s04_dia() = shot("04_detalhe_dia") { DayDetailScreen(LocalDate.now().toEpochDay(), none) }
    @Test fun s05_ofertas() = shot("05_ofertas") { OffersScreen(none) }
    @Test fun s06_revisar() = shot("06_revisar") { ReviewScreen(none) }
    @Test fun s07_mais() = shot("07_mais") { MoreScreen(go) }
    @Test fun s08_config() = shot("08_configuracoes") { SettingsScreen(none, go) }
    @Test fun s09_veiculo() = shot("09_veiculo") { VehicleScreen(none) }
    @Test fun s10_abastecimentos() = shot("10_abastecimentos") { FuelScreen(none, none) }
    @Test fun s11_novo_abastecimento() = shot("11_novo_abastecimento") { FuelFormScreen(none) }
    @Test fun s12_despesas() = shot("12_despesas") { ExpensesScreen(none) }
    @Test fun s13_metas() = shot("13_metas") { GoalsScreen(none) }
    @Test fun s14_analises() = shot("14_analises") { AnalyticsScreen(none) }
    @Test fun s15_corrida_manual() = shot("15_corrida_manual") { ManualTripScreen(none) }
    @Test fun s16_diagnostico() = shot("16_diagnostico") { DiagnosticsScreen(none) }
    @Test fun s17_permissoes() = shot("17_permissoes") { PermissionsScreen(onDone = none, onboarding = true) }

    /** Painel flutuante (Views nativas): resumo, uma oferta e Uber x 99. */
    @Test fun s20_overlay() {
        val ctx = compose.activity
        val cost = CostModel(619, 12.5, 12.0, 15.0, 0.0)
        fun offer(p: Platform, cents: Long, m: Long, s: Long, reliable: Boolean = true) =
            OverlayOffer(p, OfferAnalyzer.analyze(cents, m, s, reliable, cost, RatingThresholds()), DataStatus.AUTO_ACCEPTED, false, 0)
        val cases = mapOf(
            "20_painel_oferta" to OverlayFormatter.offers(listOf(offer(Platform.UBER, 2679, 15_600, 27 * 60))),
            "21_painel_incerto" to OverlayFormatter.offers(listOf(offer(Platform.UBER, 3593, 22_800, 50 * 60, reliable = false))),
            "22_painel_uber_x_99" to OverlayFormatter.offers(listOf(offer(Platform.NINETY_NINE, 1210, 9_700, 21 * 60),
                offer(Platform.UBER, 2260, 11_000, 20 * 60))),
            "23_painel_resumo" to OverlayFormatter.summary(null, expanded = false),
        )
        val extras = mapOf<String, View>(
            "24_painel_bolinha" to com.innexar.meuganho.platform.overlay.OverlayBubble(ctx).apply { text = "R$81\n/h" },
            "25_alvo_fechar" to com.innexar.meuganho.platform.overlay.OverlayCloseTarget(ctx).apply { highlight(true) },
        )
        (cases.mapValues { (_, t) -> OverlayView(ctx).apply { render(t) } as View } + extras).forEach { (name, view) ->
            val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(spec, spec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val bmp = Bitmap.createBitmap(view.measuredWidth + 40, view.measuredHeight + 40, Bitmap.Config.ARGB_8888)
            Canvas(bmp).apply { drawColor(0xFF7A8A9A.toInt()); translate(20f, 20f); view.draw(this) }
            File("build/screenshots").mkdirs()
            File("build/screenshots/$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
