package com.sagewire.rangewater.data

import android.content.Context
import com.sagewire.rangewater.ui.map.DisplayPreferencesRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class RangeWaterBackupManager(
    private val context: Context,
    private val database: RangeWaterDatabase,
    private val preferencesRepository: DisplayPreferencesRepository
) {
    suspend fun writeManualBackup(output: OutputStream): RangeWaterBackupManifest {
        val data = database.backupDao().snapshot().copy(
            displayPreferences = preferencesRepository.getPreferences()
        )
        return RangeWaterArchiveCodec.write(output, data, appVersionName())
    }

    fun prepareRestore(input: InputStream): PreparedRangeWaterRestore =
        RangeWaterArchiveCodec.read(input)

    suspend fun restore(prepared: PreparedRangeWaterRestore) {
        RangeWaterBackupValidator.validate(prepared.data)
        val previous = database.backupDao().snapshot().copy(
            displayPreferences = preferencesRepository.getPreferences()
        )
        writeEmergencyBackup(previous)

        database.backupDao().replaceAll(prepared.data)
        if (!preferencesRepository.replacePreferences(prepared.data.displayPreferences)) {
            database.backupDao().replaceAll(previous)
            preferencesRepository.replacePreferences(previous.displayPreferences)
            throw IllegalStateException("Display settings could not be restored; ranch records were returned to their prior state")
        }
    }

    fun hasEmergencyBackup(): Boolean = latestEmergencyBackup() != null

    fun prepareLatestEmergencyRestore(): PreparedRangeWaterRestore {
        val file = latestEmergencyBackup() ?: throw IllegalStateException("No emergency rollback is available")
        return FileInputStream(file).use(RangeWaterArchiveCodec::read)
    }

    private fun writeEmergencyBackup(data: RangeWaterBackupData) {
        val directory = File(context.filesDir, EMERGENCY_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) { "Could not create the emergency backup directory" }
        val target = File(directory, "pre-restore-${System.currentTimeMillis()}${RangeWaterArchiveCodec.FILE_EXTENSION}")
        val temporary = File(directory, "${target.name}.tmp")
        try {
            FileOutputStream(temporary).use { RangeWaterArchiveCodec.write(it, data, appVersionName()) }
            check(temporary.renameTo(target)) { "Could not finalize the emergency backup" }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == RangeWaterArchiveCodec.FILE_EXTENSION.removePrefix(".") }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_EMERGENCY_BACKUPS)
            .forEach(File::delete)
    }

    private fun latestEmergencyBackup(): File? = File(context.filesDir, EMERGENCY_DIRECTORY)
        .listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension == RangeWaterArchiveCodec.FILE_EXTENSION.removePrefix(".") }
        .maxByOrNull { it.lastModified() }

    private fun appVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    companion object {
        private const val EMERGENCY_DIRECTORY = "emergency_backups"
        private const val MAX_EMERGENCY_BACKUPS = 3
    }
}
