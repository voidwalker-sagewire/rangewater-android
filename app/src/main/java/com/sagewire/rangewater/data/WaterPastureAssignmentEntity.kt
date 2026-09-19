package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/*
 * 🪨 BLOCK 1 — EXPLICIT WATER-TO-PASTURE ACCESS
 * Purpose: Records operator-authorized many-to-many access without changing either asset.
 */
@Entity(
    tableName = "water_pasture_assignments",
    primaryKeys = ["waterPointId", "pastureId"],
    foreignKeys = [
        ForeignKey(
            entity = WaterPointEntity::class,
            parentColumns = ["id"],
            childColumns = ["waterPointId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PastureEntity::class,
            parentColumns = ["id"],
            childColumns = ["pastureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("waterPointId"), Index("pastureId")]
)
data class WaterPastureAssignmentEntity(
    val waterPointId: Long,
    val pastureId: Long,
    val assignedAt: Long = System.currentTimeMillis()
)
