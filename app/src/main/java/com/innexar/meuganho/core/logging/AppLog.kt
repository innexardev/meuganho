package com.innexar.meuganho.core.logging

import android.util.Log
import com.innexar.meuganho.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogCategory { CAPTURE, UBER, NINETY_NINE, TRIP, GPS, DATABASE, FINANCIAL, OVERLAY, SYSTEM }

data class LogEntry(val at: Long, val category: LogCategory, val message: String, val isError: Boolean)

/**
 * Log com categorias e buffer em memória para a tela de Diagnóstico.
 *
 * O buffer é circular e pequeno (RAM constante). Nada vai para disco:
 * o motorista consegue diagnosticar sem ADB, mas não acumulamos arquivos.
 * Em release só erros chegam ao Logcat.
 *
 * Nunca registrar texto bruto de tela aqui: pode conter endereço ou nome de passageiro.
 */
object AppLog {
    private const val TAG = "MeuGanho"
    private const val CAPACITY = 150

    private val buffer = ArrayDeque<LogEntry>(CAPACITY)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun d(category: LogCategory, message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[$category] $message")
        append(LogEntry(System.currentTimeMillis(), category, message, isError = false))
    }

    fun e(category: LogCategory, message: String, error: Throwable? = null) {
        Log.e(TAG, "[$category] $message", error)
        val detail = error?.let { " (${it.javaClass.simpleName}: ${it.message})" } ?: ""
        append(LogEntry(System.currentTimeMillis(), category, message + detail, isError = true))
    }

    @Synchronized
    private fun append(entry: LogEntry) {
        if (buffer.size == CAPACITY) buffer.removeFirst()
        buffer.addLast(entry)
        _entries.value = buffer.toList()
    }
}
