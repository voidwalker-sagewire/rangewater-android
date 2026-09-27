package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "paddock_split_plans",
    foreignKeys = [
        ForeignKey(
            entity = PastureEntity::class,
            parentColumns = ["id"],
            childColumns = ["pastureId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["startJunctionAId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["startJunctionBId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["endJunctionAId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["endJunctionBId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["pastureId"]),
        Index(value = ["startJunctionAId"]),
        Index(value = ["startJunctionBId"]),
        Index(value = ["endJunctionAId"]),
        Index(value = ["endJunctionBId"]),
        Index(value = ["archivedAt"])
    ]
)
data class PaddockSplitPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pastureId: Long,
    val name: String,
    val sideALabel: String = "Paddock A",
    val sideBLabel: String = "Paddock B",
    val startJunctionAId: Long,
    val startJunctionBId: Long,
    val startSegmentRatio: Double,
    val endJunctionAId: Long,
    val endJunctionBId: Long,
    val endSegmentRatio: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null
) {
    init {
        require(startJunctionAId < startJunctionBId) { "Start anchors must be canonical" }
        require(endJunctionAId < endJunctionBId) { "End anchors must be canonical" }
        require(startSegmentRatio.isFinite() && startSegmentRatio in 0.0..1.0) {
            "Start ratio must be finite and between 0 and 1"
        }
        require(endSegmentRatio.isFinite() && endSegmentRatio in 0.0..1.0) {
            "End ratio must be finite and between 0 and 1"
        }
    }
}
