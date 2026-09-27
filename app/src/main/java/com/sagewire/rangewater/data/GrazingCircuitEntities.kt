package com.sagewire.rangewater.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class SeasonalPastureRole {
    WINTER,
    CALVING,
    ROTATION,
    STOCKPILED_WINTER
}

@Entity(
    tableName = "grazing_circuits",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["archivedAt"])
    ]
)
data class GrazingCircuitEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null
) {
    init {
        require(name.isNotBlank()) { "Grazing Circuit name cannot be blank" }
    }
}

@Entity(
    tableName = "grazing_circuit_pastures",
    primaryKeys = ["circuitId", "pastureId"],
    foreignKeys = [
        ForeignKey(
            entity = GrazingCircuitEntity::class,
            parentColumns = ["id"],
            childColumns = ["circuitId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PastureEntity::class,
            parentColumns = ["id"],
            childColumns = ["pastureId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["circuitId", "sequence"], unique = true),
        Index(value = ["pastureId"])
    ]
)
data class GrazingCircuitPastureEntity(
    val circuitId: Long,
    val pastureId: Long,
    val sequence: Int,
    val assignedAt: Long = System.currentTimeMillis()
) {
    init {
        require(sequence >= 0) { "Circuit pasture sequence cannot be negative" }
    }
}

@Entity(
    tableName = "grazing_circuit_pasture_roles",
    primaryKeys = ["circuitId", "pastureId", "role"],
    foreignKeys = [
        ForeignKey(
            entity = GrazingCircuitPastureEntity::class,
            parentColumns = ["circuitId", "pastureId"],
            childColumns = ["circuitId", "pastureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["circuitId", "pastureId"])]
)
data class GrazingCircuitPastureRoleEntity(
    val circuitId: Long,
    val pastureId: Long,
    val role: SeasonalPastureRole
)

@Entity(
    tableName = "herd_grazing_circuit_assignments",
    foreignKeys = [
        ForeignKey(
            entity = HerdEntity::class,
            parentColumns = ["id"],
            childColumns = ["herdId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GrazingCircuitEntity::class,
            parentColumns = ["id"],
            childColumns = ["circuitId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["circuitId"])]
)
data class HerdGrazingCircuitAssignmentEntity(
    @androidx.room.PrimaryKey val herdId: Long,
    val circuitId: Long,
    val assignedAt: Long = System.currentTimeMillis()
)

data class GrazingCircuitPastureDraft(
    val pastureId: Long,
    val roles: Set<SeasonalPastureRole> = emptySet()
)

