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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GateDaoTest {
    private lateinit var database: RangeWaterDatabase
    private lateinit var gateDao: GateDao
    private lateinit var junctionDao: FenceJunctionDao
    private lateinit var pastureDao: PastureDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            RangeWaterDatabase::class.java
        ).allowMainThreadQueries().build()
        gateDao = database.gateDao()
        junctionDao = database.fenceJunctionDao()
        pastureDao = database.pastureDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun validatedInsertGeneratesNameAndProtectsAnchorPosts() = runBlocking {
        val (junctionAId, junctionBId) = longSegment()
        val gateId = gateDao.insertValidatedGate(
            GateEntity(name = "", junctionAId = junctionAId, junctionBId = junctionBId, segmentRatio = 0.5)
        )
        assertEquals("Gate $gateId", gateDao.getById(gateId)?.name)

        try {
            junctionDao.deleteById(junctionAId)
            fail("Gate anchor deletion should violate NO ACTION")
        } catch (_: SQLiteConstraintException) {
            // Expected.
        }
    }

    @Test
    fun validatedInsertRejectsShortFenceAndOverlappingGate() = runBlocking {
        val shortA = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val shortB = junctionDao.insert(FenceJunctionEntity(latitude = 40.00001, longitude = -100.0))
        try {
            gateDao.insertValidatedGate(
                GateEntity(name = "Short", junctionAId = shortA, junctionBId = shortB, segmentRatio = 0.5)
            )
            fail("Short fence should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("too short"))
        }

        val (junctionAId, junctionBId) = longSegment()
        gateDao.insertValidatedGate(
            GateEntity(name = "First", junctionAId = junctionAId, junctionBId = junctionBId, segmentRatio = 0.5)
        )
        try {
            gateDao.insertValidatedGate(
                GateEntity(name = "Second", junctionAId = junctionAId, junctionBId = junctionBId, segmentRatio = 0.502)
            )
            fail("Overlapping gate should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("overlaps"))
        }
    }

    @Test
    fun widthEditReclampsAtPostAndRejectsOverlap() = runBlocking {
        val junctionAId = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val junctionBId = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.9998))
        val firstId = gateDao.insertValidatedGate(
            GateEntity(name = "First", junctionAId = junctionAId, junctionBId = junctionBId, segmentRatio = 0.16)
        )
        gateDao.updateValidatedDetails(firstId, "Wide", 6.0, GateEntity.TYPE_PIPE, "", timestamp = 5)
        val updated = gateDao.getById(firstId)
        assertNotNull(updated)
        assertTrue(updated!!.segmentRatio > 0.18)
    }

    @Test
    fun alteringAnchoredEdgeAndMergingEitherPostAreBlocked() = runBlocking {
        val junctionAId = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val junctionBId = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.99))
        val junctionCId = junctionDao.insert(FenceJunctionEntity(latitude = 40.01, longitude = -99.99))
        val pastureId = pasture("Field", listOf(junctionAId, junctionBId, junctionCId))
        gateDao.insertValidatedGate(
            GateEntity(name = "Main", junctionAId = junctionAId, junctionBId = junctionBId, segmentRatio = 0.5)
        )
        val insertedCorner = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.995))
        try {
            pastureDao.savePastureBoundaryWithJunctions(
                pastureId,
                coordinates(junctionAId, insertedCorner, junctionBId, junctionCId),
                emptyMap()
            )
            fail("Subdividing a gated edge should be blocked")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("active gate"))
        }

        try {
            pastureDao.mergeJunctions(junctionAId, insertedCorner)
            fail("Source gate post merge should be blocked")
        } catch (_: IllegalStateException) {
        }
        try {
            pastureDao.mergeJunctions(insertedCorner, junctionBId)
            fail("Target gate post merge should be blocked")
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun pastureDeletionUsesOrderedSharedEdgeRatherThanSharedPosts() = runBlocking {
        val junctionAId = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val junctionBId = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.99))
        val firstC = junctionDao.insert(FenceJunctionEntity(latitude = 40.01, longitude = -99.99))
        val secondC = junctionDao.insert(FenceJunctionEntity(latitude = 39.99, longitude = -99.99))
        val firstPasture = pasture("North", listOf(junctionAId, junctionBId, firstC))
        val secondPasture = pasture("South", listOf(junctionBId, junctionAId, secondC))
        val gateId = gateDao.insertValidatedGate(
            GateEntity(name = "Shared", junctionAId = junctionAId, junctionBId = junctionBId, segmentRatio = 0.5)
        )

        pastureDao.deleteById(firstPasture)
        assertNull(pastureDao.getById(firstPasture))
        assertNotNull(pastureDao.getById(secondPasture))
        assertNotNull(gateDao.getById(gateId))

        try {
            pastureDao.deleteById(secondPasture)
            fail("The last pasture carrying the gated edge should be protected")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("exterior gate"))
        }
    }

    @Test
    fun pastureDeletionIsBlockedWhenAnotherPastureSharesOnlyOnePost() = runBlocking {
        val first = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val second = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.99))
        val third = junctionDao.insert(FenceJunctionEntity(latitude = 40.01, longitude = -99.99))
        val fourth = junctionDao.insert(FenceJunctionEntity(latitude = 40.01, longitude = -100.0))
        val fifth = junctionDao.insert(FenceJunctionEntity(latitude = 40.02, longitude = -100.0))
        val exterior = pasture("Exterior", listOf(first, second, third))
        pasture("Touches Post", listOf(first, fourth, fifth))
        gateDao.insertValidatedGate(
            GateEntity(name = "Exterior", junctionAId = first, junctionBId = second, segmentRatio = 0.5)
        )

        try {
            pastureDao.deleteById(exterior)
            fail("A shared post must not be mistaken for a shared ordered edge")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("exterior gate"))
        }
    }

    private suspend fun longSegment(): Pair<Long, Long> {
        val first = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val second = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.99))
        return minOf(first, second) to maxOf(first, second)
    }

    private suspend fun pasture(name: String, junctionIds: List<Long>): Long {
        val id = pastureDao.insertPasture(PastureEntity(name = name, createdAt = 1, updatedAt = 1))
        junctionIds.forEachIndexed { index, junctionId ->
            pastureDao.insertVertexEntity(
                PastureVertexEntity(pastureId = id, sequence = index, junctionId = junctionId)
            )
        }
        return id
    }

    private suspend fun coordinates(vararg junctionIds: Long): List<PastureCoordinate> =
        junctionIds.map { id ->
            val junction = junctionDao.getById(id)!!
            PastureCoordinate(junction.latitude, junction.longitude, junctionId = id)
        }
}
