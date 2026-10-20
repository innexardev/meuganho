package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ParserHealth(val samples: Int, val badSamples: Int, val tripped: Boolean) {
    val badRatio: Double get() = if (samples == 0) 0.0 else badSamples.toDouble() / samples
}

/**
 * Circuit breaker dos parsers.
 *
 * Se muitas das últimas capturas de uma plataforma caem em NEEDS_REVIEW/INVALID,
 * a interface do app provavelmente mudou. Nesse caso paramos de classificar
 * ofertas daquela plataforma (overlay mostra "análise indisponível") em vez de
 * contaminar dias inteiros de estatística até alguém perceber.
 *
 * Continua medindo enquanto aberto: volta sozinho quando a taxa normaliza.
 */
class ParserHealthMonitor(
    private val windowSize: Int = 20,
    private val minSamples: Int = 10,
    private val badThreshold: Double = 0.3,
) {
    private val windows = mutableMapOf<Platform, ArrayDeque<Boolean>>()
    private val _health = MutableStateFlow<Map<Platform, ParserHealth>>(emptyMap())
    val health: StateFlow<Map<Platform, ParserHealth>> = _health.asStateFlow()

    @Synchronized
    fun record(platform: Platform, ok: Boolean) {
        val window = windows.getOrPut(platform) { ArrayDeque(windowSize) }
        if (window.size == windowSize) window.removeFirst()
        window.addLast(ok)
        val bad = window.count { !it }
        val tripped = window.size >= minSamples && bad.toDouble() / window.size > badThreshold
        _health.value = _health.value + (platform to ParserHealth(window.size, bad, tripped))
    }

    fun isTripped(platform: Platform): Boolean = _health.value[platform]?.tripped == true

    @Synchronized
    fun reset(platform: Platform) {
        windows.remove(platform)
        _health.value = _health.value - platform
    }
}
