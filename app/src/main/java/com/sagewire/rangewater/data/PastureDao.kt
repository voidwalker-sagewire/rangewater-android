package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/* 🪨 One transaction owns every pasture boundary and shared-junction mutation. */
@Dao
interface PastureDao {
    @Transaction
    @Query("SELECT * FROM pastures ORDER BY id ASC")
    fun observeAll(): Flow<List<PastureWithVertices>>

    fun getAllPastures(): Flow<List<PastureWithVertices>> = observeAll()

    @Transaction
    @Query("SELECT * FROM pastures WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PastureWithVertices?

    @Query("SELECT * FROM pastures WHERE id = :id LIMIT 1")
    suspend fun getPastureEntity(id: Long): PastureEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPasture(pasture: PastureEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVertices(vertices: List<PastureVertexEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVertexEntity(vertex: PastureVertexEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertJunctionEntity(junction: FenceJunctionEntity): Long

    @Query("UPDATE fence_junctions SET latitude = :latitude, longitude = :longitude WHERE id = :junctionId")
    suspend fun updateJunctionCoordinates(junctionId: Long, latitude: Double, longitude: Double)

    @Query("SELECT DISTINCT pastureId FROM pasture_vertices WHERE junctionId = :junctionId")
    suspend fun pastureIdsForJunction(junctionId: Long): List<Long>

    @Query("UPDATE pasture_vertices SET junctionId = :targetId WHERE junctionId = :sourceId")
    suspend fun reassignJunction(sourceId: Long, targetId: Long)

    @Query("DELETE FROM fence_junctions WHERE id = :junctionId")
    suspend fun deleteJunction(junctionId: Long)

    @Query("DELETE FROM fence_junctions WHERE id NOT IN (SELECT DISTINCT junctionId FROM pasture_vertices)")
    suspend fun deleteOrphanJunctions()

    @Query("UPDATE pastures SET name = :name WHERE id = :id")
    suspend fun setGeneratedName(id: Long, name: String)

    @Query("UPDATE pastures SET name = :name, notes = :notes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, notes: String, updatedAt: Long)

    @Query("UPDATE pastures SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM pasture_vertices WHERE pastureId = :pastureId")
    suspend fun deleteVertices(pastureId: Long)

    @Query("DELETE FROM pastures WHERE id = :id")
    suspend fun deletePastureRow(id: Long)

    @Query("SELECT * FROM pasture_vertices WHERE pastureId = :pastureId ORDER BY sequence ASC")
    suspend fun verticesForPasture(pastureId: Long): List<PastureVertexEntity>

    @Query("SELECT COUNT(*) FROM gates WHERE junctionAId = :junctionAId AND junctionBId = :junctionBId")
    suspend fun gateCountOnSegment(junctionAId: Long, junctionBId: Long): Int

    @Query("SELECT COUNT(*) FROM gates WHERE junctionAId = :junctionId OR junctionBId = :junctionId")
    suspend fun gateCountForJunction(junctionId: Long): Int

    @Query(
        """
        SELECT DISTINCT pv1.pastureId
        FROM pasture_vertices AS pv1
        JOIN pasture_vertices AS pv2 ON pv1.pastureId = pv2.pastureId
        WHERE pv1.pastureId != :excludedPastureId
          AND ((pv1.junctionId = :junctionAId AND pv2.junctionId = :junctionBId)
            OR (pv1.junctionId = :junctionBId AND pv2.junctionId = :junctionAId))
          AND (
            ABS(pv1.sequence - pv2.sequence) = 1
            OR ABS(pv1.sequence - pv2.sequence) = (
              SELECT COUNT(*) - 1 FROM pasture_vertices AS pv3
              WHERE pv3.pastureId = pv1.pastureId
            )
          )
        """
    )
    suspend fun otherPasturesSharingSegment(
        junctionAId: Long,
        junctionBId: Long,
        excludedPastureId: Long
    ): List<Long>

    @Transaction
    suspend fun deleteById(id: Long) {
        val vertices = verticesForPasture(id)
        vertices.canonicalVertexSegments().forEach { (junctionAId, junctionBId) ->
            if (gateCountOnSegment(junctionAId, junctionBId) > 0 &&
                otherPasturesSharingSegment(junctionAId, junctionBId, id).isEmpty()
            ) {
                throw IllegalStateException(
                    "Cannot delete pasture: An exterior gate is anchored on segment " +
                        "#$junctionAId-#$junctionBId"
                )
            }
        }
        deletePastureRow(id)
        deleteOrphanJunctions()
    }

    @Transaction
    suspend fun insertWithVertices(
        requestedName: String,
        notes: String,
        vertices: List<PastureCoordinate>,
        now: Long = System.currentTimeMillis()
    ): Long {
        require(vertices.size >= 3) { "A pasture requires at least three vertices" }
        val id = insertPasture(
            PastureEntity(
                name = requestedName.trim(),
                notes = notes.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        if (requestedName.isBlank()) setGeneratedName(id, "Pasture $id")
        insertVertices(resolveVertexEntities(id, vertices))
        return id
    }

    @Transaction
    suspend fun replaceVertices(
        pastureId: Long,
        vertices: List<PastureCoordinate>,
        now: Long = System.currentTimeMillis()
    ) = savePastureBoundaryWithJunctions(pastureId, vertices, emptyMap(), now)

    /** Shared moves, references, timestamps, and orphan cleanup commit or roll back together. */
    @Transaction
    suspend fun savePastureBoundaryWithJunctions(
        pastureId: Long,
        vertices: List<PastureCoordinate>,
        junctionMoves: Map<Long, PastureCoordinate>,
        now: Long = System.currentTimeMillis()
    ) {
        require(vertices.size >= 3) { "A pasture requires at least three vertices" }
        check(getPastureEntity(pastureId) != null) { "Pasture $pastureId does not exist" }
        val existingVertices = verticesForPasture(pastureId)
        val newSegments = vertices.canonicalCoordinateSegments()
        existingVertices.canonicalVertexSegments().forEach { (junctionAId, junctionBId) ->
            if (gateCountOnSegment(junctionAId, junctionBId) > 0 &&
                (junctionAId to junctionBId) !in newSegments
            ) {
                throw IllegalStateException(
                    "Cannot alter fence: An active gate is anchored between posts " +
                        "#$junctionAId and #$junctionBId"
                )
            }
        }
        val affectedPastureIds = linkedSetOf(pastureId)
        junctionMoves.forEach { (junctionId, coordinate) ->
            affectedPastureIds += pastureIdsForJunction(junctionId)
            updateJunctionCoordinates(junctionId, coordinate.latitude, coordinate.longitude)
        }
        deleteVertices(pastureId)
        insertVertices(resolveVertexEntities(pastureId, vertices))
        affectedPastureIds.forEach { touch(it, now) }
        deleteOrphanJunctions()
    }

    @Transaction
    suspend fun mergeJunctions(
        sourceJunctionId: Long,
        targetJunctionId: Long,
        now: Long = System.currentTimeMillis()
    ) {
        require(sourceJunctionId != targetJunctionId)
        check(gateCountForJunction(sourceJunctionId) == 0) {
            "Cannot merge: Junction #$sourceJunctionId serves as an anchor for an active gate"
        }
        check(gateCountForJunction(targetJunctionId) == 0) {
            "Cannot merge: Junction #$targetJunctionId serves as an anchor for an active gate"
        }
        val sourcePastures = pastureIdsForJunction(sourceJunctionId)
        val targetPastures = pastureIdsForJunction(targetJunctionId)
        check(sourcePastures.intersect(targetPastures.toSet()).isEmpty()) {
            "Cannot merge: a pasture already references both junctions"
        }
        reassignJunction(sourceJunctionId, targetJunctionId)
        (sourcePastures + targetPastures).distinct().forEach { touch(it, now) }
        deleteJunction(sourceJunctionId)
        deleteOrphanJunctions()
    }

    private suspend fun resolveVertexEntities(
        pastureId: Long,
        coordinates: List<PastureCoordinate>
    ): List<PastureVertexEntity> = coordinates.mapIndexed { index, coordinate ->
        val junctionId = coordinate.junctionId ?: insertJunctionEntity(
            FenceJunctionEntity(
                latitude = coordinate.latitude,
                longitude = coordinate.longitude,
                elevationMeters = coordinate.elevationMeters,
                elevationSource = coordinate.elevationSource,
                verticalDatum = coordinate.verticalDatum,
                verticalAccuracyMeters = coordinate.verticalAccuracyMeters,
                elevationCapturedAt = coordinate.elevationCapturedAt
            )
        )
        PastureVertexEntity(pastureId = pastureId, sequence = index, junctionId = junctionId)
    }

    private fun List<PastureVertexEntity>.canonicalVertexSegments(): Set<Pair<Long, Long>> {
        if (size < 3) return emptySet()
        return indices.mapTo(linkedSetOf()) { index ->
            canonicalSegment(this[index].junctionId, this[(index + 1) % size].junctionId)
        }
    }

    private fun List<PastureCoordinate>.canonicalCoordinateSegments(): Set<Pair<Long, Long>> {
        if (size < 3) return emptySet()
        return indices.mapNotNullTo(linkedSetOf()) { index ->
            val first = this[index].junctionId
            val second = this[(index + 1) % size].junctionId
            if (first == null || second == null) null else canonicalSegment(first, second)
        }
    }

    private fun canonicalSegment(first: Long, second: Long): Pair<Long, Long> =
        if (first < second) first to second else second to first
}
