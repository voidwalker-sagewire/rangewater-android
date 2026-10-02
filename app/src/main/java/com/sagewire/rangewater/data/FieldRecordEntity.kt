package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FieldRecordType { TASK, OBSERVATION, INPUT, REPAIR, ANIMAL, WEATHER, NOTE }
enum class FieldTaskStatus { OPEN, COMPLETED, CANCELLED }

@Entity(
    tableName = "field_records",
    foreignKeys = [
        ForeignKey(
            entity = PastureEntity::class,
            parentColumns = ["id"],
            childColumns = ["pastureId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["pastureId"]),
        Index(value = ["recordType"]),
        Index(value = ["taskStatus"]),
        Index(value = ["observedAt"]),
        Index(value = ["archivedAt"])
    ]
)
data class FieldRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordType: FieldRecordType,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val pastureId: Long? = null,
    val taskStatus: FieldTaskStatus? = null,
    val observedAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val archivedAt: Long? = null
) {
    init {
        require(note.isNotBlank()) { "Field record note cannot be blank" }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Field record latitude is invalid" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Field record longitude is invalid" }
        require(observedAt > 0 && createdAt > 0 && updatedAt > 0) { "Field record timestamps are invalid" }
        if (recordType == FieldRecordType.TASK) {
            require(taskStatus != null) { "Task records require a status" }
            require((taskStatus == FieldTaskStatus.COMPLETED) == (completedAt != null)) {
                "Only completed tasks carry a completion time"
            }
        } else {
            require(taskStatus == null && completedAt == null) { "Non-task records cannot carry task state" }
        }
    }
}
