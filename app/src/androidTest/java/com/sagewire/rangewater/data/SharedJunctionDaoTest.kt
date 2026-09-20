package com.sagewire.rangewater.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedJunctionDaoTest {
    private lateinit var database: RangeWaterDatabase
    private lateinit var pastureDao: PastureDao
    private lateinit var junctionDao: FenceJunctionDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            RangeWaterDatabase::class.java
        ).allowMainThreadQueries().build()
        pastureDao = database.pastureDao()
        junctionDao = database.fenceJunctionDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun referencedJunctionCannotBeDeletedAndDuplicateReferenceAborts() = runBlocking {
        val junctionId = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val pastureId = pastureDao.insertPasture(PastureEntity(name = "Field", createdAt = 1, updatedAt = 1))
        pastureDao.insertVertexEntity(PastureVertexEntity(pastureId = pastureId, sequence = 0, junctionId = junctionId))

        try {
            junctionDao.deleteById(junctionId)
            fail("NO_ACTION must protect a referenced junction")
        } catch (_: SQLiteConstraintException) {
        }
        try {
            pastureDao.insertVertexEntity(PastureVertexEntity(pastureId = pastureId, sequence = 1, junctionId = junctionId))
            fail("ABORT must reject duplicate pasture/junction references")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun atomicSharedMoveUpdatesBothPasturesAndRollsBackOnConstraintFailure() = runBlocking {
        val shared = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val a2 = junctionDao.insert(FenceJunctionEntity(latitude = 40.01, longitude = -100.0))
        val a3 = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.99))
        val b2 = junctionDao.insert(FenceJunctionEntity(latitude = 39.99, longitude = -100.0))
        val b3 = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.01))
        val p1 = createPasture("P1", 100, listOf(shared, a2, a3))
        val p2 = createPasture("P2", 200, listOf(shared, b2, b3))

        pastureDao.savePastureBoundaryWithJunctions(
            p1,
            listOf(
                PastureCoordinate(40.005, -100.005, junctionId = shared),
                PastureCoordinate(40.01, -100.0, junctionId = a2),
                PastureCoordinate(40.0, -99.99, junctionId = a3)
            ),
            mapOf(shared to PastureCoordinate(40.005, -100.005, junctionId = shared)),
            now = 500
        )
        assertEquals(40.005, pastureDao.getById(p2)!!.vertices.first { it.junctionId == shared }.latitude, 0.000001)
        assertEquals(500L, pastureDao.getById(p1)!!.pasture.updatedAt)
        assertEquals(500L, pastureDao.getById(p2)!!.pasture.updatedAt)

        try {
            pastureDao.savePastureBoundaryWithJunctions(
                p1,
                listOf(
                    PastureCoordinate(41.0, -101.0, junctionId = shared),
                    PastureCoordinate(41.0, -101.0, junctionId = shared),
                    PastureCoordinate(40.0, -99.99, junctionId = a3)
                ),
                mapOf(shared to PastureCoordinate(41.0, -101.0, junctionId = shared)),
                now = 999
            )
            fail("Duplicate junction should roll back the whole save")
        } catch (_: SQLiteConstraintException) {
        }
        assertEquals(40.005, junctionDao.getById(shared)!!.latitude, 0.000001)
        assertEquals(500L, pastureDao.getById(p1)!!.pasture.updatedAt)
    }

    @Test
    fun deletingOnePastureCleansOnlyOrphansAndMergeRejectsDuplicates() = runBlocking {
        val shared = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val onlyA = junctionDao.insert(FenceJunctionEntity(latitude = 40.01, longitude = -100.0))
        val onlyB = junctionDao.insert(FenceJunctionEntity(latitude = 39.99, longitude = -100.0))
        val p1 = createPasture("P1", 1, listOf(shared, onlyA, junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.99))))
        createPasture("P2", 2, listOf(shared, onlyB, junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.01))))

        try {
            pastureDao.mergeJunctions(shared, onlyA)
            fail("A pasture cannot reference the surviving junction twice")
        } catch (_: IllegalStateException) {
        }
        pastureDao.deleteById(p1)
        assertNull(junctionDao.getById(onlyA))
        assertNotNull(junctionDao.getById(shared))
    }

    private suspend fun createPasture(name: String, timestamp: Long, junctions: List<Long>): Long {
        val pastureId = pastureDao.insertPasture(
            PastureEntity(name = name, createdAt = timestamp, updatedAt = timestamp)
        )
        junctions.forEachIndexed { sequence, junctionId ->
            pastureDao.insertVertexEntity(PastureVertexEntity(pastureId = pastureId, sequence = sequence, junctionId = junctionId))
        }
        return pastureId
    }
}
