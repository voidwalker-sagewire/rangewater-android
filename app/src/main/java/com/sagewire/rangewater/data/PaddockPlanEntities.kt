package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaddockPlanNodeKind {
    BOUNDARY_ANCHOR,
    INTERIOR_JUNCTION,
    INTERIOR_WAYPOINT
}

@Entity(
    tableName = "paddock_plans",
    foreignKeys = [
        ForeignKey(
            entity = PastureEntity::class,
            parentColumns = ["id"],
            childColumns = ["pastureId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["pastureId"]),
        Index(value = ["archivedAt"])
    ]
)
data class PaddockPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pastureId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null
) {
    init { require(name.isNotBlank()) { "Plan name cannot be blank" } }
}

@Entity(
    tableName = "paddock_plan_nodes",
    foreignKeys = [
        ForeignKey(
            entity = PaddockPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["boundaryJunctionAId"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = FenceJunctionEntity::class,
            parentColumns = ["id"],
            childColumns = ["boundaryJunctionBId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["planId"]),
        Index(value = ["boundaryJunctionAId"]),
        Index(value = ["boundaryJunctionBId"])
    ]
)
data class PaddockPlanNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val nodeKind: PaddockPlanNodeKind,
    val boundaryJunctionAId: Long? = null,
    val boundaryJunctionBId: Long? = null,
    val boundarySegmentRatio: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        when (nodeKind) {
            PaddockPlanNodeKind.BOUNDARY_ANCHOR -> {
                require(boundaryJunctionAId != null && boundaryJunctionBId != null) { "Boundary anchor requires a segment" }
                require(boundaryJunctionAId < boundaryJunctionBId) { "Boundary anchor segment must be canonical" }
                require(boundarySegmentRatio?.let { it.isFinite() && it in 0.0..1.0 } == true) { "Boundary anchor ratio is invalid" }
                require(latitude == null && longitude == null) { "Boundary anchor coordinates are derived" }
            }
            PaddockPlanNodeKind.INTERIOR_JUNCTION,
            PaddockPlanNodeKind.INTERIOR_WAYPOINT -> {
                require(boundaryJunctionAId == null && boundaryJunctionBId == null && boundarySegmentRatio == null) {
                    "Interior nodes cannot carry boundary anchors"
                }
                require(latitude?.let { it.isFinite() && it in -90.0..90.0 } == true) { "Interior-node latitude is invalid" }
                require(longitude?.let { it.isFinite() && it in -180.0..180.0 } == true) { "Interior-node longitude is invalid" }
            }
        }
    }
}

@Entity(
    tableName = "paddock_dividers",
    foreignKeys = [
        ForeignKey(
            entity = PaddockPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["planId"]),
        Index(value = ["planId", "sequence"], unique = true),
        Index(value = ["archivedAt"])
    ]
)
data class PaddockDividerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val name: String,
    val sequence: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null
) {
    init {
        require(name.isNotBlank()) { "Divider name cannot be blank" }
        require(sequence >= 0) { "Divider sequence cannot be negative" }
    }
}

@Entity(
    tableName = "paddock_divider_node_refs",
    primaryKeys = ["dividerId", "sequence"],
    foreignKeys = [
        ForeignKey(
            entity = PaddockDividerEntity::class,
            parentColumns = ["id"],
            childColumns = ["dividerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PaddockPlanNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index(value = ["nodeId"])]
)
data class PaddockDividerNodeRefEntity(
    val dividerId: Long,
    val sequence: Int,
    val nodeId: Long
) {
    init { require(sequence >= 0) { "Node-reference sequence cannot be negative" } }
}

@Entity(
    tableName = "paddock_region_labels",
    primaryKeys = ["planId", "regionKey"],
    foreignKeys = [
        ForeignKey(
            entity = PaddockPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["planId"])]
)
data class PaddockRegionLabelEntity(
    val planId: Long,
    val regionKey: String,
    val label: String
) {
    init {
        require(regionKey.isNotBlank()) { "Region key cannot be blank" }
        require(label.isNotBlank()) { "Region label cannot be blank" }
    }
}
