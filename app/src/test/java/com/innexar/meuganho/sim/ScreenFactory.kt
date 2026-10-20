package com.innexar.meuganho.sim

import com.innexar.meuganho.Fixtures
import com.innexar.meuganho.capture.core.ScreenLine
import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.domain.model.CaptureChannel
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** Uma oferta gerada: o que a plataforma mostra. */
data class SimOffer(
    val cents: Long,
    val pickupMeters: Int,
    val pickupSeconds: Int,
    val tripMeters: Int,
    val tripSeconds: Int,
    val category: String,
    val cash: Boolean,
) {
    val totalMeters get() = pickupMeters + tripMeters
}

/**
 * Telas do Uber e da 99 nos formatos REAIS (tirados das amostras de 28–29/09/2026), com valores
 * variáveis. As telas de corrida partem das fixtures reais, com os trechos anonimizados trocados
 * por nomes e ruas inventados. Leituras por imagem da 99 podem receber ruído de OCR real.
 */
class ScreenFactory(private val rnd: Random) {
    private val uberPkg = "com.ubercab.driver"
    private val nnPkg = "com.app99.driver"
    private val names = listOf("Ana", "Bruno", "Carla", "Diego", "Elisa", "Fábio", "Gabi", "Hugo", "Iara", "João")
    private val streets = listOf("Rua das Acácias", "Av. Brasil", "Rua Tupi", "Av. Pres. Kennedy", "Rua Aldo Coli",
        "Rua Jorge Tavares", "Av. do Trabalhador", "Rua Osmar Antonioli", "Rua Coroados", "Av. Castelo Branco")

    // ---------- números nos formatos das plataformas ----------
    private fun brl(cents: Long, space: Boolean) = "R$" + (if (space) " " else "") + "${cents / 100},${"%02d".format(cents % 100)}"
    private fun kmDot(m: Int) = "%.1f".format(java.util.Locale.US, m / 1000.0)
    private fun kmComma(m: Int) = if (m % 1000 == 0) "${m / 1000}" else "%.1f".format(java.util.Locale.US, m / 1000.0).replace('.', ',')
    private fun mins(s: Int) = maxOf(1, (s + 30) / 60)
    private fun perKm(cents: Long, meters: Int) = (cents * 1000 + meters / 2) / meters
    private fun street() = streets.random(rnd) + ", " + rnd.nextInt(10, 999)
    private fun name() = names.random(rnd)
    private fun rating() = "4,${rnd.nextInt(70, 99)}"

    fun randomOffer(): SimOffer {
        val pickupM = rnd.nextInt(300, 4_500)
        val tripM = rnd.nextInt(1_200, 22_000)
        val perKmCents = rnd.nextInt(95, 260) // R$ 0,95 a 2,60 por km total
        val cents = maxOf(550L, (pickupM + tripM).toLong() * perKmCents / 1000)
        return SimOffer(cents, pickupM, pickupM / 7 + rnd.nextInt(30, 180), tripM, tripM / 8 + rnd.nextInt(60, 300),
            listOf("UberX", "Comfort", "Pop", "Bag").random(rnd), cash = rnd.nextInt(100) < 20)
    }

    private fun lines(vararg l: Pair<String, String?>, group: Int = -1) = l.map { ScreenLine(it.first, it.second, group) }
    private fun snap(pkg: String, lines: List<ScreenLine>, channel: CaptureChannel = CaptureChannel.ACCESSIBILITY) =
        ScreenSnapshot(pkg, 0, 0, lines, channel)

    /** Fixture real com os trechos anonimizados trocados por nomes/ruas inventados. */
    private fun fixture(path: String, pkg: String): ScreenSnapshot {
        val base = if (pkg == uberPkg) Fixtures.uber(path) else Fixtures.ninetyNine(path)
        return base.copy(lines = base.lines.map { l ->
            ScreenLine(l.text.replace("‹texto livre›", street()).replace("‹texto›", name()), l.viewId, l.group)
        })
    }

    // ============================ UBER ============================

    fun uberOffer(o: SimOffer): ScreenSnapshot = snap(uberPkg, lines(
        o.category to null,
        "Exclusivo" to null,
        brl(o.cents, true) to null,
        "${brl(perKm(o.cents, o.totalMeters), false)}/km aprox." to null,
        "${rating()} (${rnd.nextInt(50, 900)})" to null,
        "${mins(o.pickupSeconds)} min (${kmDot(o.pickupMeters)} km)" to null,
        street() to null,
        "${mins(o.tripSeconds)} minutos (${kmDot(o.tripMeters)} km)" to null,
        street() to null,
        "Aceitar" to null,
    ))

