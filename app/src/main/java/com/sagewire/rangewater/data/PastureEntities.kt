package com.sagewire.rangewater.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * 🪨 BLOCK 1 — PERSISTED PASTURE AND ORDERED FENCE CORNERS
 * Purpose: Stores durable pasture identity separately from its editable geometry.
 * 🪨 Protected: Coordinates are authoritative; acreage is always derived.
 */
@Entity(tableName = "pastures")
data class PastureEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "pasture_vertices",
    foreignKeys = [
        ForeignKey(
            entity = PastureEntity::class,
            parentColumns = ["id"],
            childColumns = ["pastureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pastureId"]),
        Index(value = ["pastureId", "sequence"], unique = true)
    ]
)
data class PastureVertexEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pastureId: Long,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val elevationSource: String? = null,
    val verticalDatum: String? = null,
    val verticalAccuracyMeters: Double? = null,
    val elevationCapturedAt: Long? = null
)

data class PastureWithVertices(
    @Embedded val pasture: PastureEntity,
    @Relation(parentColumn = "id", entityColumn = "pastureId")
    val vertices: List<PastureVertexEntity>
)

/** In-memory geometry input. Elevation remains nullable until a later terrain milestone. */
data class PastureCoordinate(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val elevationSource: String? = null,
    val verticalDatum: String? = null,
    val verticalAccuracyMeters: Double? = null,
    val elevationCapturedAt: Long? = null
)
