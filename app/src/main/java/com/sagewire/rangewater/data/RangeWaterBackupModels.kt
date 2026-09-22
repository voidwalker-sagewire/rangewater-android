package com.sagewire.rangewater.data

import com.sagewire.rangewater.ui.map.DisplayPreferences

data class RangeWaterBackupData(
    val waterPoints: List<WaterPointEntity> = emptyList(),
    val pastures: List<PastureEntity> = emptyList(),
    val junctions: List<FenceJunctionEntity> = emptyList(),
    val vertices: List<PastureVertexEntity> = emptyList(),
    val assignments: List<WaterPastureAssignmentEntity> = emptyList(),
    val gates: List<GateEntity> = emptyList(),
    val herds: List<HerdEntity> = emptyList(),
    val movements: List<CattleMovementEntity> = emptyList(),
    val displayPreferences: DisplayPreferences = DisplayPreferences()
) {
    fun recordCounts(): BackupRecordCounts = BackupRecordCounts(
        waterPoints.size,
        pastures.size,
        junctions.size,
        vertices.size,
        assignments.size,
        gates.size,
        herds.size,
        movements.size
    )
}

data class BackupRecordCounts(
    val waterPoints: Int,
    val pastures: Int,
    val junctions: Int,
    val vertices: Int,
    val assignments: Int,
    val gates: Int,
    val herds: Int,
    val movements: Int
)

data class RangeWaterBackupManifest(
    val formatId: String,
    val formatVersion: Int,
    val databaseSchemaVersion: Int,
    val appVersionName: String,
    val createdAt: Long,
    val dataSha256: String,
    val recordCounts: BackupRecordCounts
)

data class PreparedRangeWaterRestore(
    val manifest: RangeWaterBackupManifest,
    val data: RangeWaterBackupData
)

data class RestorePreview(
    val createdAt: Long,
    val appVersionName: String,
    val counts: BackupRecordCounts
)
