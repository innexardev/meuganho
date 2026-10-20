package com.innexar.meuganho.sim

import com.innexar.meuganho.data.local.entity.OfferEntity
import com.innexar.meuganho.data.local.entity.TripEntity
import com.innexar.meuganho.domain.model.OfferOutcome
import com.innexar.meuganho.domain.model.Platform
import com.innexar.meuganho.domain.model.TripState
import com.innexar.meuganho.domain.model.ValueSource
import kotlin.math.abs

/** Placar de um turno (ou soma de vários), por plataforma. */
data class Score(
    var offersShown: Int = 0,
    var offersRead: Int = 0,
    var acceptsTrue: Int = 0,
    var acceptsLinked: Int = 0,
    var outcomesRight: Int = 0,
    var tripsTrue: Int = 0,
    var tripsFound: Int = 0,
    var stateRight: Int = 0,
    var completedTrue: Int = 0,
    var valueExact: Int = 0,
    var valueOfficial: Int = 0,
    var falseTrips: Int = 0,
    var grossTrue: Long = 0,
    var grossApp: Long = 0,
) {
    operator fun plusAssign(o: Score) {
        offersShown += o.offersShown; offersRead += o.offersRead; acceptsTrue += o.acceptsTrue
        acceptsLinked += o.acceptsLinked; outcomesRight += o.outcomesRight; tripsTrue += o.tripsTrue
        tripsFound += o.tripsFound; stateRight += o.stateRight; completedTrue += o.completedTrue
        valueExact += o.valueExact; valueOfficial += o.valueOfficial; falseTrips += o.falseTrips
        grossTrue += o.grossTrue; grossApp += o.grossApp
    }
}

object Scorer {
    private val official = setOf(ValueSource.PLATFORM_HISTORY, ValueSource.PLATFORM_TOTAL, ValueSource.COMPLETION_SCREEN)

    /** [problems] recebe uma linha por divergência (para depurar). */
    fun score(shift: GeneratedShift, dbOffers: List<OfferEntity>, dbTrips: List<TripEntity>, problems: MutableList<String>): Map<Platform, Score> =
        Platform.entries.associateWith { p ->
            val s = Score()
            val offers = dbOffers.filter { it.platform == p }
            val trips = dbTrips.filter { it.platform == p }
            val usedOffers = mutableSetOf<Long>()
            val offerOf = HashMap<TruthOffer, OfferEntity>()
            for (t in shift.offers.filter { it.platform == p }) {
                s.offersShown++
                val o = offers.firstOrNull { it.id !in usedOffers && it.offeredCents == t.cents && abs(it.detectedAt - t.shownAt) <= 5_000 }
                if (o == null) {
                    problems += "seed ${shift.seed} ${p.label} ${clock(t.shownAt)}: oferta ${money(t.cents)} não lida"
                    continue
                }
                usedOffers += o.id; offerOf[t] = o; s.offersRead++
                val ok = (o.outcome == OfferOutcome.ACCEPTED) == t.accepted
                if (ok) s.outcomesRight++
                else problems += "seed ${shift.seed} ${p.label} ${clock(t.shownAt)}: oferta ${money(t.cents)} aceita=${t.accepted} mas app=${o.outcome}${if (t.inRadar) " (Radar)" else ""}"
                if (t.accepted) s.acceptsTrue++
            }
            val usedTrips = mutableSetOf<Long>()
            for (t in shift.trips.filter { it.platform == p }) {
                s.tripsTrue++
                if (!t.cancelled) { s.completedTrue++; s.grossTrue += t.finalCents }
                val d = trips.filter { it.id !in usedTrips && abs(it.acceptedAt - t.acceptedAt) <= 90_000 }.minByOrNull { abs(it.acceptedAt - t.acceptedAt) }
                if (d == null) {
                    problems += "seed ${shift.seed} ${p.label} ${clock(t.acceptedAt)}: corrida ${money(t.finalCents)} não registrada"
                    continue
                }
                usedTrips += d.id; s.tripsFound++
                val truthOffer = shift.offers.firstOrNull { it.platform == p && it.accepted && abs(it.shownAt - t.acceptedAt) < 15_000 }
                if (truthOffer != null && d.offerId != null && d.offerId == offerOf[truthOffer]?.id) s.acceptsLinked++
                val stateOk = if (t.cancelled) d.state == TripState.CANCELLED else d.state == TripState.COMPLETED
                if (stateOk) s.stateRight++
                else problems += "seed ${shift.seed} ${p.label} ${clock(t.acceptedAt)}: corrida ${if (t.cancelled) "cancelada" else "concluída"} mas app=${d.state}"
                if (!t.cancelled) {
                    if (d.effectiveCents == t.finalCents) s.valueExact++
                    else problems += "seed ${shift.seed} ${p.label} ${clock(t.acceptedAt)}: valor ${money(t.finalCents)} mas app=${d.effectiveCents?.let(::money)} (${d.valueSource})"
                    if (d.valueSource in official && d.effectiveCents == t.finalCents) s.valueOfficial++
                }
            }
            for (d in trips.filter { it.id !in usedTrips }) {
                s.falseTrips++
                problems += "seed ${shift.seed} ${p.label} ${clock(d.acceptedAt)}: corrida que não existiu (${d.state}, ${d.effectiveCents?.let(::money)}, ${d.valueSource})"
            }
            // Como o app soma: só concluídas com status que conta (revisão pendente fica de fora, "a confirmar").
            s.grossApp = trips.filter { it.state == TripState.COMPLETED && it.status.countsInStats }.sumOf { it.effectiveCents ?: 0 }
            s
        }

    fun money(c: Long) = "R$ ${c / 100},${"%02d".format(abs(c % 100))}"
    fun clock(at: Long): String = java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalTime().withNano(0).toString()
}
