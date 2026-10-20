package com.innexar.meuganho.data.remote

import android.content.Context
import com.innexar.meuganho.BuildConfig
import com.innexar.meuganho.capture.core.RemoteRules
import com.innexar.meuganho.core.logging.AppLog
import com.innexar.meuganho.core.logging.LogCategory
import java.io.File

/** Baixa as regras de leitura do servidor e guarda a última boa em disco (vale sem internet). */
class RemoteRulesSync(context: Context) {
    private val file = File(context.filesDir, "rules.json")
    @Volatile private var lastFetchAt = 0L

    fun loadCached() {
        runCatching { if (file.exists()) RemoteRules.load(file.readText()) }
    }

    /** No máximo a cada 15 min. Chamar fora da main thread. */
    fun refreshIfDue(now: Long = System.currentTimeMillis()) {
        if (now - lastFetchAt < 15 * 60_000L) return
        lastFetchAt = now
        runCatching {
            val body = HttpJson.get("${BuildConfig.DIST_BASE_URL}/rules.json")
            val before = RemoteRules.version
            if (RemoteRules.load(body)) {
                file.writeText(body)
                if (RemoteRules.version != before) AppLog.d(LogCategory.CAPTURE, "Regras de leitura v${RemoteRules.version}")
            }
        }.onFailure { AppLog.d(LogCategory.CAPTURE, "Regras de leitura: sem atualização (${it.message})") }
    }
}
