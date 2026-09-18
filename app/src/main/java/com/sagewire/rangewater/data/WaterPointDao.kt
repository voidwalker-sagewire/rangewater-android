package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * 🪨 BLOCK 1 — WATER ASSET DATA ACCESS
 * Purpose: Supplies atomic local writes and reactive ordered reads.
 * 🪨 Protected: Default names use the generated row ID, never the current list size.
 */
@Dao
interface WaterPointDao {
    @Query("SELECT * FROM water_points ORDER BY id ASC")
    fun observeAll(): Flow<List<WaterPointEntity>>

    @Query("SELECT * FROM water_points WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WaterPointEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(point: WaterPointEntity): Long

    @Query("UPDATE water_points SET name = :name WHERE id = :id")
    suspend fun setGeneratedName(id: Long, name: String)

    @Transaction
    suspend fun insertWithDefaultName(
        latitude: Double,
        longitude: Double,
        now: Long = System.currentTimeMillis()
    ): Long {
        val id = insert(
            WaterPointEntity(
                latitude = latitude,
                longitude = longitude,
                name = "",
                createdAt = now,
                updatedAt = now
            )
        )
        setGeneratedName(id, "Water Point $id")
        return id
    }

    @Update
    suspend fun update(point: WaterPointEntity)

    @Query("DELETE FROM water_points WHERE id = :id")
    suspend fun deleteById(id: Long)
}
