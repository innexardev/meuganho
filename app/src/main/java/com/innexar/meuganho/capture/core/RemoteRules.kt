package com.innexar.meuganho.capture.core

import com.innexar.meuganho.domain.model.Platform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Regras de leitura publicadas no servidor (innexar.com.br/meuganho/rules.json): palavras-chave
 * EXTRAS por plataforma e tipo de tela. Quando o Uber ou a 99 mudam um texto, a correção chega
 * ao app em minutos, sem versão nova. Só ACRESCENTAM às regras do app (nunca removem):
 * uma regra remota errada não quebra o que já funciona.
 *
 * {"version": 3, "UBER": {"completed": ["viagem encerrada"], "idle": ["..."]}, "NINETY_NINE": {...}}
 * Chaves: toPickup, waiting, inTrip, completed, cancelled, dailyEarnings, idle, earningsHistory.
 */
object RemoteRules {
    @Volatile var version: Int = 0
        private set

    @Volatile private var rules: Map<Platform, Map<String, List<String>>> = emptyMap()

    fun extra(platform: Platform, key: String): List<String> = rules[platform]?.get(key).orEmpty()

    /** Aplica o JSON; devolve false (e mantém as regras atuais) se estiver malformado. */
    fun load(json: String): Boolean = runCatching {
        val root = Json.parseToJsonElement(json) as JsonObject
        val parsed = Platform.entries.associateWith { p ->
            (root[p.name] as? JsonObject)?.mapValues { (_, v) ->
                (v as JsonArray).mapNotNull { it.jsonPrimitive.contentOrNull?.let(TextPatterns::normalize) }.filter { it.length >= 3 }
            }.orEmpty()
        }
        rules = parsed
        version = root["version"]?.jsonPrimitive?.intOrNull ?: 0
        true
    }.getOrDefault(false)

    fun clear() {
        rules = emptyMap()
        version = 0
    }
}
