package com.sagewire.rangewater.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class RestClockCalculatorTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun occupiedPasture_neverShowsARecordedRestDuration() {
        val result = RestClockCalculator.derive(
            activeHerdCount = 1,
            completedDepartures = listOf(departure(1, instant(2026, 9, 1, 8))),
            nowMillis = instant(2026, 9, 26, 18),
            timeZone = utc
        )

        assertEquals(PastureRestState.OCCUPIED, result.state)
        assertNull(result.daysSinceRecordedDeparture)
    }

    @Test
    fun emptyPasture_usesLatestCompletedDepartureAndLocalCalendarDays() {
        val result = RestClockCalculator.derive(
            activeHerdCount = 0,
            completedDepartures = listOf(
                departure(4, instant(2026, 9, 20, 23)),
                departure(9, instant(2026, 9, 22, 1))
            ),
            nowMillis = instant(2026, 9, 26, 0),
            timeZone = utc
        )

        assertEquals(PastureRestState.RESTING, result.state)
        assertEquals(4, result.daysSinceRecordedDeparture)
        assertEquals(9L, result.controllingMovementId)
    }

    @Test
    fun noCompletedDeparture_isExplicitlyUnknownRatherThanReady() {
        val result = RestClockCalculator.derive(0, emptyList(), timeZone = utc)

        assertEquals(PastureRestState.NO_RECORDED_DEPARTURE, result.state)
        assertNull(result.daysSinceRecordedDeparture)
    }

    @Test
    fun futureDeparture_isRejectedAsInvalidHistory() {
        try {
            RestClockCalculator.derive(
                activeHerdCount = 0,
                completedDepartures = listOf(departure(1, instant(2026, 9, 27, 0))),
                nowMillis = instant(2026, 9, 26, 0),
                timeZone = utc
            )
            fail("Expected future-history rejection")
        } catch (_: IllegalArgumentException) {
            // Invalid history must not silently produce a negative rest period.
        }
    }

    private fun departure(id: Long, completedAt: Long) = CattleMovementEntity(
        id = id,
        herdId = 1,
        originLocationKind = HerdLocationKind.PASTURE,
        originPastureId = 10,
        originNameSnapshot = "North",
        destinationLocationKind = HerdLocationKind.PASTURE,
        destinationPastureId = 11,
        destinationNameSnapshot = "South",
        quantity = 30,
        countUnit = CountUnit.HEAD,
        status = MovementStatus.COMPLETED,
        completedAt = completedAt
    )

    private fun instant(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis
}
