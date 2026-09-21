package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HerdDao {
    @Query("SELECT * FROM herds WHERE archivedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveHerds(): Flow<List<HerdEntity>>

    @Query("SELECT * FROM herds ORDER BY name COLLATE NOCASE ASC")
    fun observeAllHerds(): Flow<List<HerdEntity>>

    @Query("SELECT * FROM herds WHERE id = :id")
    suspend fun getById(id: Long): HerdEntity?

    @Query("SELECT COUNT(*) FROM herds WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countByName(name: String, excludeId: Long = 0): Int

    @Query("SELECT COUNT(*) FROM cattle_movements WHERE herdId = :herdId AND status = 'PLANNED'")
    suspend fun countPlannedMovementsForHerd(herdId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(herd: HerdEntity): Long

    @Update
    suspend fun updateRaw(herd: HerdEntity)

    @Transaction
    suspend fun createHerd(herd: HerdEntity): Long {
        val trimmedName = herd.name.trim()
        if (countByName(trimmedName) > 0) throw IllegalArgumentException("A herd named '$trimmedName' already exists")
        val now = System.currentTimeMillis()
        return insertRaw(herd.copy(name = trimmedName, createdAt = now, updatedAt = now))
    }

    @Transaction
    suspend fun updateHerdDetails(
        id: Long,
        name: String,
        quantity: Int,
        countUnit: CountUnit,
        stockClass: StockClass,
        averageWeightLbs: Double?,
        markerColorHex: String,
        shortMarkerLabel: String?,
        notes: String
    ) {
        val existing = getById(id) ?: throw IllegalArgumentException("Herd #$id not found")
        val trimmedName = name.trim()
        if (countByName(trimmedName, id) > 0) throw IllegalArgumentException("A herd named '$trimmedName' already exists")
        updateRaw(
            existing.copy(
                name = trimmedName,
                quantity = quantity,
                countUnit = countUnit,
                stockClass = stockClass,
                averageWeightLbs = averageWeightLbs,
                markerColorHex = markerColorHex,
                shortMarkerLabel = shortMarkerLabel?.trim()?.ifBlank { null },
                notes = notes.trim(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    @Transaction
    suspend fun archiveHerdWithChecks(id: Long, timestamp: Long = System.currentTimeMillis()) {
        val existing = getById(id) ?: throw IllegalArgumentException("Herd #$id not found")
        if (countPlannedMovementsForHerd(id) > 0) {
            throw IllegalStateException("Cannot archive herd: It has active planned movements. Cancel or complete them first.")
        }
        updateRaw(
            existing.copy(
                locationKind = HerdLocationKind.UNKNOWN,
                currentPastureId = null,
                archivedAt = timestamp,
                updatedAt = timestamp
            )
        )
    }
}
