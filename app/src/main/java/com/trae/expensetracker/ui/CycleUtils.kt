package com.trae.expensetracker.ui

import java.time.LocalDate
import java.time.ZoneId

/**
 * Budget-cycle date maths, shared by Dashboard, Transactions, Reports and budget alerts so
 * every screen agrees on what "this cycle" means.
 */
object CycleUtils {

    /** Inclusive start date and exclusive end-of-day for the cycle containing [today]. */
    fun cycleRange(today: LocalDate, startDay: Int): Pair<LocalDate, LocalDate> {
        val safeStart = startDay.coerceIn(1, 28)
        val candidateStartThisMonth = today.withDayOfMonth(safeStart)
        val start = if (today.dayOfMonth >= safeStart) candidateStartThisMonth else candidateStartThisMonth.minusMonths(1)
        val end = start.plusMonths(1).minusDays(1)
        return start to end
    }

    /** Same range expressed as epoch millis, with an exclusive upper bound. */
    fun cycleRangeMillis(
        today: LocalDate,
        startDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Triple<Long, Long, LocalDate> {
        val (start, end) = cycleRange(today, startDay)
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toExclusive = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return Triple(from, toExclusive, start)
    }

    /** Days remaining in the cycle, counting today. */
    fun daysLeft(today: LocalDate, end: LocalDate): Int =
        (end.toEpochDay() - today.toEpochDay() + 1).coerceAtLeast(0).toInt()
}
