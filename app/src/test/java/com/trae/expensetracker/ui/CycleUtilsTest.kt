package com.trae.expensetracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CycleUtilsTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `cycle starts on the configured day when today is past it`() {
        val (start, end) = CycleUtils.cycleRange(LocalDate.of(2026, 9, 15), 10)

        assertEquals(LocalDate.of(2026, 9, 10), start)
        assertEquals(LocalDate.of(2026, 10, 9), end)
    }

    @Test
    fun `cycle falls back to the previous month when today is before the start day`() {
        val (start, end) = CycleUtils.cycleRange(LocalDate.of(2026, 9, 5), 10)

        assertEquals(LocalDate.of(2026, 8, 10), start)
        assertEquals(LocalDate.of(2026, 9, 9), end)
    }

    @Test
    fun `today equal to the start day begins a new cycle`() {
        val (start, _) = CycleUtils.cycleRange(LocalDate.of(2026, 9, 10), 10)

        assertEquals(LocalDate.of(2026, 9, 10), start)
    }

    @Test
    fun `cycle start day is clamped to a safe range`() {
        // 31 would not exist in every month, so it is clamped to 28.
        val (start, _) = CycleUtils.cycleRange(LocalDate.of(2026, 2, 28), 31)

        assertEquals(LocalDate.of(2026, 2, 28), start)
    }

    @Test
    fun `cycle range spans a year boundary correctly`() {
        val (start, end) = CycleUtils.cycleRange(LocalDate.of(2026, 12, 28), 26)

        assertEquals(LocalDate.of(2026, 12, 26), start)
        assertEquals(LocalDate.of(2027, 1, 25), end)
    }

    @Test
    fun `millis range covers the whole cycle`() {
        val (from, toExclusive, start) = CycleUtils.cycleRangeMillis(LocalDate.of(2026, 9, 15), 10, zone)

        assertEquals(LocalDate.of(2026, 9, 10), start)
        assertEquals(LocalDate.of(2026, 9, 10).atStartOfDay(zone).toInstant().toEpochMilli(), from)
        // Exclusive bound is midnight after the last day.
        assertEquals(LocalDate.of(2026, 10, 10).atStartOfDay(zone).toInstant().toEpochMilli(), toExclusive)
    }

    @Test
    fun `days left counts today as remaining`() {
        assertEquals(1, CycleUtils.daysLeft(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 9)))
        assertEquals(5, CycleUtils.daysLeft(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 9)))
    }

    @Test
    fun `days left never goes negative`() {
        assertEquals(0, CycleUtils.daysLeft(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 9)))
    }
}
