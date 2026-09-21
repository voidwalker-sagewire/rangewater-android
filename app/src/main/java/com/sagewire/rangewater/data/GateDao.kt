package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

@Dao
interface GateDao {
    @Query("SELECT * FROM gates ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<GateEntity>>

    @Query("SELECT * FROM gates WHERE id = :id")
    suspend fun getById(id: Long): GateEntity?

    @Query("SELECT * FROM gates WHERE junctionAId = :junctionAId AND junctionBId = :junctionBId")
    suspend fun getGatesForSegment(junctionAId: Long, junctionBId: Long): List<GateEntity>

    @Query("SELECT * FROM fence_junctions WHERE id = :id")
    suspend fun getJunction(id: Long): FenceJunctionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGateRaw(gate: GateEntity): Long

    @Update
    suspend fun updateGateRaw(gate: GateEntity)

    @Query("UPDATE gates SET status = :status, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStatusRaw(id: Long, status: String, timestamp: Long)

    @Query(
        "UPDATE gates SET junctionAId = :junctionAId, junctionBId = :junctionBId, " +
            "segmentRatio = :segmentRatio, updatedAt = :timestamp WHERE id = :id"
    )
    suspend fun updatePositionRaw(
        id: Long,
        junctionAId: Long,
        junctionBId: Long,
        segmentRatio: Double,
        timestamp: Long
    )

    @Delete
    suspend fun deleteGate(gate: GateEntity)

    @Transaction
    suspend fun insertValidatedGate(gate: GateEntity): Long {
        validateCanonicalValues(gate)
        val segmentLengthMeters = segmentLengthMeters(gate.junctionAId, gate.junctionBId)
        validateGeometryAndOverlap(gate, segmentLengthMeters, excludeGateId = 0L)

        val now = System.currentTimeMillis()
        val initialName = gate.name.trim()
        val id = insertGateRaw(gate.copy(name = initialName, createdAt = now, updatedAt = now))
        if (initialName.isBlank()) {
            updateGateRaw(gate.copy(id = id, name = "Gate $id", createdAt = now, updatedAt = now))
        }
        return id
    }

    @Transaction
    suspend fun updateValidatedDetails(
        id: Long,
        name: String,
        widthMeters: Double,
        gateType: String,
        notes: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val existing = getById(id) ?: throw IllegalArgumentException("Gate #$id not found")
        require(widthMeters in GateEntity.MIN_WIDTH_METERS..GateEntity.MAX_WIDTH_METERS) {
            "Gate width must be between 2.0m and 10.0m"
        }
        val segmentLengthMeters = segmentLengthMeters(existing.junctionAId, existing.junctionBId)
        require(segmentLengthMeters >= widthMeters + 1.0) {
            "Fence segment too short for gate width"
        }
        val clearanceRatio = ((widthMeters / 2.0) + 0.5) / segmentLengthMeters
        val normalizedType = gateType.trim().uppercase(Locale.US)
        val updated = existing.copy(
            name = name.trim(),
            widthMeters = widthMeters,
            segmentRatio = existing.segmentRatio.coerceIn(clearanceRatio, 1.0 - clearanceRatio),
            gateType = normalizedType.takeIf { it in GateEntity.VALID_TYPES } ?: GateEntity.TYPE_UNSPECIFIED,
            notes = notes.trim(),
            updatedAt = timestamp
        )
        validateGeometryAndOverlap(updated, segmentLengthMeters, excludeGateId = id)
        updateGateRaw(updated)
    }

    @Transaction
    suspend fun updateStatus(id: Long, status: String, timestamp: Long = System.currentTimeMillis()) {
        require(status in GateEntity.VALID_STATUSES) { "Unsupported gate status: $status" }
        check(getById(id) != null) { "Gate #$id not found" }
        updateStatusRaw(id, status, timestamp)
    }

    @Transaction
    suspend fun updateValidatedPosition(
        id: Long,
        junctionAId: Long,
        junctionBId: Long,
        segmentRatio: Double,
        timestamp: Long = System.currentTimeMillis()
    ) {
        require(junctionAId < junctionBId) { "Gate anchor junctions must be canonical" }
        require(segmentRatio in 0.0..1.0) { "Gate ratio must be between 0 and 1" }
        val existing = getById(id) ?: throw IllegalArgumentException("Gate #$id not found")
        val updated = existing.copy(
            junctionAId = junctionAId,
            junctionBId = junctionBId,
            segmentRatio = segmentRatio,
            updatedAt = timestamp
        )
        val targetLengthMeters = segmentLengthMeters(junctionAId, junctionBId)
        validateGeometryAndOverlap(updated, targetLengthMeters, excludeGateId = id)
        updatePositionRaw(id, junctionAId, junctionBId, segmentRatio, timestamp)
    }

    private fun validateCanonicalValues(gate: GateEntity) {
        require(gate.junctionAId < gate.junctionBId) { "Gate anchor junctions must be canonical" }
        require(gate.segmentRatio in 0.0..1.0) { "Gate ratio must be between 0 and 1" }
        require(gate.gateType in GateEntity.VALID_TYPES) { "Unsupported gate type: ${gate.gateType}" }
        require(gate.status in GateEntity.VALID_STATUSES) { "Unsupported gate status: ${gate.status}" }
    }

    private suspend fun segmentLengthMeters(junctionAId: Long, junctionBId: Long): Double {
        val a = getJunction(junctionAId) ?: throw IllegalArgumentException("Fence post #$junctionAId not found")
        val b = getJunction(junctionBId) ?: throw IllegalArgumentException("Fence post #$junctionBId not found")
        val latitudeRadians = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val dx = (b.longitude - a.longitude) * 111_320.0 * cos(latitudeRadians)
        val dy = (b.latitude - a.latitude) * 110_540.0
        return sqrt(dx * dx + dy * dy)
    }

    private suspend fun validateGeometryAndOverlap(
        gate: GateEntity,
        segmentLengthMeters: Double,
        excludeGateId: Long
    ) {
        require(segmentLengthMeters >= gate.widthMeters + 1.0) {
            "Fence segment too short for gate width"
        }
        val clearanceRatio = ((gate.widthMeters / 2.0) + 0.5) / segmentLengthMeters
        require(gate.segmentRatio >= clearanceRatio - 1e-4 && gate.segmentRatio <= 1.0 - clearanceRatio + 1e-4) {
            "Gate violates minimum 0.5m post clearance"
        }
        getGatesForSegment(gate.junctionAId, gate.junctionBId)
            .filterNot { it.id == excludeGateId }
            .forEach { other ->
                val centerDistance = abs(gate.segmentRatio - other.segmentRatio) * segmentLengthMeters
                val requiredDistance = ((gate.widthMeters + other.widthMeters) / 2.0) + 0.5
                require(centerDistance >= requiredDistance) {
                    "Gate overlaps with existing gate '${other.name}'"
                }
            }
    }
}
