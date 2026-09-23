package com.sagewire.rangewater.ui.map

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.sagewire.rangewater.data.BackupDestinationRepository
import android.net.Uri
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MapContinuityPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun cameraRoundTripsAndCanBeCleared() {
        val repository = MapCameraStateRepository(context)
        val expected = SavedMapCamera(
            latitude = 40.123456,
            longitude = -80.654321,
            zoom = 15.2,
            bearing = 27.5,
            tilt = 12.0
        )

        assertNull(repository.load())
        repository.save(expected)
        assertEquals(expected, MapCameraStateRepository(context).load())
        repository.clear()
        assertNull(repository.load())
    }

    @Test
    fun backupFolderAndLatestArchiveSurviveRepositoryRecreation() {
        val folder = Uri.parse("content://example/tree/rangewater")
        val archive = Uri.parse("content://example/document/backup.rangewater")
        BackupDestinationRepository(context).apply {
            saveBackupFolder(folder)
            saveLastBackup(archive)
        }

        val recreated = BackupDestinationRepository(context)
        assertEquals(folder, recreated.backupFolder())
        assertEquals(archive, recreated.lastBackup())
    }

    private fun clearPreferences() {
        context.getSharedPreferences("rangewater_map_camera", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("rangewater_backup_destinations", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
}
