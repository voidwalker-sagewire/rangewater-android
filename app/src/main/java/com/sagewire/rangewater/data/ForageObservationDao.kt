package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ForageObservationDao {
    @Query("SELECT * FROM pasture_forage_observations ORDER BY observedAt DESC, id DESC")
    fun observeAll(): Flow<List<PastureForageObservationEntity>>

    @Query("SELECT * FROM pasture_forage_observations WHERE pastureId = :pastureId ORDER BY observedAt DESC, id DESC")
    fun observeForPasture(pastureId: Long): Flow<List<PastureForageObservationEntity>>

    @Query("SELECT * FROM pasture_forage_observations WHERE pastureId = :pastureId ORDER BY observedAt DESC, id DESC LIMIT 1")
    suspend fun latestForPasture(pastureId: Long): PastureForageObservationEntity?

    @Query("SELECT COUNT(*) FROM pastures WHERE id = :pastureId")
    suspend fun countPasture(pastureId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(observation: PastureForageObservationEntity): Long

    @Transaction
    suspend fun record(observation: PastureForageObservationEntity): Long {
        require(countPasture(observation.pastureId) == 1) { "Pasture #${observation.pastureId} not found" }
        if (observation.calibrationSource == ForageCalibrationSource.OHIO_NRCS_GLCI_GRAZING_STICK) {
            val expected = ForageCalculator.ohioCalibration(observation.forageStandType, observation.standCondition)
            require(observation.dmPerAcreInchLow == expected.low && observation.dmPerAcreInchHigh == expected.high) {
                "Ohio grazing-stick calibration does not match the selected stand and condition"
            }
        }
        return insertRaw(observation.copy(notes = observation.notes.trim()))
    }
}

