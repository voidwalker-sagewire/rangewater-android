package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/*
 * 🪨 BLOCK 1 — ATOMIC PASTURE DATA ACCESS
 * Purpose: Creates, reshapes, renames, and deletes a pasture without partial geometry.
 */
@Dao
interface PastureDao {
    @Transaction
    @Query("SELECT * FROM pastures ORDER BY id ASC")
    fun observeAll(): Flow<List<PastureWithVertices>>

    @Transaction
    @Query("SELECT * FROM pastures WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PastureWithVertices?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPasture(pasture: PastureEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVertices(vertices: List<PastureVertexEntity>)

    @Query("UPDATE pastures SET name = :name WHERE id = :id")
    suspend fun setGeneratedName(id: Long, name: String)

    @Query("UPDATE pastures SET name = :name, notes = :notes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, notes: String, updatedAt: Long)

    @Query("UPDATE pastures SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("DELETE FROM pasture_vertices WHERE pastureId = :pastureId")
    suspend fun deleteVertices(pastureId: Long)

    @Query("DELETE FROM pastures WHERE id = :id")
    suspend fun deleteById(id: Long)

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
        insertVertices(vertices.toPastureVertexEntities(id))
        return id
    }

    @Transaction
    suspend fun replaceVertices(
        pastureId: Long,
        vertices: List<PastureCoordinate>,
        now: Long = System.currentTimeMillis()
    ) {
        require(vertices.size >= 3) { "A pasture requires at least three vertices" }
        check(getById(pastureId) != null) { "Pasture $pastureId does not exist" }
        deleteVertices(pastureId)
        insertVertices(vertices.toPastureVertexEntities(pastureId))
        touch(pastureId, now)
    }
}

private fun List<PastureCoordinate>.toPastureVertexEntities(pastureId: Long) =
    mapIndexed { index, coordinate ->
        PastureVertexEntity(
            pastureId = pastureId,
            sequence = index,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            elevationMeters = coordinate.elevationMeters,
            elevationSource = coordinate.elevationSource,
            verticalDatum = coordinate.verticalDatum,
            verticalAccuracyMeters = coordinate.verticalAccuracyMeters,
            elevationCapturedAt = coordinate.elevationCapturedAt
        )
    }
