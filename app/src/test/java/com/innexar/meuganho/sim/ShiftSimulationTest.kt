package com.innexar.meuganho.sim

import com.innexar.meuganho.domain.model.Platform
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Simulação de turnos completos no servidor: N turnos gerados (sementes fixas = sempre os mesmos),
 * Uber e 99 juntos, passando pelo pipeline real. Gera o relatório em app/build/sim-report e
 * FALHA se a precisão cair abaixo do piso (trava a publicação de uma versão pior).
 *
 *   ./gradlew :app:testDebugUnitTest --tests '*ShiftSimulationTest*' -Psim.shifts=50
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ShiftSimulationTest {

    @Test fun `turnos simulados com Uber e 99 juntos`() {
        val shifts = (System.getProperty("sim.shifts") ?: "20").toInt()
        val total = Platform.entries.associateWith { Score() }
        val problems = mutableListOf<String>()
        repeat(shifts) { i ->
            val shift = ShiftGenerator(seed = 1_000 + i, start = Simulator.START).generate()
            Simulator().use { sim ->
                sim.run(shift)
                Scorer.score(shift, sim.offers(), sim.trips(), problems).forEach { (p, s) -> total.getValue(p) += s }
            }
        }
        val report = Report(shifts, total, problems)
        report.write(File(System.getProperty("sim.out") ?: "build/sim-report"))
        println(report.text())

        // Pisos: a precisão atual menos uma folga. Subir os pisos quando o app melhorar.
        for ((p, s) in total) {
            assertTrue("${p.label}: ofertas lidas ${pct(s.offersRead, s.offersShown)}", s.offersRead * 100 >= s.offersShown * MIN_OFFERS_READ)
            assertTrue("${p.label}: corridas registradas ${pct(s.tripsFound, s.tripsTrue)}", s.tripsFound * 100 >= s.tripsTrue * MIN_TRIPS_FOUND)
            assertTrue("${p.label}: valor exato ${pct(s.valueExact, s.completedTrue)}", s.valueExact * 100 >= s.completedTrue * MIN_VALUE_EXACT)
            assertTrue("${p.label}: corridas falsas ${s.falseTrips}", s.falseTrips * 100 <= s.tripsTrue * MAX_FALSE_TRIPS)
        }
    }

    /** Mesmos turnos, como o iPhone leria (só OCR da tela inteira). Relatório, sem piso por enquanto. */
    @Test fun `turnos simulados como no iPhone`() {
        val shifts = (System.getProperty("sim.shifts") ?: "20").toInt()
        val total = Platform.entries.associateWith { Score() }
        val problems = mutableListOf<String>()
        repeat(shifts) { i ->
            val shift = ShiftGenerator(seed = 1_000 + i, start = Simulator.START).generate()
            Simulator().use { sim ->
                sim.run(shift, iphone = true)
                Scorer.score(shift, sim.offers(), sim.trips(), problems).forEach { (p, s) -> total.getValue(p) += s }
            }
        }
        val report = Report(shifts, total, problems)
        report.write(File((System.getProperty("sim.out") ?: "build/sim-report") + "-iphone"))
        println("[iPhone] " + report.text())
    }

    companion object {
        // Precisão em 30/09/2026 (0.6.3, gerador com as telas reais do dia): ofertas 99–100%, corridas 98–100%,
        // valor 97–100%, falsas ≤ 1,5%. Subir os pisos quando o app melhorar.
        const val MIN_OFFERS_READ = 97
        const val MIN_TRIPS_FOUND = 95
        const val MIN_VALUE_EXACT = 90
        const val MAX_FALSE_TRIPS = 3
        fun pct(a: Int, b: Int) = if (b == 0) "-" else "$a/$b (${a * 100 / b}%)"
    }
}
