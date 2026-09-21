package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MovementDao {
    @Query("SELECT * FROM cattle_movements ORDER BY createdAt DESC")
    fun observeAllMovements(): Flow<List<CattleMovementEntity>>

    @Query("SELECT * FROM cattle_movements WHERE herdId = :herdId ORDER BY createdAt DESC")
    fun observeMovementsForHerd(herdId: Long): Flow<List<CattleMovementEntity>>

    @Query("SELECT * FROM cattle_movements WHERE status = 'PLANNED' ORDER BY plannedAt ASC")
    fun observePlannedMovements(): Flow<List<CattleMovementEntity>>

    @Query("SELECT * FROM cattle_movements WHERE id = :id")
    suspend fun getById(id: Long): CattleMovementEntity?

    @Query("SELECT * FROM herds WHERE id = :id")
    suspend fun getHerdById(id: Long): HerdEntity?

    @Query("SELECT * FROM pastures WHERE id = :id")
    suspend fun getPastureById(id: Long): PastureEntity?

    @Query("SELECT * FROM gates WHERE id = :id")
    suspend fun getGateById(id: Long): GateEntity?

    @Query(
        """
        SELECT COUNT(*) FROM pasture_vertices pv1
        JOIN pasture_vertices pv2 ON pv1.pastureId = pv2.pastureId
        WHERE pv1.pastureId = :pastureId
          AND ((pv1.junctionId = :jA AND pv2.junctionId = :jB) OR (pv1.junctionId = :jB AND pv2.junctionId = :jA))
          AND (ABS(pv1.sequence - pv2.sequence) = 1 OR ABS(pv1.sequence - pv2.sequence) =
            (SELECT COUNT(*) - 1 FROM pasture_vertices pv3 WHERE pv3.pastureId = pv1.pastureId))
        """
    )
    suspend fun countConsecutiveSegmentInPasture(pastureId: Long, jA: Long, jB: Long): Int

    @Query(
        """
        SELECT COUNT(DISTINCT pv1.pastureId) FROM pasture_vertices pv1
        JOIN pasture_vertices pv2 ON pv1.pastureId = pv2.pastureId
        WHERE pv1.pastureId != :excludePastureId
          AND ((pv1.junctionId = :jA AND pv2.junctionId = :jB) OR (pv1.junctionId = :jB AND pv2.junctionId = :jA))
          AND (ABS(pv1.sequence - pv2.sequence) = 1 OR ABS(pv1.sequence - pv2.sequence) =
            (SELECT COUNT(*) - 1 FROM pasture_vertices pv3 WHERE pv3.pastureId = pv1.pastureId))
        """
    )
    suspend fun countOtherPasturesSharingSegment(jA: Long, jB: Long, excludePastureId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovementRaw(movement: CattleMovementEntity): Long

    @Query("UPDATE herds SET locationKind = :kind, currentPastureId = :pastureId, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateHerdLocationRaw(id: Long, kind: HerdLocationKind, pastureId: Long?, timestamp: Long)

    @Query("UPDATE cattle_movements SET status = :status, completedAt = :completedAt, quantity = :quantity, countUnit = :unit, notes = :notes, updatedAt = :timestamp WHERE id = :id")
    suspend fun completeMovementRaw(id: Long, status: MovementStatus, completedAt: Long, quantity: Int, unit: CountUnit, notes: String, timestamp: Long)

    @Query("UPDATE cattle_movements SET status = 'CANCELED', updatedAt = :timestamp WHERE id = :id")
    suspend fun cancelMovementRaw(id: Long, timestamp: Long)

    @Transaction
    suspend fun scheduleOrLogMovement(
        herdId: Long,
        destinationLocationKind: HerdLocationKind,
        destinationPastureId: Long?,
        gateId: Long?,
        unmappedRoute: Boolean,
        plannedAt: Long?,
        completedImmediately: Boolean,
        notes: String
    ): Long {
        val now = System.currentTimeMillis()
        val herd = getHerdById(herdId) ?: throw IllegalArgumentException("Herd #$herdId not found")
        if (herd.archivedAt != null) throw IllegalStateException("Cannot move an archived herd")
        require((gateId != null) xor unmappedRoute) { "Must select exactly one route option: mapped gate XOR unmapped route" }
        require(!unmappedRoute || notes.isNotBlank()) { "Unmapped or other routes require explanatory notes" }
        if (destinationLocationKind == HerdLocationKind.PASTURE) {
            requireNotNull(destinationPastureId) { "Destination pasture ID is required when destination is PASTURE" }
        } else {
            require(destinationPastureId == null) { "Destination pasture ID must be null when destination is not PASTURE" }
        }
        require(herd.locationKind != destinationLocationKind || herd.currentPastureId != destinationPastureId) {
            "Origin and destination cannot be identical"
        }

        val gate = gateId?.let { getGateById(it) ?: throw IllegalArgumentException("Gate #$it not found") }
        if (gate != null) validateGateConnectivity(herd.locationKind, herd.currentPastureId, destinationLocationKind, destinationPastureId, gate)

        val originSnapshot = if (herd.locationKind == HerdLocationKind.PASTURE) {
            getPastureById(herd.currentPastureId!!)?.name
                ?: throw IllegalStateException("Origin pasture #${herd.currentPastureId} does not exist")
        } else herd.locationKind.name
        val destinationSnapshot = if (destinationLocationKind == HerdLocationKind.PASTURE) {
            getPastureById(destinationPastureId!!)?.name
                ?: throw IllegalArgumentException("Destination pasture #$destinationPastureId does not exist")
        } else destinationLocationKind.name

        val movement = CattleMovementEntity(
            herdId = herd.id,
            originLocationKind = herd.locationKind,
            originPastureId = herd.currentPastureId,
            originNameSnapshot = originSnapshot,
            destinationLocationKind = destinationLocationKind,
            destinationPastureId = destinationPastureId,
            destinationNameSnapshot = destinationSnapshot,
            quantity = herd.quantity,
            countUnit = herd.countUnit,
            status = if (completedImmediately) MovementStatus.COMPLETED else MovementStatus.PLANNED,
            plannedAt = plannedAt ?: now,
            completedAt = now.takeIf { completedImmediately },
            unmappedRoute = unmappedRoute,
            gateId = gateId,
            gateSnapshot = gate?.let { "Gate #${it.id} - ${it.name}" },
            notes = notes.trim(),
            createdAt = now,
            updatedAt = now
        )
        val movementId = insertMovementRaw(movement)
        if (completedImmediately) updateHerdLocationRaw(herd.id, destinationLocationKind, destinationPastureId, now)
        return movementId
    }

    @Transaction
    suspend fun completePlannedMovement(movementId: Long, completedAt: Long, updatedNotes: String?) {
        val move = getById(movementId) ?: throw IllegalArgumentException("Movement #$movementId not found")
        check(move.status == MovementStatus.PLANNED) { "Only PLANNED movements can be completed. Current status: ${move.status}" }
        val herd = getHerdById(move.herdId) ?: throw IllegalArgumentException("Herd #${move.herdId} not found")
        check(herd.archivedAt == null) { "Cannot complete a movement for an archived herd" }
        check(herd.locationKind == move.originLocationKind && herd.currentPastureId == move.originPastureId) {
            "Herd is no longer at planned origin (${move.originNameSnapshot}). Move must be canceled and rescheduled."
        }
        move.gateId?.let { gateId ->
            val gate = getGateById(gateId) ?: throw IllegalStateException("Planned gate #$gateId no longer exists")
            validateGateConnectivity(move.originLocationKind, move.originPastureId, move.destinationLocationKind, move.destinationPastureId, gate)
        }
        val transactionTimestamp = System.currentTimeMillis()
        completeMovementRaw(
            movementId,
            MovementStatus.COMPLETED,
            completedAt,
            herd.quantity,
            herd.countUnit,
            updatedNotes?.trim()?.takeIf { it.isNotEmpty() } ?: move.notes,
            transactionTimestamp
        )
        updateHerdLocationRaw(herd.id, move.destinationLocationKind, move.destinationPastureId, transactionTimestamp)
    }

    @Transaction
    suspend fun cancelPlannedMovement(movementId: Long) {
        val move = getById(movementId) ?: throw IllegalArgumentException("Movement #$movementId not found")
        check(move.status == MovementStatus.PLANNED) { "Only PLANNED movements can be canceled. Current status: ${move.status}" }
        cancelMovementRaw(movementId, System.currentTimeMillis())
    }

    private suspend fun validateGateConnectivity(
        originLocationKind: HerdLocationKind,
        originPastureId: Long?,
        destinationLocationKind: HerdLocationKind,
        destinationPastureId: Long?,
        gate: GateEntity
    ) {
        when {
            originLocationKind == HerdLocationKind.PASTURE && destinationLocationKind == HerdLocationKind.PASTURE -> {
                val originId = requireNotNull(originPastureId)
                val destinationId = requireNotNull(destinationPastureId)
                if (countConsecutiveSegmentInPasture(originId, gate.junctionAId, gate.junctionBId) == 0 ||
                    countConsecutiveSegmentInPasture(destinationId, gate.junctionAId, gate.junctionBId) == 0
                ) throw IllegalStateException("Selected gate does not connect origin pasture #$originId and destination pasture #$destinationId")
            }
            originLocationKind == HerdLocationKind.PASTURE -> {
                val originId = requireNotNull(originPastureId)
                if (countConsecutiveSegmentInPasture(originId, gate.junctionAId, gate.junctionBId) == 0 ||
                    countOtherPasturesSharingSegment(gate.junctionAId, gate.junctionBId, originId) > 0
                ) throw IllegalStateException("Gate #${gate.id} is not an exterior perimeter gate for origin pasture #$originId")
            }
            destinationLocationKind == HerdLocationKind.PASTURE -> {
                val destinationId = requireNotNull(destinationPastureId)
                if (countConsecutiveSegmentInPasture(destinationId, gate.junctionAId, gate.junctionBId) == 0 ||
                    countOtherPasturesSharingSegment(gate.junctionAId, gate.junctionBId, destinationId) > 0
                ) throw IllegalStateException("Gate #${gate.id} is not an exterior perimeter gate for destination pasture #$destinationId")
            }
            else -> throw IllegalStateException("Gate routing requires at least one endpoint to be a mapped pasture")
        }
    }
}
