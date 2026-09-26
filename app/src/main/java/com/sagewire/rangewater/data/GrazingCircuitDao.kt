package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GrazingCircuitDao {
    @Query("SELECT * FROM grazing_circuits WHERE archivedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActiveCircuits(): Flow<List<GrazingCircuitEntity>>

    @Query("SELECT * FROM grazing_circuits ORDER BY name COLLATE NOCASE")
    fun observeAllCircuits(): Flow<List<GrazingCircuitEntity>>

    @Query("SELECT * FROM grazing_circuit_pastures ORDER BY circuitId, sequence")
    fun observeAllMemberships(): Flow<List<GrazingCircuitPastureEntity>>

    @Query("SELECT * FROM grazing_circuit_pasture_roles ORDER BY circuitId, pastureId, role")
    fun observeAllRoles(): Flow<List<GrazingCircuitPastureRoleEntity>>

    @Query("SELECT * FROM herd_grazing_circuit_assignments ORDER BY herdId")
    fun observeAllHerdAssignments(): Flow<List<HerdGrazingCircuitAssignmentEntity>>

    @Query("SELECT * FROM grazing_circuits WHERE id = :id")
    suspend fun getCircuit(id: Long): GrazingCircuitEntity?

    @Query("SELECT * FROM grazing_circuit_pastures WHERE circuitId = :circuitId ORDER BY sequence")
    suspend fun membershipsForCircuit(circuitId: Long): List<GrazingCircuitPastureEntity>

    @Query("SELECT * FROM grazing_circuit_pasture_roles WHERE circuitId = :circuitId ORDER BY pastureId, role")
    suspend fun rolesForCircuit(circuitId: Long): List<GrazingCircuitPastureRoleEntity>

    @Query("SELECT * FROM herd_grazing_circuit_assignments WHERE herdId = :herdId")
    suspend fun assignmentForHerd(herdId: Long): HerdGrazingCircuitAssignmentEntity?

    @Query("SELECT COUNT(*) FROM grazing_circuits WHERE name = :name COLLATE NOCASE AND id != :excludeId")
    suspend fun countName(name: String, excludeId: Long = 0): Int

    @Query("SELECT COUNT(*) FROM pastures WHERE id = :pastureId")
    suspend fun countPasture(pastureId: Long): Int

    @Query("SELECT * FROM herds WHERE id = :herdId")
    suspend fun getHerd(herdId: Long): HerdEntity?

    @Query("SELECT COUNT(*) FROM herd_grazing_circuit_assignments WHERE circuitId = :circuitId")
    suspend fun assignmentCountForCircuit(circuitId: Long): Int

    @Query(
        """
        SELECT gc.name FROM grazing_circuits gc
        JOIN grazing_circuit_pastures gcp ON gcp.circuitId = gc.id
        WHERE gcp.pastureId = :pastureId
        ORDER BY gc.name COLLATE NOCASE
        """
    )
    suspend fun circuitNamesForPasture(pastureId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCircuitRaw(circuit: GrazingCircuitEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembershipsRaw(rows: List<GrazingCircuitPastureEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRolesRaw(rows: List<GrazingCircuitPastureRoleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssignmentRaw(row: HerdGrazingCircuitAssignmentEntity)

    @Query("DELETE FROM grazing_circuit_pasture_roles WHERE circuitId = :circuitId")
    suspend fun deleteRolesForCircuit(circuitId: Long)

    @Query("DELETE FROM grazing_circuit_pastures WHERE circuitId = :circuitId")
    suspend fun deleteMembershipsForCircuit(circuitId: Long)

    @Query("DELETE FROM herd_grazing_circuit_assignments WHERE herdId = :herdId")
    suspend fun deleteAssignmentForHerd(herdId: Long)

    @Query("DELETE FROM herd_grazing_circuit_assignments WHERE circuitId = :circuitId")
    suspend fun deleteAssignmentsForCircuit(circuitId: Long)

    @Query("UPDATE grazing_circuits SET name = :name, notes = :notes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCircuitRaw(id: Long, name: String, notes: String, updatedAt: Long)

    @Query("UPDATE grazing_circuits SET archivedAt = :archivedAt, updatedAt = :archivedAt WHERE id = :id")
    suspend fun archiveCircuitRaw(id: Long, archivedAt: Long)

    @Transaction
    suspend fun createCircuit(
        name: String,
        notes: String,
        pastures: List<GrazingCircuitPastureDraft>,
        now: Long = System.currentTimeMillis()
    ): Long {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Grazing Circuit name cannot be blank" }
        require(countName(trimmed) == 0) { "A Grazing Circuit named '$trimmed' already exists" }
        validateDrafts(pastures)
        val id = insertCircuitRaw(
            GrazingCircuitEntity(name = trimmed, notes = notes.trim(), createdAt = now, updatedAt = now)
        )
        replaceCircuitContents(id, pastures, now)
        return id
    }

    @Transaction
    suspend fun updateCircuit(
        circuitId: Long,
        name: String,
        notes: String,
        pastures: List<GrazingCircuitPastureDraft>,
        now: Long = System.currentTimeMillis()
    ) {
        val circuit = getCircuit(circuitId) ?: throw IllegalArgumentException("Grazing Circuit #$circuitId not found")
        check(circuit.archivedAt == null) { "Archived Grazing Circuits cannot be edited" }
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Grazing Circuit name cannot be blank" }
        require(countName(trimmed, circuitId) == 0) { "A Grazing Circuit named '$trimmed' already exists" }
        validateDrafts(pastures)
        updateCircuitRaw(circuitId, trimmed, notes.trim(), now)
        replaceCircuitContents(circuitId, pastures, now)
    }

    @Transaction
    suspend fun assignHerd(
        herdId: Long,
        circuitId: Long?,
        now: Long = System.currentTimeMillis()
    ) {
        val herd = getHerd(herdId) ?: throw IllegalArgumentException("Herd #$herdId not found")
        check(herd.archivedAt == null) { "Cannot assign an archived herd" }
        if (circuitId == null) {
            deleteAssignmentForHerd(herdId)
            return
        }
        val circuit = getCircuit(circuitId) ?: throw IllegalArgumentException("Grazing Circuit #$circuitId not found")
        check(circuit.archivedAt == null) { "Cannot assign an archived Grazing Circuit" }
        check(membershipsForCircuit(circuitId).isNotEmpty()) { "Cannot assign an empty Grazing Circuit" }
        upsertAssignmentRaw(HerdGrazingCircuitAssignmentEntity(herdId, circuitId, now))
    }

    @Transaction
    suspend fun archiveCircuit(circuitId: Long, now: Long = System.currentTimeMillis()) {
        val circuit = getCircuit(circuitId) ?: throw IllegalArgumentException("Grazing Circuit #$circuitId not found")
        check(circuit.archivedAt == null) { "Grazing Circuit is already archived" }
        check(assignmentCountForCircuit(circuitId) == 0) {
            "This circuit is assigned to an active herd. Change or remove that assignment first."
        }
        archiveCircuitRaw(circuitId, now)
    }

    @Transaction
    suspend fun clearAssignmentForArchivedHerd(herdId: Long) {
        deleteAssignmentForHerd(herdId)
    }

    private suspend fun replaceCircuitContents(
        circuitId: Long,
        drafts: List<GrazingCircuitPastureDraft>,
        now: Long
    ) {
        deleteRolesForCircuit(circuitId)
        deleteMembershipsForCircuit(circuitId)
        insertMembershipsRaw(drafts.mapIndexed { index, draft ->
            GrazingCircuitPastureEntity(circuitId, draft.pastureId, index, now)
        })
        insertRolesRaw(drafts.flatMap { draft ->
            draft.roles.map { role -> GrazingCircuitPastureRoleEntity(circuitId, draft.pastureId, role) }
        })
    }

    private suspend fun validateDrafts(drafts: List<GrazingCircuitPastureDraft>) {
        require(drafts.isNotEmpty()) { "A Grazing Circuit requires at least one pasture" }
        require(drafts.map { it.pastureId }.distinct().size == drafts.size) {
            "A pasture cannot appear twice in one Grazing Circuit"
        }
        drafts.forEach { require(countPasture(it.pastureId) == 1) { "Pasture #${it.pastureId} not found" } }
    }
}

