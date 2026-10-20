package com.innexar.meuganho.core.crash

import android.content.Context
import java.io.File

/**
 * Registra travamentos (só o erro técnico: tipo, mensagem e pilha de chamadas) num arquivo
 * local. Na próxima abertura, o erro vira amostra de diagnóstico e é enviado ao servidor.
 * Um serviço de acessibilidade que trava é desligado pelo Samsung: sem isso não dá para
 * descobrir a causa.
 */
object CrashRecorder {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file.writeText(buildString {
                    appendLine("thread=${thread.name} time=${System.currentTimeMillis()}")
                    appendLine(error.stackTraceToString().take(6_000))
                })
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Devolve e apaga o último travamento registrado, se houver. */
    fun consume(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull().also { file.delete() }
    }
}
