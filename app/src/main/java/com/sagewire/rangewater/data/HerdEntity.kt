package com.sagewire.rangewater.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "herds",
    foreignKeys = [
        ForeignKey(
            entity = PastureEntity::class,
            parentColumns = ["id"],
            childColumns = ["currentPastureId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["currentPastureId"]),
        Index(value = ["archivedAt"])
    ]
)
data class HerdEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val quantity: Int,
    val countUnit: CountUnit,
    val stockClass: StockClass,
    val averageWeightLbs: Double? = null,
    val markerColorHex: String,
    val shortMarkerLabel: String? = null,
    val notes: String = "",
    val locationKind: HerdLocationKind,
    val currentPastureId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val archivedAt: Long? = null
) {
    init {
        require(name.isNotBlank()) { "Herd name cannot be blank" }
        require(quantity > 0) { "Quantity must be strictly positive" }
        require(averageWeightLbs == null || averageWeightLbs > 0.0) { "Average weight must be positive" }
        require(markerColorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) { "Color must be a valid 6-character hex value" }
        require(shortMarkerLabel == null || shortMarkerLabel.length <= 4) { "Short label cannot exceed 4 characters" }
        if (locationKind == HerdLocationKind.PASTURE) {
            require(currentPastureId != null) { "currentPastureId is required when locationKind is PASTURE" }
        } else {
            require(currentPastureId == null) { "currentPastureId must be null when locationKind is not PASTURE" }
        }
    }
}
