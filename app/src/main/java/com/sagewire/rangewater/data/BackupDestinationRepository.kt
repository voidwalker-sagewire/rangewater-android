package com.sagewire.rangewater.data

import android.content.Context
import android.net.Uri

/** Stores only user-granted Storage Access Framework locations, never account credentials. */
class BackupDestinationRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun backupFolder(): Uri? = preferences.getString(KEY_BACKUP_FOLDER, null)?.let(Uri::parse)

    fun saveBackupFolder(uri: Uri) {
        preferences.edit().putString(KEY_BACKUP_FOLDER, uri.toString()).apply()
    }

    fun lastBackup(): Uri? = preferences.getString(KEY_LAST_BACKUP, null)?.let(Uri::parse)

    fun saveLastBackup(uri: Uri) {
        preferences.edit().putString(KEY_LAST_BACKUP, uri.toString()).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "rangewater_backup_destinations"
        private const val KEY_BACKUP_FOLDER = "backup_folder_uri"
        private const val KEY_LAST_BACKUP = "last_backup_uri"
    }
}
