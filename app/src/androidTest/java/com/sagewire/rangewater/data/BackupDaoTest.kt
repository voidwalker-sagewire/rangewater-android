package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RangeWaterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.backupDao()
    }

    @After
    fun closeDb() = db.close()

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
