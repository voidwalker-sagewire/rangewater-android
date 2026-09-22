package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sagewire.rangewater.ui.map.DisplayPreferences
import com.sagewire.rangewater.ui.map.DisplayPreferencesRepository
import com.sagewire.rangewater.ui.map.SpatialCoverageScope
import com.sagewire.rangewater.ui.map.WaterCoverageMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupDaoTest {
    private lateinit var db: RangeWaterDatabase
    private lateinit var dao: BackupDao
    private lateinit var context: Context
    private lateinit var preferences: DisplayPreferencesRepository

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(DisplayPreferencesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        preferences = DisplayPreferencesRepository(context)
        db = Room.inMemoryDatabaseBuilder(context, RangeWaterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.backupDao()
    }

    @After
    fun closeDb() {
        db.close()
        context.getSharedPreferences(DisplayPreferencesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.filesDir.resolve("emergency_backups").deleteRecursively()
    }

    @Test
    fun replaceAll_roundTripPreservesIdsRelationshipsAndHistory() = runBlocking {
        val expected = completeData()
        dao.replaceAll(expected)
        val exported = dao.snapshot()
        dao.replaceAll(RangeWaterBackupData())
        dao.replaceAll(exported)

        assertEquals(expected, dao.snapshot())
    }

    @Test
    fun replaceAll_foreignKeyFailureRollsBackExistingDatabase() = runBlocking {
        val original = completeData()
        dao.replaceAll(original)
        val broken = RangeWaterBackupData(
            assignments = listOf(WaterPastureAssignmentEntity(999, 998, 100L))
        )

        try {
            dao.replaceAll(broken)
            fail("Expected foreign-key failure")
        } catch (_: Exception) {
            // The @Transaction boundary must restore all prior rows.
        }

        assertEquals(original, dao.snapshot())
    }

    @Test
    fun managerRestore_replacesRecordsPreferencesAndCreatesEmergencyRollback() = runBlocking {
        val manager = RangeWaterBackupManager(context, db, preferences)
        val original = completeData()
        dao.replaceAll(original)
        preferences.replacePreferences(
            DisplayPreferences(WaterCoverageMode.FULL, SpatialCoverageScope.PHYSICAL_RADIUS, true)
        )

        val replacement = original.copy(
            waterPoints = listOf(original.waterPoints.single().copy(name = "Restored Tank")),
            displayPreferences = DisplayPreferences(
                WaterCoverageMode.LINES_ONLY,
                SpatialCoverageScope.ACCESSIBLE_COVERAGE,
                false
            )
        )
        val archiveBytes = RangeWaterArchiveCodec.toByteArray(replacement, "0.12.0")
        val prepared = manager.prepareRestore(ByteArrayInputStream(archiveBytes))

        manager.restore(prepared)

        assertEquals("Restored Tank", dao.allWaterPoints().single().name)
        assertEquals(replacement.displayPreferences, preferences.getPreferences())
        assertEquals(true, manager.hasEmergencyBackup())

        manager.restore(manager.prepareLatestEmergencyRestore())
        assertEquals("Tank 1", dao.allWaterPoints().single().name)
        assertEquals(DisplayPreferences(), preferences.getPreferences())
    }

    @Test
    fun managerManualBackup_roundTripsCurrentDatabaseAndPreferences() = runBlocking {
        val manager = RangeWaterBackupManager(context, db, preferences)
        val original = completeData()
        dao.replaceAll(original)
        val expectedPreferences = DisplayPreferences(
            WaterCoverageMode.OFF,
            SpatialCoverageScope.ACCESSIBLE_COVERAGE,
            false
        )
        preferences.replacePreferences(expectedPreferences)
        val output = ByteArrayOutputStream()

        manager.writeManualBackup(output)
        val restored = manager.prepareRestore(ByteArrayInputStream(output.toByteArray()))

        assertEquals(original.copy(displayPreferences = expectedPreferences), restored.data)
    }

    private fun completeData(): RangeWaterBackupData {
        val now = 100L
        return RangeWaterBackupData(
            waterPoints = listOf(WaterPointEntity(1, 40.0, -100.0, "Tank 1", WaterSourceType.TANK, "", now, now)),
            pastures = listOf(PastureEntity(10, "North", "", now, now)),
            junctions = listOf(
                FenceJunctionEntity(20, 40.0, -100.0),
                FenceJunctionEntity(21, 40.0, -99.99),
                FenceJunctionEntity(22, 40.01, -99.99)
            ),
            vertices = listOf(
                PastureVertexEntity(30, 10, 0, 20),
                PastureVertexEntity(31, 10, 1, 21),
                PastureVertexEntity(32, 10, 2, 22)
            ),
            assignments = listOf(WaterPastureAssignmentEntity(1, 10, now)),
            gates = listOf(GateEntity(40, "North Gate", 20, 21, 0.5, createdAt = now, updatedAt = now)),
            herds = listOf(
                HerdEntity(
                    50, "Pairs", 30, CountUnit.PAIRS, StockClass.COW_CALF_PAIRS,
                    markerColorHex = "#FF9100", locationKind = HerdLocationKind.PASTURE,
                    currentPastureId = 10, createdAt = now, updatedAt = now
                )
            ),
            movements = listOf(
                CattleMovementEntity(
                    60, 50, HerdLocationKind.PASTURE, 10, "North", HerdLocationKind.PEN,
                    null, "PEN", 30, CountUnit.PAIRS, MovementStatus.COMPLETED,
                    completedAt = now, gateId = 40, gateSnapshot = "Gate #40 - North Gate",
                    createdAt = now, updatedAt = now
                )
            )
        )
    }
}
