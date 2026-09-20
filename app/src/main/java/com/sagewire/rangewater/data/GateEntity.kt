package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gates",
    foreignKeys = [
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["junctionAId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["junctionBId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["junctionAId"]),
        Index(value = ["junctionBId"]),
        Index(value = ["junctionAId", "junctionBId", "segmentRatio"], unique = true)
    ]
)
data class GateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val junctionAId: Long,
    val junctionBId: Long,
    val segmentRatio: Double,
    val widthMeters: Double = WIDTH_14_FT,
    val gateType: String = TYPE_UNSPECIFIED,
    val status: String = STATUS_CLOSED,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(junctionAId < junctionBId) {
            "junctionAId ($junctionAId) must be strictly less than junctionBId ($junctionBId)"
        }
        require(segmentRatio in 0.0..1.0) {
            "segmentRatio ($segmentRatio) must be normalized between 0.0 and 1.0"
        }
        require(widthMeters in MIN_WIDTH_METERS..MAX_WIDTH_METERS) {
            "widthMeters ($widthMeters) must be between $MIN_WIDTH_METERS and $MAX_WIDTH_METERS"
        }
        require(gateType in VALID_TYPES) { "Unsupported gate type: $gateType" }
        require(status in VALID_STATUSES) { "Unsupported gate status: $status" }
    }

    companion object {
        const val TYPE_UNSPECIFIED = "UNSPECIFIED"
        const val TYPE_PIPE = "PIPE"
        const val TYPE_WIRE_GAP = "WIRE_GAP"
        const val TYPE_PANEL = "PANEL"
        const val TYPE_WOODEN = "WOODEN"
        val VALID_TYPES = listOf(TYPE_UNSPECIFIED, TYPE_PIPE, TYPE_WIRE_GAP, TYPE_PANEL, TYPE_WOODEN)

        const val STATUS_CLOSED = "CLOSED"
        const val STATUS_OPEN = "OPEN"
        val VALID_STATUSES = setOf(STATUS_CLOSED, STATUS_OPEN)

        const val MIN_WIDTH_METERS = 2.0
        const val MAX_WIDTH_METERS = 10.0
        const val WIDTH_10_FT = 3.05
        const val WIDTH_12_FT = 3.66
        const val WIDTH_14_FT = 4.27
        const val WIDTH_16_FT = 4.88
    }
}
