package com.innexar.meuganho.sim

import com.innexar.meuganho.capture.core.ScreenSnapshot
import com.innexar.meuganho.domain.model.Platform
import kotlin.random.Random

/** Gabarito: o que realmente aconteceu no turno simulado. */
data class TruthOffer(val platform: Platform, val cents: Long, val shownAt: Long, var accepted: Boolean = false, val inRadar: Boolean = false)

data class SimTrip(
    val platform: Platform,
    val offer: SimOffer,
    val acceptedAt: Long,
    var endedAt: Long = 0,
    var cancelled: Boolean = false,
    var finalCents: Long = offer.cents,
    var surgeOrTip: Boolean = false,
)

/** Um instante do turno: uma tela lida, ou um toque de oferta da 99. */
sealed interface SimEvent {
    val at: Long
    data class Screen(override val at: Long, val snapshot: ScreenSnapshot) : SimEvent
    data class Sound(override val at: Long) : SimEvent
}

data class GeneratedShift(val seed: Int, val events: List<SimEvent>, val offers: List<TruthOffer>, val trips: List<SimTrip>, val endAt: Long)

/**
 * Motorista com Uber e 99 ligados ao mesmo tempo. Parâmetros tirados dos turnos reais:
 * ~1 oferta do Uber a cada 1,5 min e da 99 a cada 2 min, Radar às vezes com 2–3 cartões,
 * ofertas das duas chegando juntas, telas de fase que nem sempre aparecem, leitura por imagem com ruído.
 */