    /** Radar de Viagens: vários cartões clicáveis empilhados. */
    fun uberRadar(offers: List<SimOffer>): ScreenSnapshot = snap(uberPkg,
        lines("Radar de Viagens" to null, "${offers.size}" to null) + offers.flatMapIndexed { i, o ->
            lines(
                o.category to null,
                brl(o.cents, true) to null,
                "${brl(perKm(o.cents, o.totalMeters), false)}/km aprox." to null,
                "${mins(o.pickupSeconds)} min (${kmDot(o.pickupMeters)} km)" to null,
                street() to null,
                "${mins(o.tripSeconds)} minutos (${kmDot(o.tripMeters)} km)" to null,
                street() to null,
                "Selecionar" to null,
                group = 10 + i * 30,
            )
        })

    /** Indo buscar. [labeled]: com "Encontro com" (às vezes o Uber mostra, às vezes só navega). */
    fun uberToPickup(labeled: Boolean, tick: Int) =
        (if (labeled) fixture("real_trip_to_pickup", uberPkg) else fixture("real_nav_no_label", uberPkg)).ticked(tick)

    fun uberWaiting(tick: Int) = fixture("real_trip_waiting", uberPkg).let { s ->
        s.copy(lines = s.lines.map { if (it.text.matches(Regex("""\d:\d\d"""))) ScreenLine("${tick % 5}:${"%02d".format(tick % 60)}", it.viewId, it.group) else it })
    }

    /** Em viagem: "Destino de" às vezes; muitas vezes só a navegação (inclusive "Pessoa convidada de"). */
    fun uberInTrip(labeled: Boolean, tick: Int) = when {
        labeled -> fixture("real_trip_in_progress", uberPkg)
        rnd.nextBoolean() -> fixture("real_nav_guest", uberPkg)
        else -> fixture("real_nav_no_label", uberPkg)
    }.ticked(tick)

    fun uberCollectCash(cents: Long) = snap(uberPkg, lines(
        "Voltar" to null, "Coletar pagamento" to null, brl(cents, true) to "ub__collect_cash_overview_card_amount_value"))

    fun uberCancelled() = snap(uberPkg, lines("Viagem cancelada" to null, "O usuário cancelou a viagem" to null, "OK" to null))

    /** Tela inicial com o contador "HOJE" (total do dia). */
    /** Tela inicial com o total do dia. Real (30/09/2026): às vezes SEM o rótulo "HOJE". */
    fun uberHome(todayCents: Long) = fixture("real_home_online", uberPkg).let { s ->
        s.copy(lines = s.lines.map {
            if (it.viewId == "ub__earnings_tracker_counter_content_status") ScreenLine(brl(todayCents, true), it.viewId, it.group) else it
        } + if (rnd.nextBoolean()) listOf(ScreenLine("HOJE", "ub__earnings_tracker_label_text")) else emptyList())
    }

    /** Online, navegando sem passageiro ("Filtro de destino" / "Procurando viagens"). Real 30/09/2026. */
    fun uberOnlineNav(tick: Int) = fixture(if (rnd.nextBoolean()) "real_online_dest_filter" else "real_online_nav_searching", uberPkg).ticked(tick)

    /** Histórico de ganhos (lista "Atividade"): cabeçalho do dia + uma entrada por corrida. */
    fun uberHistory(trips: List<Pair<Long, SimTrip>>): ScreenSnapshot {
        val out = mutableListOf<ScreenLine>()
        val zone = ZoneId.systemDefault()
        trips.groupBy { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() }.forEach { (day, list) ->
            out += ScreenLine("${weekday(day)}, ${day.dayOfMonth} de ${month(day.monthValue)}.", null)
            list.sortedByDescending { it.first }.forEach { (at, t) ->
                val time = Instant.ofEpochMilli(at).atZone(zone)
                out += ScreenLine(brl(t.finalCents, true), "ub__styled_icon_link_title")
                if (t.surgeOrTip) out += ScreenLine("Preço dinâmico", "ub_badge_text_view")
                val secs = t.offer.tripSeconds
                out += ScreenLine("${t.offer.category} · ${secs / 60} min ${secs % 60} segundos · ${"%.2f".format(java.util.Locale.US, t.offer.tripMeters / 1000.0)} km",
                    "ub__styled_icon_link_subtitle")
                out += ScreenLine("${time.hour}:${"%02d".format(time.minute)}", "ub__styled_icon_link_tertiary_title")
                out += ScreenLine(street(), "ub__pick_up_text")
                out += ScreenLine(street(), "ub__drop_off_text")
            }
        }
        return snap(uberPkg, out)
    }

