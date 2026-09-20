package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FenceJunctionDao {
    @Query("SELECT * FROM fence_junctions ORDER BY id")
    fun observeAll(): Flow<List<FenceJunctionEntity>>

    @Query("SELECT * FROM fence_junctions ORDER BY id")
    suspend fun getAll(): List<FenceJunctionEntity>

    @Query("SELECT * FROM fence_junctions WHERE id = :id")
    suspend fun getById(id: Long): FenceJunctionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(junction: FenceJunctionEntity): Long

    @Query("DELETE FROM fence_junctions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(DISTINCT pastureId) FROM pasture_vertices WHERE junctionId = :junctionId")
    suspend fun pastureCount(junctionId: Long): Int

    @Query("SELECT DISTINCT pastureId FROM pasture_vertices WHERE junctionId = :junctionId")
    suspend fun pastureIds(junctionId: Long): List<Long>
}
