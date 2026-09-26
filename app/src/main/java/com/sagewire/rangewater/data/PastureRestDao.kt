package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PastureRestDao {
    @Query("SELECT COUNT(*) FROM herds WHERE archivedAt IS NULL AND locationKind = 'PASTURE' AND currentPastureId = :pastureId")
    suspend fun activeHerdCount(pastureId: Long): Int

    @Query(
        """
        SELECT * FROM cattle_movements
        WHERE status = 'COMPLETED' AND originPastureId = :pastureId AND completedAt IS NOT NULL
        ORDER BY completedAt DESC, id DESC
        """
    )
    suspend fun completedDepartures(pastureId: Long): List<CattleMovementEntity>

    @Transaction
    suspend fun status(
        pastureId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): PastureRestStatus = RestClockCalculator.derive(
        activeHerdCount = activeHerdCount(pastureId),
        completedDepartures = completedDepartures(pastureId),
        nowMillis = nowMillis
    )
}
