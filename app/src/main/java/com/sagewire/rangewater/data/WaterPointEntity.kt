package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WaterSourceType {
    TROUGH,
    TANK,
    SPRING,
    POND,
    HYDRANT,
    OTHER
}

/*
 * 🪨 BLOCK 1 — WATER ASSET ENTITY
 * Purpose: Durable device-local schema for a pasture water source.
 * 🎮 Behavior: Stores WGS84 position, operator metadata, and audit timestamps.
 */
@Entity(tableName = "water_points")
data class WaterPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val sourceType: WaterSourceType = WaterSourceType.TROUGH,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)
