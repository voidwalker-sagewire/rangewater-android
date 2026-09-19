package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterPastureAssignmentDao {
    @Query("SELECT * FROM water_pasture_assignments ORDER BY waterPointId, pastureId")
    fun observeAll(): Flow<List<WaterPastureAssignmentEntity>>

    @Query(
        "SELECT pastureId FROM water_pasture_assignments " +
            "WHERE waterPointId = :waterPointId ORDER BY pastureId"
    )
    suspend fun getPastureIdsForWaterPoint(waterPointId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(assignment: WaterPastureAssignmentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(assignments: List<WaterPastureAssignmentEntity>)

    @Query("DELETE FROM water_pasture_assignments WHERE waterPointId = :waterPointId")
    suspend fun deleteForWaterPoint(waterPointId: Long)

    @Transaction
    suspend fun replaceForWaterPoint(
        waterPointId: Long,
        pastureIds: Collection<Long>,
        assignedAt: Long = System.currentTimeMillis()
    ) {
        deleteForWaterPoint(waterPointId)
        val replacements = pastureIds.distinct().map { pastureId ->
            WaterPastureAssignmentEntity(waterPointId, pastureId, assignedAt)
        }
        if (replacements.isNotEmpty()) insertAll(replacements)
    }
}
