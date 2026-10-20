package com.innexar.meuganho

import com.innexar.meuganho.capture.core.ScreenSnapshot

/** Carrega telas anonimizadas de src/test/resources/fixtures. */
object Fixtures {
    fun snapshot(path: String, packageName: String, capturedAt: Long = 1_000_000L): ScreenSnapshot {
        val text = requireNotNull(javaClass.classLoader?.getResource("fixtures/$path")) { "Fixture ausente: $path" }.readText()
        return ScreenSnapshot.fromText(packageName, text, capturedAt)
    }

    fun uber(name: String) = snapshot("uber/$name.txt", "com.ubercab.driver")
    fun ninetyNine(name: String) = snapshot("ninetynine/$name.txt", "com.app99.driver")
}
