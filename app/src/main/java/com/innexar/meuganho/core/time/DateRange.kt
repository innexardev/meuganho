package com.innexar.meuganho.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Intervalo [startMillis, endMillis) em horário local. */
data class DateRange(val startMillis: Long, val endMillis: Long) {

    operator fun contains(millis: Long): Boolean = millis in startMillis until endMillis

    companion object {
        private val zone: ZoneId get() = ZoneId.systemDefault()

        fun day(date: LocalDate): DateRange = DateRange(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        fun days(from: LocalDate, toInclusive: LocalDate): DateRange = DateRange(
            from.atStartOfDay(zone).toInstant().toEpochMilli(),
            toInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        fun today(): DateRange = day(LocalDate.now(zone))

        // Semana começa na segunda-feira, como os repasses das plataformas.
        fun thisWeek(): DateRange {
            val start = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return days(start, start.plusDays(6))
        }

        fun thisMonth(): DateRange {
            val start = LocalDate.now(zone).withDayOfMonth(1)
            return days(start, start.with(TemporalAdjusters.lastDayOfMonth()))
        }

        fun lastDays(count: Int): DateRange {
            val today = LocalDate.now(zone)
            return days(today.minusDays(count - 1L), today)
        }
    }
}

object DateFormats {
    private val dayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val shortDayFormatter = DateTimeFormatter.ofPattern("EEE dd/MM", java.util.Locale.forLanguageTag("pt-BR"))
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun day(millis: Long): String = localDate(millis).format(dayFormatter)
    fun day(date: LocalDate): String = date.format(dayFormatter)
    fun shortDay(date: LocalDate): String = date.format(shortDayFormatter)
    fun time(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timeFormatter)

    fun epochDay(millis: Long): Long = localDate(millis).toEpochDay()

    /** "14:32" se for hoje, "ontem 14:32", senão "27/09 14:32". */
    fun relative(millis: Long): String {
        val date = localDate(millis)
        val today = LocalDate.now(ZoneId.systemDefault())
        return when (date) {
            today -> time(millis)
            today.minusDays(1) -> "ontem ${time(millis)}"
            else -> "${date.format(DateTimeFormatter.ofPattern("dd/MM"))} ${time(millis)}"
        }
    }
}
