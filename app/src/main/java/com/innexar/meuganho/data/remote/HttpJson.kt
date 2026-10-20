package com.innexar.meuganho.data.remote

import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP mínimo com HttpURLConnection: duas chamadas simples não justificam OkHttp/Retrofit.
 * Chamar sempre fora da main thread.
 */
object HttpJson {
    private const val TIMEOUT_MS = 10_000

    fun get(url: String): String {
        val conn = open(url, "GET")
        return conn.useResponse()
    }

    fun post(url: String, body: String, headers: Map<String, String>): String {
        val conn = open(url, "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray()) }
        return conn.useResponse()
    }

    fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS * 3
            setRequestProperty("User-Agent", "MeuGanho-Android")
        }

    private fun HttpURLConnection.useResponse(): String = try {
        val code = responseCode
        if (code !in 200..299) throw HttpException(code)
        inputStream.bufferedReader().use { it.readText() }
    } finally {
        disconnect()
    }
}

class HttpException(val code: Int) : Exception("HTTP $code")
