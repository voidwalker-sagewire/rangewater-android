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
        ),
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["junctionId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["pastureId"]),
        Index(value = ["junctionId"]),
        Index(value = ["pastureId", "sequence"], unique = true),
        Index(value = ["pastureId", "junctionId"], unique = true)
    ]
)
data class PastureVertexEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pastureId: Long,
    val sequence: Int,
    val junctionId: Long
)

data class PastureVertexWithJunction(
    @Embedded val vertex: PastureVertexEntity,
    @Relation(parentColumn = "junctionId", entityColumn = "id")
    val junction: FenceJunctionEntity
) {
    val id: Long get() = vertex.id
    val pastureId: Long get() = vertex.pastureId
    val sequence: Int get() = vertex.sequence
    val junctionId: Long get() = vertex.junctionId
    val latitude: Double get() = junction.latitude
    val longitude: Double get() = junction.longitude
    val elevationMeters: Double? get() = junction.elevationMeters
    val elevationSource: String? get() = junction.elevationSource
    val verticalDatum: String? get() = junction.verticalDatum
    val verticalAccuracyMeters: Double? get() = junction.verticalAccuracyMeters
    val elevationCapturedAt: Long? get() = junction.elevationCapturedAt
}

data class PastureWithVertices(
    @Embedded val pasture: PastureEntity,
    @Relation(
        entity = PastureVertexEntity::class,
        parentColumn = "id",
        entityColumn = "pastureId"
    )
    val vertices: List<PastureVertexWithJunction>
)
