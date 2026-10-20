package com.innexar.meuganho.sim

import com.innexar.meuganho.domain.model.Platform
import java.io.File

/** Relatório da simulação: texto (console), JSON (histórico) e HTML (página no servidor). */
class Report(private val shifts: Int, private val scores: Map<Platform, Score>, private val problems: List<String>) {

    private data class Row(val label: String, val money: Boolean = false, val get: (Score) -> Pair<Long, Long>)

    private val rows = listOf(
        Row("Ofertas lidas") { it.offersRead.toLong() to it.offersShown.toLong() },
        Row("Aceite/recusa certos") { it.outcomesRight.toLong() to it.offersRead.toLong() },
        Row("Aceites ligados à oferta certa") { it.acceptsLinked.toLong() to it.acceptsTrue.toLong() },
        Row("Corridas registradas") { it.tripsFound.toLong() to it.tripsTrue.toLong() },
        Row("Concluída/cancelada certo") { it.stateRight.toLong() to it.tripsFound.toLong() },
        Row("Valor exato") { it.valueExact.toLong() to it.completedTrue.toLong() },
        Row("Valor oficial (conferido)") { it.valueOfficial.toLong() to it.completedTrue.toLong() },
        Row("Faturamento no app / real", money = true) { it.grossApp to it.grossTrue },
    )

    private fun pct(a: Long, b: Long) = if (b == 0L) "-" else "${a * 100 / b}%"
    private fun cell(r: Row, s: Score): String {
        val (a, b) = r.get(s)
        return if (r.money) "${Scorer.money(a)} / ${Scorer.money(b)}" else "$a/$b (${pct(a, b)})"
    }

    fun text(): String = buildString {
        appendLine("=== Simulação: $shifts turnos com Uber e 99 juntos ===")
        rows.forEach { r -> appendLine("%-34s %-28s %s".format(r.label, "Uber " + cell(r, scores.getValue(Platform.UBER)), "99 " + cell(r, scores.getValue(Platform.NINETY_NINE)))) }
        scores.forEach { (p, s) -> appendLine("${p.label}: corridas que não existiram = ${s.falseTrips}") }
        appendLine("--- primeiras divergências ---")
        problems.take(40).forEach { appendLine(it) }
    }

    fun write(dir: File) {
        dir.mkdirs()
        val json = buildString {
            append("{\"shifts\":$shifts,\"generatedAt\":\"${java.time.OffsetDateTime.now()}\",\"platforms\":{")
            append(scores.entries.joinToString(",") { (p, s) ->
                "\"${p.name}\":{" + rows.joinToString(",") { r -> val (a, b) = r.get(s); "\"${r.label}\":[$a,$b]" } + ",\"falseTrips\":${s.falseTrips}}"
            })
            append("},\"problems\":[")
            append(problems.take(300).joinToString(",") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" })
            append("]}")
        }
        File(dir, "report.json").writeText(json)
        File(dir, "index.html").writeText(html())
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun bar(a: Long, b: Long): String {
        val p = if (b == 0L) 0 else (a * 100 / b).toInt().coerceIn(0, 100)
        val tone = when { p >= 95 -> "good"; p >= 80 -> "warn"; else -> "bad" }
        return "<div class=\"bar $tone\"><i style=\"width:$p%\"></i></div>"
    }

    private fun html(): String = """<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>MeuGanho · Simulação</title>
<style>
:root{--bg:#0e1116;--card:#171b22;--text:#e8ebf0;--muted:#8b93a1;--line:#252b35;--good:#22c55e;--warn:#eab308;--bad:#ef4444}
@media (prefers-color-scheme: light){:root{--bg:#f5f6f8;--card:#fff;--text:#12151a;--muted:#5d6572;--line:#e3e6eb}}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.45 system-ui,sans-serif}
main{max-width:860px;margin:0 auto;padding:24px 16px}
h1{font-size:22px;margin:0 0 4px}.sub{color:var(--muted);margin:0 0 20px}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px;margin-bottom:16px;overflow-x:auto}
table{width:100%;border-collapse:collapse}th,td{text-align:left;padding:8px 6px;border-bottom:1px solid var(--line);vertical-align:top}
th{color:var(--muted);font-weight:600;font-size:13px}td.n{font-variant-numeric:tabular-nums;white-space:nowrap}
.bar{height:6px;background:var(--line);border-radius:3px;margin-top:4px;min-width:90px}.bar i{display:block;height:100%;border-radius:3px}
.good i{background:var(--good)}.warn i{background:var(--warn)}.bad i{background:var(--bad)}
ul{margin:0;padding-left:18px;color:var(--muted);font-size:13px}li{margin:3px 0}
</style></head><body><main>
<h1>Simulação de turnos</h1>
<p class="sub">$shifts turnos gerados com Uber e 99 ao mesmo tempo, passando pelo MeuGanho real (leitura, corridas, conciliador). Gerado em ${java.time.LocalDateTime.now().withNano(0)}.</p>
<div class="card"><table><tr><th>Medida</th><th>Uber</th><th>99</th></tr>
${rows.joinToString("\n") { r ->
        val u = scores.getValue(Platform.UBER); val n = scores.getValue(Platform.NINETY_NINE)
        val (ua, ub) = r.get(u); val (na, nb) = r.get(n)
        "<tr><td>${r.label}</td><td class=\"n\">${esc(cell(r, u))}${if (r.money) "" else bar(ua, ub)}</td><td class=\"n\">${esc(cell(r, n))}${if (r.money) "" else bar(na, nb)}</td></tr>"
    }}
<tr><td>Corridas que não existiram</td><td class="n">${scores.getValue(Platform.UBER).falseTrips}</td><td class="n">${scores.getValue(Platform.NINETY_NINE).falseTrips}</td></tr>
</table></div>
<div class="card"><strong>Divergências (${problems.size})</strong><ul>
${problems.take(150).joinToString("\n") { "<li>${esc(it)}</li>" }}
</ul></div>
</main></body></html>
"""
}