    // ============================ 99 ============================

    /** Extrato da Conta99 (Acessibilidade): cartão/PIX = valor recebido; dinheiro = taxa negativa. */
    fun nnWallet(trips: List<SimTrip>): ScreenSnapshot {
        val zone = ZoneId.systemDefault()
        val out = mutableListOf(ScreenLine("Extrato", "title_bar_name"), ScreenLine("Filtrar", "titlebar_right_btn"))
        trips.sortedByDescending { it.endedAt }.groupBy { Instant.ofEpochMilli(it.endedAt).atZone(zone).toLocalDate() }.forEach { (day, list) ->
            out += ScreenLine("%02d-%02d-%04d".format(day.dayOfMonth, day.monthValue, day.year))
            out += ScreenLine("Saldo R$0,00")
            for (t in list) {
                val time = Instant.ofEpochMilli(t.endedAt).atZone(zone)
                out += ScreenLine(if (t.offer.category == "Bag") "Entrega" else "Pop")
                out += ScreenLine(if (t.offer.cash) "-" + brl(t.finalCents / 5, false) else brl(t.finalCents, false))
                out += ScreenLine("${time.hour}:${"%02d".format(time.minute)}")
                out += ScreenLine("-")
                out += ScreenLine(if (t.offer.cash) "Dinheiro" else listOf("PIX", "Cartão").random(rnd))
            }
        }
        return snap(nnPkg, out)
    }

    /** Cartão de oferta da 99 lido por IMAGEM (a 99 esconde o texto da Acessibilidade). */
    fun nnOffer(o: SimOffer, noise: Boolean): ScreenSnapshot {
        val l = mutableListOf(
            (if (o.cash) "Dinheiro" else "Cartão verif."),
            brl(o.cents, false),
            "${brl(perKm(o.cents, o.totalMeters), false)}/km",
            "${rating()} · ${rnd.nextInt(30, 900)} corridas",
            "${mins(o.pickupSeconds)} min (${kmComma(o.pickupMeters)} km)",
            street(),
            "${mins(o.tripSeconds)} min (${kmComma(o.tripMeters)} km)",
            street(),
        )
        return snap(nnPkg, ocr(l, noise), CaptureChannel.OCR)
    }

    /** Mapa da 99 com o carro andando: a Acessibilidade vê velocidade e próxima curva. */
    /** "Navegação leve" da 99 (real 30/09/2026): distância da manobra, rua, "Mapa do …". */
    fun nnLightNav(tick: Int) = snap(nnPkg, lines(
        "${50 + tick * 37 % 900} m" to "tv_maprouter_light_nav_road_eda",
        listOf("Vire à esquerda", "Vire à direita", "Siga em frente", streets.random(rnd)).random(rnd) to "tv_maprouter_light_nav_turn_name",
        name() to null,
        "Mapa do ${name()}" to null,
    ))

    /** Tela inicial da 99 com o ganho do dia no topo (OCR; real 01/10/2026). */
    fun nnHome(todayCents: Long, noise: Boolean) = snap(nnPkg, ocr(listOf(brl(todayCents, false) + (if (rnd.nextBoolean()) " -" else ""),
        streets.random(rnd), "${rnd.nextInt(1, 9)} 1,${rnd.nextInt(1, 4)}X-1,${rnd.nextInt(5, 8)}X", "101", street(), "Buscando"), noise), CaptureChannel.OCR)

    /** Fim da corrida da 99: avaliação do passageiro (OCR; print real 01/10/2026). */
    fun nnRating(noise: Boolean) = snap(nnPkg, ocr(listOf("As Solicitações seguidas estão ativadas", "Como foi sua corrida com ${name()}?",
        "Seu feedback sincero pode ajudar outros motoristas", "Avaliar como anônimo", "Pular"), noise), CaptureChannel.OCR)

    /** Aviso "Corrida aceita" (Acessibilidade; real 01/10/2026). */
    fun nnAcceptedToast() = snap(nnPkg, lines("Corrida aceita" to "toast_text"))

    fun nnCashTripEnd(noise: Boolean) = snap(nnPkg, ocr(listOf(street(), "1 min · 10 m", "Corrida em dinheiro"), noise), CaptureChannel.OCR)

