package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovementDaoTest {
    private lateinit var db: RangeWaterDatabase
    private val herdDao get() = db.herdDao()
    private val movementDao get() = db.movementDao()
    private val pastureDao get() = db.pastureDao()
    private val junctionDao get() = db.fenceJunctionDao()
    private val gateDao get() = db.gateDao()

    @Before
    fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), RangeWaterDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDatabase() = db.close()

    @Test
    fun plannedMoveDoesNotDriftAndCompletionIsAtomic() = runBlocking {
        val geometry = twoPasturesWithSharedGate()
        val herdId = herdDao.createHerd(herd("Pairs", geometry.firstPasture, CountUnit.PAIRS))
        val moveId = movementDao.scheduleOrLogMovement(
            herdId, HerdLocationKind.PASTURE, geometry.secondPasture, geometry.gateId,
            unmappedRoute = false, plannedAt = 1000, completedImmediately = false, notes = "Spring move"
        )
        assertEquals(geometry.firstPasture, herdDao.getById(herdId)?.currentPastureId)
        movementDao.completePlannedMovement(moveId, 2000, "Moved safely")
        assertEquals(geometry.secondPasture, herdDao.getById(herdId)?.currentPastureId)
        val movement = movementDao.getById(moveId)!!
        assertEquals(MovementStatus.COMPLETED, movement.status)
        assertEquals(2000, movement.completedAt)
        assertEquals("Field A", movement.originNameSnapshot)
        assertEquals("Field B", movement.destinationNameSnapshot)
        assertEquals(CountUnit.PAIRS, movement.countUnit)
    }

    @Test
    fun unrelatedGateIsRejected() = runBlocking {
        val geometry = twoPasturesWithSharedGate()
        val a = junctionDao.insert(FenceJunctionEntity(latitude = 39.0, longitude = -99.0))
        val b = junctionDao.insert(FenceJunctionEntity(latitude = 39.0, longitude = -98.99))
        val unrelatedGate = gateDao.insertValidatedGate(GateEntity(name = "Far Away", junctionAId = minOf(a, b), junctionBId = maxOf(a, b), segmentRatio = .5))
        val herdId = herdDao.createHerd(herd("Stockers", geometry.firstPasture))
        expectFailure("does not connect origin pasture") {
            movementDao.scheduleOrLogMovement(herdId, HerdLocationKind.PASTURE, geometry.secondPasture, unrelatedGate, false, 1000, false, "")
        }
    }

    @Test
    fun plannedMovementProtectsGateRelocationAndDeletion() = runBlocking {
        val (pasture, gate) = perimeterPastureAndGate()
        val herdId = herdDao.createHerd(herd("Shipping", pasture))
        movementDao.scheduleOrLogMovement(herdId, HerdLocationKind.OFF_RANCH, null, gate, false, 1000, false, "Shipping")
        val gateEntity = gateDao.getById(gate)!!
        expectFailure("active planned cattle movement") {
            gateDao.updateValidatedPosition(gate, gateEntity.junctionAId, gateEntity.junctionBId, .4)
        }
        expectFailure("active planned movement") { gateDao.deleteGateWithChecks(gateEntity) }
    }

    @Test
    fun archiveClearsLocationAfterPlanIsCanceled() = runBlocking {
        val pasture = pastureDao.insertPasture(PastureEntity(name = "Retirement", createdAt = 1, updatedAt = 1))
        val herdId = herdDao.createHerd(herd("Retiring", pasture))
        val moveId = movementDao.scheduleOrLogMovement(herdId, HerdLocationKind.OFF_RANCH, null, null, true, 1000, false, "Sale barn")
        expectFailure("active planned movements") { herdDao.archiveHerdWithChecks(herdId) }
        movementDao.cancelPlannedMovement(moveId)
        herdDao.archiveHerdWithChecks(herdId)
        val archived = herdDao.getById(herdId)!!
        assertEquals(HerdLocationKind.UNKNOWN, archived.locationKind)
        assertNull(archived.currentPastureId)
        assertNotNull(archived.archivedAt)
        pastureDao.deleteById(pasture)
        assertNull(pastureDao.getPastureEntity(pasture))
    }

    @Test
    fun unoccupiedDestinationPastureIsProtectedByPlan() = runBlocking {
        val destination = pastureDao.insertPasture(PastureEntity(name = "Future Field", createdAt = 1, updatedAt = 1))
        val herdId = herdDao.createHerd(
            HerdEntity(name = "Arriving", quantity = 20, countUnit = CountUnit.HEAD, stockClass = StockClass.OTHER,
                markerColorHex = "#FF9100", locationKind = HerdLocationKind.OFF_RANCH)
        )
        movementDao.scheduleOrLogMovement(herdId, HerdLocationKind.PASTURE, destination, null, true, 1000, false, "Truck delivery")
        expectFailure("active planned cattle movement") { pastureDao.deleteById(destination) }
    }

    @Test
    fun exteriorToPastureUsesPerimeterGate() = runBlocking {
        val (pasture, gate) = perimeterPastureAndGate()
        val herdId = herdDao.createHerd(
            HerdEntity(name = "Arriving", quantity = 100, countUnit = CountUnit.HEAD,
                stockClass = StockClass.STOCKERS_YEARLINGS, markerColorHex = "#00E5FF",
                locationKind = HerdLocationKind.OFF_RANCH)
        )
        movementDao.scheduleOrLogMovement(herdId, HerdLocationKind.PASTURE, pasture, gate, false, 1000, true, "Delivery")
        assertEquals(pasture, herdDao.getById(herdId)?.currentPastureId)
        assertEquals(HerdLocationKind.PASTURE, herdDao.getById(herdId)?.locationKind)
    }

    @Test
    fun completedHistorySurvivesGateAndPastureDeletion() = runBlocking {
        val (pasture, gateId) = perimeterPastureAndGate("Old Field", "Historic Gate")
        val herdId = herdDao.createHerd(herd("Trail Herd", pasture))
        val moveId = movementDao.scheduleOrLogMovement(herdId, HerdLocationKind.PEN, null, gateId, false, 1000, true, "Pens")
        gateDao.deleteGateWithChecks(gateDao.getById(gateId)!!)
        pastureDao.deleteById(pasture)
        val movement = movementDao.getById(moveId)!!
        assertNull(movement.originPastureId)
        assertNull(movement.gateId)
        assertEquals("Old Field", movement.originNameSnapshot)
        assertEquals("Gate #$gateId - Historic Gate", movement.gateSnapshot)
    }

    private suspend fun twoPasturesWithSharedGate(): SharedGeometry {
        val j1 = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val j2 = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.99))
        val j3 = junctionDao.insert(FenceJunctionEntity(latitude = 40.01, longitude = -99.99))
        val j4 = junctionDao.insert(FenceJunctionEntity(latitude = 39.99, longitude = -99.99))
        val first = createPasture("Field A", listOf(j1, j2, j3))
        val second = createPasture("Field B", listOf(j2, j1, j4))
        val gate = gateDao.insertValidatedGate(GateEntity(name = "Mid Gate", junctionAId = minOf(j1, j2), junctionBId = maxOf(j1, j2), segmentRatio = .5))
        return SharedGeometry(first, second, gate)
    }

    private suspend fun perimeterPastureAndGate(pastureName: String = "Field", gateName: String = "Perimeter Gate"): Pair<Long, Long> {
        val j1 = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -100.0))
        val j2 = junctionDao.insert(FenceJunctionEntity(latitude = 40.0, longitude = -99.99))
        val j3 = junctionDao.insert(FenceJunctionEntity(latitude = 40.01, longitude = -99.99))
        val pasture = createPasture(pastureName, listOf(j1, j2, j3))
        val gate = gateDao.insertValidatedGate(GateEntity(name = gateName, junctionAId = minOf(j1, j2), junctionBId = maxOf(j1, j2), segmentRatio = .5))
        return pasture to gate
    }

    private suspend fun createPasture(name: String, junctions: List<Long>): Long {
        val id = pastureDao.insertPasture(PastureEntity(name = name, createdAt = 1, updatedAt = 1))
        junctions.forEachIndexed { sequence, junction -> pastureDao.insertVertexEntity(PastureVertexEntity(pastureId = id, sequence = sequence, junctionId = junction)) }
        return id
    }

    private fun herd(name: String, pasture: Long, unit: CountUnit = CountUnit.HEAD) = HerdEntity(
        name = name, quantity = 42, countUnit = unit, stockClass = StockClass.MIXED,
        markerColorHex = "#FF9100", locationKind = HerdLocationKind.PASTURE, currentPastureId = pasture
    )

    private suspend fun expectFailure(message: String, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected failure containing: $message")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains(message, ignoreCase = true))
        }
    }

    private data class SharedGeometry(val firstPasture: Long, val secondPasture: Long, val gateId: Long)
}
