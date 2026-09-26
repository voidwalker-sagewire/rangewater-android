package com.sagewire.rangewater.data

import java.util.Calendar
import java.util.TimeZone

enum class PastureRestState { OCCUPIED, RESTING, NO_RECORDED_DEPARTURE }

data class PastureRestStatus(
    val state: PastureRestState,
    val daysSinceRecordedDeparture: Int? = null,
    val restStartedAt: Long? = null,
    val controllingMovementId: Long? = null
)

object RestClockCalculator {
    fun derive(
        activeHerdCount: Int,
        completedDepartures: List<CattleMovementEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): PastureRestStatus {
        require(activeHerdCount >= 0)
        if (activeHerdCount > 0) return PastureRestStatus(PastureRestState.OCCUPIED)

        val latest = completedDepartures
            .asSequence()
            .filter { it.status == MovementStatus.COMPLETED && it.completedAt != null }
            .maxByOrNull { requireNotNull(it.completedAt) }
            ?: return PastureRestStatus(PastureRestState.NO_RECORDED_DEPARTURE)
        val startedAt = requireNotNull(latest.completedAt)
        require(startedAt <= nowMillis) { "Recorded departure cannot be in the future" }

        return PastureRestStatus(
            state = PastureRestState.RESTING,
            daysSinceRecordedDeparture = localCalendarDaysBetween(startedAt, nowMillis, timeZone),
            restStartedAt = startedAt,
            controllingMovementId = latest.id
        )
    }

    private fun localCalendarDaysBetween(start: Long, end: Long, timeZone: TimeZone): Int {
        val startDay = startOfDay(start, timeZone)
        val endDay = startOfDay(end, timeZone)
        var days = 0
        while (startDay.before(endDay)) {
            startDay.add(Calendar.DAY_OF_YEAR, 1)
            days++
        }
        return days
    }

    private fun startOfDay(value: Long, timeZone: TimeZone): Calendar =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = value
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