    fun nnNavMap(speedKmh: Int, tick: Int) = snap(nnPkg, lines(
        "${100 + tick * 7 % 800}" to "share_track_road_indicate_txt",
        "m" to "share_track_road_indicate_txt_unit",
        streets.random(rnd) to "share_track_road_name_txt",
        streets.random(rnd) to "next_next_turn_name_tv",
        "$speedKmh" to "current_speed_tv",
        "km/h" to "current_speed_unit_tv",
    ))

    fun nnToPickup(noise: Boolean) = snap(nnPkg, ocr(listOf(street(), "km/h", "${rnd.nextInt(1, 9)} min · ${rnd.nextInt(1, 9)},${rnd.nextInt(10)} km",
        "Chegada prevista: ${rnd.nextInt(10, 23)}:${"%02d".format(rnd.nextInt(60))}", name(), "${rating()}★ · ${rnd.nextInt(10, 900)} corridas",
        "Cheguei no embarque"), noise), CaptureChannel.OCR)

    fun nnWaiting(noise: Boolean) = snap(nnPkg, ocr(listOf("Passageiro a caminho. Entre em contato para avisar que você chegou.",
        "Por favor, aguarde", "${rnd.nextInt(0, 5)}:${"%02d".format(rnd.nextInt(60))}", name(), "${rating()}★ · ${rnd.nextInt(10, 900)} corridas",
        "Iniciar corrida"), noise), CaptureChannel.OCR)

    fun nnInTrip(noise: Boolean) = snap(nnPkg, ocr(listOf(street(), "km/h", "${rnd.nextInt(1, 30)} min · ${rnd.nextInt(1, 20)},${rnd.nextInt(10)} km",
        "Chegada prevista: ${rnd.nextInt(10, 23)}:${"%02d".format(rnd.nextInt(60))}", "Finalizar corrida"), noise), CaptureChannel.OCR)

    fun nnCancelled() = snap(nnPkg, lines("Não confirmado" to "common_message_title",
        "O passageiro cancelou a corrida, mas você pode pesquisar outras corridas" to "common_message_content"))

    fun nnIdle(n: Int) = snap(nnPkg, lines("Você está online" to null, "Procurando corridas" to null, "${n % 7 + 1} corridas por perto" to null))

    /** Central de ganhos (lida por imagem). */
    fun nnEarnings(todayCents: Long, noise: Boolean) = snap(nnPkg, ocr(listOf("Central de ganhos", "Ganhos do dia (29 de set)",
        brl(todayCents, false), "Corridas", brl(todayCents, false).replace("R$", "RS") + " >", "Meus recursos"), noise), CaptureChannel.OCR)

    /**
     * Ruído de OCR visto nas amostras reais: relógio/bateria no topo, "R$" lido como "RS" nas linhas
     * secundárias, "·" como "-", endereço cortado. Em 3% das telas o preço principal sai ilegível.
     */
    private fun ocr(clean: List<String>, noise: Boolean): List<ScreenLine> {
        if (!noise) return clean.map { ScreenLine(it) }
        val out = mutableListOf("${rnd.nextInt(6, 23)}:${"%02d".format(rnd.nextInt(60))} 99 O •", "4G+ ${rnd.nextInt(20, 99)}%")
        var firstPrice = true
        for (l in clean) {
            var t = l
            val isPrice = Regex("""^R\$\s?\d+,\d{2}$""").matches(t)
            if (isPrice && firstPrice) {
                firstPrice = false
                if (rnd.nextInt(100) < 3) t = t.replace("R$", "RS").replace(",", "")
            } else if (t.contains("R$") && rnd.nextInt(100) < 25) {
                t = t.replace("R$", "RS")
            }
            if (rnd.nextInt(100) < 20) t = t.replace(" · ", " - ")
            if (streets.any { t.startsWith(it) } && rnd.nextInt(100) < 20) t = t.take(rnd.nextInt(3, t.length))
            out += t
        }
        return out.map { ScreenLine(it) }
    }

    private fun weekday(d: java.time.LocalDate) = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")[d.dayOfWeek.value - 1] + "."
    private fun month(m: Int) = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")[m - 1]
}

/** Muda um número da navegação (distância da manobra/ETA) para a tela não ser idêntica à anterior. */
private fun ScreenSnapshot.ticked(n: Int): ScreenSnapshot {
    var done = false
    return copy(lines = lines.map { l ->
        if (!done && (l.viewId == "ub__nav_maneuver_distance" || l.viewId == "ub__speed_limit" || l.viewId == "status_label_trailing")) {
            done = true
            ScreenLine(if (l.viewId == "ub__speed_limit") "${30 + n % 5 * 10}" else "${50 + n * 37 % 900} m", l.viewId, l.group)
        } else l
    })
}
