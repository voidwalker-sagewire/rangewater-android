package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Canonical physical fence corner which may be referenced by several pastures. */
@Entity(tableName = "fence_junctions")
data class FenceJunctionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val elevationSource: String? = null,
    val verticalDatum: String? = null,
    val verticalAccuracyMeters: Double? = null,
    val elevationCapturedAt: Long? = null
)
