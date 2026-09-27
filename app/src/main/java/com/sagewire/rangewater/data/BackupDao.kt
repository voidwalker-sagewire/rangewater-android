package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Exact-ID import/export boundary for a complete RangeWater data set. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM water_points ORDER BY id")
    suspend fun allWaterPoints(): List<WaterPointEntity>

    @Query("SELECT * FROM pastures ORDER BY id")
    suspend fun allPastures(): List<PastureEntity>

    @Query("SELECT * FROM fence_junctions ORDER BY id")
    suspend fun allJunctions(): List<FenceJunctionEntity>

    @Query("SELECT * FROM pasture_vertices ORDER BY pastureId, sequence")
    suspend fun allVertices(): List<PastureVertexEntity>

    @Query("SELECT * FROM water_pasture_assignments ORDER BY waterPointId, pastureId")
    suspend fun allAssignments(): List<WaterPastureAssignmentEntity>

    @Query("SELECT * FROM gates ORDER BY id")
    suspend fun allGates(): List<GateEntity>

    @Query("SELECT * FROM herds ORDER BY id")
    suspend fun allHerds(): List<HerdEntity>

    @Query("SELECT * FROM cattle_movements ORDER BY id")
    suspend fun allMovements(): List<CattleMovementEntity>

    @Query("SELECT * FROM grazing_circuits ORDER BY id")
    suspend fun allGrazingCircuits(): List<GrazingCircuitEntity>

    @Query("SELECT * FROM grazing_circuit_pastures ORDER BY circuitId, sequence")
    suspend fun allCircuitPastures(): List<GrazingCircuitPastureEntity>

    @Query("SELECT * FROM grazing_circuit_pasture_roles ORDER BY circuitId, pastureId, role")
    suspend fun allCircuitPastureRoles(): List<GrazingCircuitPastureRoleEntity>

    @Query("SELECT * FROM herd_grazing_circuit_assignments ORDER BY herdId")
    suspend fun allHerdCircuitAssignments(): List<HerdGrazingCircuitAssignmentEntity>

    @Query("SELECT * FROM pasture_forage_observations ORDER BY id")
    suspend fun allPastureForageObservations(): List<PastureForageObservationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWaterPoints(rows: List<WaterPointEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPastures(rows: List<PastureEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJunctions(rows: List<FenceJunctionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVertices(rows: List<PastureVertexEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssignments(rows: List<WaterPastureAssignmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGates(rows: List<GateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHerds(rows: List<HerdEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovements(rows: List<CattleMovementEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGrazingCircuits(rows: List<GrazingCircuitEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCircuitPastures(rows: List<GrazingCircuitPastureEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCircuitPastureRoles(rows: List<GrazingCircuitPastureRoleEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHerdCircuitAssignments(rows: List<HerdGrazingCircuitAssignmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPastureForageObservations(rows: List<PastureForageObservationEntity>)

    @Query("DELETE FROM pasture_forage_observations")
    suspend fun deletePastureForageObservations()

    @Query("DELETE FROM grazing_circuit_pasture_roles")
    suspend fun deleteCircuitPastureRoles()

    @Query("DELETE FROM herd_grazing_circuit_assignments")
    suspend fun deleteHerdCircuitAssignments()

    @Query("DELETE FROM grazing_circuit_pastures")
    suspend fun deleteCircuitPastures()

    @Query("DELETE FROM grazing_circuits")
    suspend fun deleteGrazingCircuits()

    @Query("DELETE FROM cattle_movements")
    suspend fun deleteMovements()

    @Query("DELETE FROM water_pasture_assignments")
    suspend fun deleteAssignments()

    @Query("DELETE FROM herds")
    suspend fun deleteHerds()

    @Query("DELETE FROM gates")
    suspend fun deleteGates()

    @Query("DELETE FROM pasture_vertices")
    suspend fun deleteVertices()

    @Query("DELETE FROM water_points")
    suspend fun deleteWaterPoints()

    @Query("DELETE FROM pastures")
    suspend fun deletePastures()

    @Query("DELETE FROM fence_junctions")
    suspend fun deleteJunctions()

    @Transaction
    suspend fun snapshot(): RangeWaterBackupData = RangeWaterBackupData(
        waterPoints = allWaterPoints(),
        pastures = allPastures(),
        junctions = allJunctions(),
        vertices = allVertices(),
        assignments = allAssignments(),
        gates = allGates(),
        herds = allHerds(),
        movements = allMovements(),
        grazingCircuits = allGrazingCircuits(),
        circuitPastures = allCircuitPastures(),
        circuitPastureRoles = allCircuitPastureRoles(),
        herdCircuitAssignments = allHerdCircuitAssignments(),
        pastureForageObservations = allPastureForageObservations()
    )

    /** Delete order and insert order deliberately follow the foreign-key graph. */
    @Transaction
    suspend fun replaceAll(data: RangeWaterBackupData) {
        deletePastureForageObservations()
        deleteCircuitPastureRoles()
        deleteHerdCircuitAssignments()
        deleteCircuitPastures()
        deleteGrazingCircuits()
        deleteMovements()
        deleteAssignments()
        deleteHerds()
        deleteGates()
        deleteVertices()
        deleteWaterPoints()
        deletePastures()
        deleteJunctions()

        insertWaterPoints(data.waterPoints)
        insertPastures(data.pastures)
        insertJunctions(data.junctions)
        insertVertices(data.vertices)
        insertAssignments(data.assignments)
        insertGates(data.gates)
        insertHerds(data.herds)
        insertMovements(data.movements)
        insertGrazingCircuits(data.grazingCircuits)
        insertCircuitPastures(data.circuitPastures)
        insertCircuitPastureRoles(data.circuitPastureRoles)
        insertHerdCircuitAssignments(data.herdCircuitAssignments)
        insertPastureForageObservations(data.pastureForageObservations)
    }
}
