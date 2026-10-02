package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldRecordDao {
    @Query("SELECT * FROM field_records WHERE archivedAt IS NULL ORDER BY observedAt DESC, id DESC")
    fun observeActive(): Flow<List<FieldRecordEntity>>

    @Query("SELECT * FROM field_records ORDER BY observedAt DESC, id DESC")
    fun observeAll(): Flow<List<FieldRecordEntity>>

    @Query("SELECT * FROM field_records WHERE id = :id")
    suspend fun getById(id: Long): FieldRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(record: FieldRecordEntity): Long

    @Update
    suspend fun updateRaw(record: FieldRecordEntity)

    @Transaction
    suspend fun create(record: FieldRecordEntity): Long = insertRaw(normalize(record))

    @Transaction
    suspend fun edit(record: FieldRecordEntity, now: Long = System.currentTimeMillis()) {
        val existing = getById(record.id) ?: throw IllegalArgumentException("Field record #${record.id} not found")
        check(existing.archivedAt == null) { "Archived field records must be reactivated before editing" }
        updateRaw(normalize(record.copy(createdAt = existing.createdAt, updatedAt = now)))
    }

    @Transaction
    suspend fun completeTask(id: Long, now: Long = System.currentTimeMillis()) {
        val existing = getById(id) ?: throw IllegalArgumentException("Field record #$id not found")
        check(existing.recordType == FieldRecordType.TASK) { "Only a Task can be completed" }
        check(existing.archivedAt == null) { "Archived Tasks cannot be completed" }
        updateRaw(existing.copy(taskStatus = FieldTaskStatus.COMPLETED, completedAt = now, updatedAt = now))
    }

    @Transaction
    suspend fun reopenTask(id: Long, now: Long = System.currentTimeMillis()) {
        val existing = getById(id) ?: throw IllegalArgumentException("Field record #$id not found")
        check(existing.recordType == FieldRecordType.TASK) { "Only a Task can be reopened" }
        check(existing.taskStatus == FieldTaskStatus.COMPLETED) { "Only a completed Task can be reopened" }
        check(existing.archivedAt == null) { "Archived Tasks cannot be reopened" }
        updateRaw(existing.copy(taskStatus = FieldTaskStatus.OPEN, completedAt = null, updatedAt = now))
    }

    @Transaction
    suspend fun archive(id: Long, now: Long = System.currentTimeMillis()) {
        val existing = getById(id) ?: throw IllegalArgumentException("Field record #$id not found")
        check(existing.archivedAt == null) { "Field record is already archived" }
        updateRaw(existing.copy(archivedAt = now, updatedAt = now))
    }

    @Transaction
    suspend fun reactivate(id: Long, now: Long = System.currentTimeMillis()) {
        val existing = getById(id) ?: throw IllegalArgumentException("Field record #$id not found")
        check(existing.archivedAt != null) { "Field record is already active" }
        updateRaw(existing.copy(archivedAt = null, updatedAt = now))
    }

    private fun normalize(record: FieldRecordEntity): FieldRecordEntity = record.copy(note = record.note.trim())
}
