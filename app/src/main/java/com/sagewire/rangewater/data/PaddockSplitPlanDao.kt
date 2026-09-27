package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sagewire.rangewater.spatial.PaddockBoundaryAnchor
import com.sagewire.rangewater.spatial.PaddockSplitEngine
import kotlinx.coroutines.flow.Flow

@Dao
interface PaddockSplitPlanDao {
    @Query("SELECT * FROM paddock_split_plans ORDER BY createdAt, id")
    fun observeAll(): Flow<List<PaddockSplitPlanEntity>>

    @Query("SELECT * FROM paddock_split_plans WHERE archivedAt IS NULL ORDER BY createdAt, id")
    fun observeActive(): Flow<List<PaddockSplitPlanEntity>>

    @Query("SELECT * FROM paddock_split_plans WHERE id = :id")
    suspend fun getById(id: Long): PaddockSplitPlanEntity?

    @Query("SELECT * FROM paddock_split_plans WHERE pastureId = :pastureId ORDER BY createdAt, id")
    suspend fun plansForPasture(pastureId: Long): List<PaddockSplitPlanEntity>

    @Query("SELECT * FROM paddock_split_plans WHERE pastureId = :pastureId AND archivedAt IS NULL LIMIT 1")
    suspend fun activeForPasture(pastureId: Long): PaddockSplitPlanEntity?

    @Transaction
    @Query("SELECT * FROM pastures WHERE id = :pastureId LIMIT 1")
    suspend fun pastureWithVertices(pastureId: Long): PastureWithVertices?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(plan: PaddockSplitPlanEntity): Long

    @Update
    suspend fun updateRaw(plan: PaddockSplitPlanEntity)

    @Delete
    suspend fun deleteRaw(plan: PaddockSplitPlanEntity)

    @Query("UPDATE paddock_split_plans SET archivedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun archiveRaw(id: Long, now: Long)

    @Query("UPDATE paddock_split_plans SET archivedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun reactivateRaw(id: Long, now: Long)

    @Transaction
    suspend fun create(
        pastureId: Long,
        name: String,
        sideALabel: String,
        sideBLabel: String,
        start: PaddockBoundaryAnchor,
        end: PaddockBoundaryAnchor,
        now: Long = System.currentTimeMillis()
    ): Long {
        check(activeForPasture(pastureId) == null) {
            "This pasture already has an active paddock split"
        }
        val plan = PaddockSplitPlanEntity(
            pastureId = pastureId,
            name = normalizedLabel(name, "Paddock Split"),
            sideALabel = normalizedLabel(sideALabel, "Paddock A"),
            sideBLabel = normalizedLabel(sideBLabel, "Paddock B"),
            startJunctionAId = start.junctionAId,
            startJunctionBId = start.junctionBId,
            startSegmentRatio = start.segmentRatio,
            endJunctionAId = end.junctionAId,
            endJunctionBId = end.junctionBId,
            endSegmentRatio = end.segmentRatio,
            createdAt = now,
            updatedAt = now
        )
        validatePlan(plan)
        return insertRaw(plan)
    }

    @Transaction
    suspend fun update(
        id: Long,
        name: String,
        sideALabel: String,
        sideBLabel: String,
        start: PaddockBoundaryAnchor,
        end: PaddockBoundaryAnchor,
        now: Long = System.currentTimeMillis()
    ) {
        val existing = getById(id) ?: throw IllegalArgumentException("Paddock split #$id not found")
        check(existing.archivedAt == null) { "Archived paddock splits cannot be edited" }
        val updated = existing.copy(
            name = normalizedLabel(name, "Paddock Split"),
            sideALabel = normalizedLabel(sideALabel, "Paddock A"),
            sideBLabel = normalizedLabel(sideBLabel, "Paddock B"),
            startJunctionAId = start.junctionAId,
            startJunctionBId = start.junctionBId,
            startSegmentRatio = start.segmentRatio,
            endJunctionAId = end.junctionAId,
            endJunctionBId = end.junctionBId,
            endSegmentRatio = end.segmentRatio,
            updatedAt = now
        )
        validatePlan(updated)
        updateRaw(updated)
    }

    @Transaction
    suspend fun archive(id: Long, now: Long = System.currentTimeMillis()) {
        val existing = getById(id) ?: throw IllegalArgumentException("Paddock split #$id not found")
        check(existing.archivedAt == null) { "Paddock split is already archived" }
        archiveRaw(id, now)
    }

    @Transaction
    suspend fun reactivate(id: Long, now: Long = System.currentTimeMillis()) {
        val existing = getById(id) ?: throw IllegalArgumentException("Paddock split #$id not found")
        check(existing.archivedAt != null) { "Paddock split is already active" }
        check(activeForPasture(existing.pastureId) == null) {
            "This pasture already has an active paddock split"
        }
        validatePlan(existing.copy(archivedAt = null, updatedAt = now))
        reactivateRaw(id, now)
    }

    @Transaction
    suspend fun delete(id: Long) {
        val existing = getById(id) ?: throw IllegalArgumentException("Paddock split #$id not found")
        deleteRaw(existing)
    }

    private suspend fun validatePlan(plan: PaddockSplitPlanEntity) {
        val pasture = pastureWithVertices(plan.pastureId)
            ?: throw IllegalArgumentException("Pasture #${plan.pastureId} not found")
        PaddockSplitEngine.resolve(plan, pasture)
    }

    private fun normalizedLabel(value: String, fallback: String): String =
        value.trim().ifBlank { fallback }
}