class ShiftGenerator(
    private val seed: Int,
    private val start: Long,
    private val durationMs: Long = 4 * 3_600_000L,
    /** Chance de uma tela de fase (não oferta) não ser capturada. */
    private val dropScreen: Int = 10,
    private val ocrNoise: Boolean = true,
) {
    private val rnd = Random(seed)
    private val f = ScreenFactory(rnd)
    private val events = mutableListOf<SimEvent>()
    private val offers = mutableListOf<TruthOffer>()
    private val trips = mutableListOf<SimTrip>()
    private var uberToday = rnd.nextLong(0, 6_000)
    private var nnToday = rnd.nextLong(0, 6_000)
    private var tick = 0
    private val backToSearch = mutableListOf<Triple<Platform, Long, ScreenSnapshot>>()

    private fun screen(at: Long, s: ScreenSnapshot, mayDrop: Boolean = true) {
        if (mayDrop && rnd.nextInt(100) < dropScreen) return
        events += SimEvent.Screen(at, s.copy(capturedAt = at))
    }

    private fun exp(meanMs: Long) = (-kotlin.math.ln(1 - rnd.nextDouble()) * meanMs).toLong().coerceAtLeast(5_000)

    fun generate(): GeneratedShift {
        val end = start + durationMs
        screen(start, f.uberHome(uberToday), mayDrop = false)
        screen(start + 2_000, f.nnEarnings(nnToday, ocrNoise), mayDrop = false)
        var t = start + 10_000
        var nextUber = t + exp(90_000)
        var nextNn = t + exp(120_000)
        var busyUntil = 0L
        var busyOn: Platform? = null
        while (t < end) {
            val isUber = nextUber <= nextNn
            t = if (isUber) nextUber else nextNn
            if (t >= end) break
            val busy = t < busyUntil
            val platform = if (isUber) Platform.UBER else Platform.NINETY_NINE
            // Em corrida do Uber, o Uber não manda ofertas novas; a 99 manda sempre (cartão flutuante),
            // e o Uber continua mandando durante uma corrida da 99.
            if (!(isUber && busy && busyOn == Platform.UBER)) {
                val radar = isUber && !busy && rnd.nextInt(100) < 15
                val shown = if (radar) List(rnd.nextInt(2, 4)) { f.randomOffer() } else listOf(f.randomOffer())
                val truth = shown.map { TruthOffer(platform, it.cents, t, inRadar = radar) }
                offers += truth
                if (isUber) {
                    screen(t, if (radar) f.uberRadar(shown) else f.uberOffer(shown.single()), mayDrop = false)
                } else {
                    events += SimEvent.Sound(t - 300)
                    screen(t, f.nnOffer(shown.single(), ocrNoise), mayDrop = false)
                    // Vigia da 99: o cartão continua na tela e é lido de novo (mesma oferta, ruído diferente).
                    screen(t + 1_400, f.nnOffer(shown.single(), ocrNoise).let { it.copy(lines = it.lines) }, mayDrop = true)
                }
                // Decide: aceita as boas quando está livre.
                val pick = shown.indices.maxByOrNull { shown[it].cents * 1000 / shown[it].totalMeters }!!
                val o = shown[pick]
                val good = o.cents * 1000 / o.totalMeters >= 150
                val accept = !busy && rnd.nextInt(100) < (if (good) 60 else 12)
                if (accept) {
                    truth[pick].accepted = true
                    val acceptAt = t + rnd.nextLong(3_000, 9_000)
                    busyUntil = runTrip(platform, o, acceptAt)
                    busyOn = platform
                    // A plataforma não manda outra oferta enquanto o motorista aceita esta.
                    if (isUber) nextUber = maxOf(nextUber, acceptAt + 10_000) else nextNn = maxOf(nextNn, acceptAt + 10_000)
                } else {
                    // Deixou passar: a oferta some e o app volta à busca.
                    val back = t + rnd.nextLong(12_000, 20_000)
                    // O cartão some e o app volta à busca (fora de corrida). Fica pendente: se o motorista aceitar
                    // outra oferta antes disso, essa tela não existe (filtrado no fim).
                    if (isUber) {
                        if (!(busy && busyOn == Platform.UBER)) backToSearch += Triple(Platform.UBER, back, if (rnd.nextInt(100) < 30) f.uberOnlineNav(tick++) else f.uberHome(uberToday))
                    }
                    else if (!(busy && busyOn == Platform.NINETY_NINE) && rnd.nextBoolean()) backToSearch += Triple(Platform.NINETY_NINE, back, f.nnIdle(tick++))
                }
            }
            if (isUber) nextUber = maxOf(nextUber, t + exp(90_000)) else nextNn = maxOf(nextNn, t + exp(120_000))
        }
        // Fim do turno: o motorista abre o histórico do Uber (às vezes) e a Central de ganhos da 99.
        val closeAt = maxOf(end, busyUntil) + 60_000
        val uberDone = trips.filter { it.platform == Platform.UBER && !it.cancelled }
        if (uberDone.isNotEmpty() && rnd.nextInt(100) < 70) {
            screen(closeAt, f.uberHistory(uberDone.map { it.acceptedAt to it }), mayDrop = false)
        }
        screen(closeAt + 30_000, f.nnEarnings(nnToday, ocrNoise), mayDrop = false)
        // Às vezes confere também o extrato da Conta99 (fonte oficial da 99).
        val nnDone = trips.filter { it.platform == Platform.NINETY_NINE && !it.cancelled }
        if (nnDone.isNotEmpty() && rnd.nextInt(100) < 60) screen(closeAt + 50_000, f.nnWallet(nnDone), mayDrop = false)
        // Tela de busca de uma oferta recusada só aparece se o motorista não estiver em corrida daquele app.
        for ((p, at, snap) in backToSearch) {
            if (trips.none { it.platform == p && at >= it.acceptedAt - 1_000 && at <= it.endedAt }) screen(at, snap)
        }
        events.sortBy { it.at }
        return GeneratedShift(seed, events.toList(), offers.toList(), trips.toList(), closeAt + 60_000)
    }

    /** Uma corrida completa; devolve quando o motorista fica livre de novo. */
    private fun runTrip(p: Platform, o: SimOffer, acceptAt: Long): Long {
        val trip = SimTrip(p, o, acceptAt)
        trips += trip
        var t = acceptAt
        val pickupMs = (o.pickupSeconds * 1000L * rnd.nextDouble(0.8, 1.4)).toLong()
        val cancel = rnd.nextInt(100) < 8
        // A 99 usa duas navegações (real): mapa com velocidade ou "navegação leve".
        val light = rnd.nextBoolean()
        // Parado no sinal ~20% das vezes (velocidade < 8 km/h).
        fun nnNav() = if (light) f.nnLightNav(tick++) else f.nnNavMap(if (rnd.nextInt(100) < 20) rnd.nextInt(0, 8) else rnd.nextInt(15, 70), tick++)

        if (p == Platform.NINETY_NINE && rnd.nextBoolean()) screen(t + 800, f.nnAcceptedToast())
        // Indo buscar: telas a cada 20–50 s.
        val pickupEnd = t + pickupMs
        var first = true
        while (t < pickupEnd) {
            if (p == Platform.UBER) screen(t, f.uberToPickup(labeled = rnd.nextInt(100) < 40, tick++), mayDrop = !first)
            else {
                screen(t, nnNav(), mayDrop = !first)
                if (rnd.nextInt(100) < 30) screen(t + 700, f.nnToPickup(ocrNoise))
            }
            first = false
            t += rnd.nextLong(10_000, 25_000) // navegação: eventos contínuos
        }
        if (cancel) {
            t = pickupEnd + rnd.nextLong(10_000, 60_000)
            screen(t, if (p == Platform.UBER) f.uberCancelled() else f.nnCancelled(), mayDrop = false)
            screen(t + 5_000, if (p == Platform.UBER) f.uberHome(uberToday) else f.nnIdle(tick++))
            trip.cancelled = true
            trip.endedAt = t
            return t + 10_000
        }
        // Esperando o passageiro.
        t = pickupEnd
        val waitEnd = t + rnd.nextLong(40_000, 180_000)
        while (t < waitEnd) {
            screen(t, if (p == Platform.UBER) f.uberWaiting(tick++) else f.nnWaiting(ocrNoise))
            t += rnd.nextLong(25_000, 60_000)
        }
        // Em viagem.
        t = waitEnd
        val tripEnd = t + (o.tripSeconds * 1000L * rnd.nextDouble(0.85, 1.3)).toLong()
        val labeled = rnd.nextInt(100) < 40
        while (t < tripEnd) {
            if (p == Platform.UBER) screen(t, f.uberInTrip(labeled, tick++))
            else {
                screen(t, nnNav())
                if (rnd.nextInt(100) < 30) screen(t + 700, f.nnInTrip(ocrNoise))
            }
            t += rnd.nextLong(10_000, 25_000) // navegação: eventos contínuos
        }
        // Fim: às vezes dinâmica/gorjeta a mais que a oferta.
        if (rnd.nextInt(100) < 10) {
            trip.finalCents = o.cents + rnd.nextLong(100, 400)
            trip.surgeOrTip = true
        }
        trip.endedAt = tripEnd
        t = tripEnd
        if (p == Platform.UBER) {
            if (o.cash) { screen(t, f.uberCollectCash(trip.finalCents + rnd.nextLong(0, 500))); t += 20_000 }
            uberToday += trip.finalCents
            // A tela inicial com o total do dia costuma aparecer logo; às vezes só bem depois.
            screen(t + rnd.nextLong(3_000, 15_000), f.uberHome(uberToday))
        } else {
            nnToday += trip.finalCents
            if (o.cash) screen(t - 30_000, f.nnCashTripEnd(ocrNoise))
            if (rnd.nextInt(100) < 60) screen(t + 1_500, f.nnRating(ocrNoise))
            // A tela inicial mostra o ganho do dia (lida 2x pelo vigia) na maioria das vezes.
            if (rnd.nextInt(100) < 70) {
                val back = t + rnd.nextLong(20_000, 60_000)
                screen(back, f.nnHome(nnToday, ocrNoise)); screen(back + 5_000, f.nnHome(nnToday, ocrNoise))
            }
            // Real (30/09/2026): muitas vezes a 99 não mostra NADA legível no fim (só some a navegação).
            if (rnd.nextBoolean()) screen(t + rnd.nextLong(3_000, 10_000), f.nnIdle(tick++))
            // Metade das vezes o motorista confere a Central de ganhos logo após a corrida.
            if (rnd.nextBoolean()) screen(t + rnd.nextLong(15_000, 40_000), f.nnEarnings(nnToday, ocrNoise))
        }
        return t + 20_000
    }
}
