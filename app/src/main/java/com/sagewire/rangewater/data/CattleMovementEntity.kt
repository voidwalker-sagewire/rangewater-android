package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cattle_movements",
    foreignKeys = [
        ForeignKey(entity = HerdEntity::class, parentColumns = ["id"], childColumns = ["herdId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PastureEntity::class, parentColumns = ["id"], childColumns = ["originPastureId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PastureEntity::class, parentColumns = ["id"], childColumns = ["destinationPastureId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = GateEntity::class, parentColumns = ["id"], childColumns = ["gateId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index(value = ["herdId", "completedAt"]),
        Index(value = ["status", "plannedAt"]),
        Index(value = ["originPastureId"]),
        Index(value = ["destinationPastureId"]),
        Index(value = ["gateId"])
    ]
)
data class CattleMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val herdId: Long,
    val originLocationKind: HerdLocationKind,
    val originPastureId: Long? = null,
    val originNameSnapshot: String,
    val destinationLocationKind: HerdLocationKind,
    val destinationPastureId: Long? = null,
    val destinationNameSnapshot: String,
    val quantity: Int,
    val countUnit: CountUnit,
    val status: MovementStatus,
    val plannedAt: Long? = null,
    val completedAt: Long? = null,
    val unmappedRoute: Boolean = false,
    val gateId: Long? = null,
    val gateSnapshot: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(quantity > 0) { "Movement quantity must be positive" }
        require(originNameSnapshot.isNotBlank()) { "Origin snapshot cannot be blank" }
        require(destinationNameSnapshot.isNotBlank()) { "Destination snapshot cannot be blank" }
    }
}
