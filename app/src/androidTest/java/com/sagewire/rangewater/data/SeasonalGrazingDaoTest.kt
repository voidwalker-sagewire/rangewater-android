package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeasonalGrazingDaoTest {
    private lateinit var db: RangeWaterDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RangeWaterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun circuitPersistsOrderMultipleRolesAndOneAssignmentPerHerd() = runBlocking {
        val north = pasture("North")
        val south = pasture("South")
        val herd = db.herdDao().createHerd(
            HerdEntity(
                name = "Cow Herd",
                quantity = 40,
                countUnit = CountUnit.PAIRS,
                stockClass = StockClass.COW_CALF_PAIRS,
                markerColorHex = "#FF2A92",
                locationKind = HerdLocationKind.PASTURE,
                currentPastureId = north
            )
        )
        val winter = db.grazingCircuitDao().createCircuit(
            name = "Winter Circuit",
            notes = "Near the feed pad",
            pastures = listOf(
                GrazingCircuitPastureDraft(
                    north,
                    setOf(SeasonalPastureRole.WINTER, SeasonalPastureRole.STOCKPILED_WINTER)
                ),
                GrazingCircuitPastureDraft(south, setOf(SeasonalPastureRole.CALVING))
            ),
            now = 100
        )
        val summer = db.grazingCircuitDao().createCircuit(
            name = "Summer Rotation",
            notes = "",
            pastures = listOf(GrazingCircuitPastureDraft(south, setOf(SeasonalPastureRole.ROTATION))),
            now = 101
        )

        db.grazingCircuitDao().assignHerd(herd, winter, now = 102)
        db.grazingCircuitDao().assignHerd(herd, summer, now = 103)

        assertEquals(listOf(north, south), db.grazingCircuitDao().membershipsForCircuit(winter).map { it.pastureId })
        assertEquals(
            setOf(SeasonalPastureRole.WINTER, SeasonalPastureRole.STOCKPILED_WINTER),
            db.grazingCircuitDao().rolesForCircuit(winter).filter { it.pastureId == north }.map { it.role }.toSet()
        )
        assertEquals(summer, db.grazingCircuitDao().assignmentForHerd(herd)?.circuitId)
        assertEquals(1, db.grazingCircuitDao().observeAllHerdAssignments().first().size)
    }

    @Test
    fun duplicatePastureAndAssignedCircuitArchiveAreRejected() = runBlocking {
        val pasture = pasture("North")
        val herd = db.herdDao().createHerd(
            HerdEntity(
                name = "Cow Herd",
                quantity = 30,
                countUnit = CountUnit.HEAD,
                stockClass = StockClass.DRY_COWS,
                markerColorHex = "#FF9100",
                locationKind = HerdLocationKind.PASTURE,
                currentPastureId = pasture
            )
        )
        try {
            db.grazingCircuitDao().createCircuit(
                "Broken",
                "",
                listOf(GrazingCircuitPastureDraft(pasture), GrazingCircuitPastureDraft(pasture))
            )
            fail("Expected duplicate-pasture rejection")
        } catch (_: IllegalArgumentException) {
        }

        val circuit = db.grazingCircuitDao().createCircuit(
            "Cow Rotation",
            "",
            listOf(GrazingCircuitPastureDraft(pasture))
        )
        db.grazingCircuitDao().assignHerd(herd, circuit)
        try {
            db.grazingCircuitDao().archiveCircuit(circuit)
            fail("Expected assigned-circuit archive rejection")
        } catch (_: IllegalStateException) {
        }
        assertEquals(null, db.grazingCircuitDao().getCircuit(circuit)?.archivedAt)
    }

    @Test
    fun forageObservationRequiresExactOhioCalibrationAndBlocksPastureDeletion() = runBlocking {
        val pasture = pasture("Stockpile")
        val valid = PastureForageObservationEntity(
            pastureId = pasture,
            observedAt = 100,
            averageHeightInches = 9.0,
            sampleCount = 8,
            forageStandType = ForageStandType.TALL_FESCUE_CLOVER,
            standCondition = ForageStandCondition.GOOD,
            dmPerAcreInchLow = 300.0,
            dmPerAcreInchHigh = 350.0,
            calibrationSource = ForageCalibrationSource.OHIO_NRCS_GLCI_GRAZING_STICK,
            acreageSnapshot = 15.0
        )
        val id = db.forageObservationDao().record(valid)
        assertEquals(id, db.forageObservationDao().latestForPasture(pasture)?.id)

        db.pastureDao().replaceVertices(
            pasture,
            listOf(
                PastureCoordinate(40.0, -80.0),
                PastureCoordinate(40.0, -79.98),
                PastureCoordinate(40.02, -79.98)
            )
        )
        assertEquals(15.0, db.forageObservationDao().latestForPasture(pasture)?.acreageSnapshot ?: 0.0, 0.0)

        try {
            db.forageObservationDao().record(valid.copy(id = 0, dmPerAcreInchLow = 299.0))
            fail("Expected altered-preset rejection")
        } catch (_: IllegalArgumentException) {
        }

        try {
            db.pastureDao().deleteById(pasture)
            fail("Expected forage-history delete blocker")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("forage observations"))
        }
    }

    @Test
    fun failedCircuitUpdateRollsBackAndHerdArchiveClearsOnlyActiveAssignment() = runBlocking {
        val north = pasture("North")
        val south = pasture("South")
        val herd = db.herdDao().createHerd(
            HerdEntity(
                name = "Cow Herd",
                quantity = 30,
                countUnit = CountUnit.HEAD,
                stockClass = StockClass.DRY_COWS,
                markerColorHex = "#FF9100",
                locationKind = HerdLocationKind.PASTURE,
                currentPastureId = north
            )
        )
        val movement = db.movementDao().insertMovementRaw(
            CattleMovementEntity(
                herdId = herd,
                originLocationKind = HerdLocationKind.OFF_RANCH,
                originNameSnapshot = "OFF_RANCH",
                destinationLocationKind = HerdLocationKind.PASTURE,
                destinationPastureId = north,
                destinationNameSnapshot = "North",
                quantity = 30,
                countUnit = CountUnit.HEAD,
                status = MovementStatus.COMPLETED,
                completedAt = 100,
                unmappedRoute = true,
                notes = "arrival"
            )
        )
        val circuit = db.grazingCircuitDao().createCircuit(
            "Cow Rotation",
            "original",
            listOf(GrazingCircuitPastureDraft(north), GrazingCircuitPastureDraft(south))
        )
        db.grazingCircuitDao().assignHerd(herd, circuit)

        try {
            db.grazingCircuitDao().updateCircuit(
                circuit,
                "Changed",
                "must roll back",
                listOf(GrazingCircuitPastureDraft(999_999))
            )
            fail("Expected missing-pasture rejection")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals("Cow Rotation", db.grazingCircuitDao().getCircuit(circuit)?.name)
        assertEquals(listOf(north, south), db.grazingCircuitDao().membershipsForCircuit(circuit).map { it.pastureId })

        db.herdDao().archiveHerdWithChecks(herd, timestamp = 200)
        assertEquals(null, db.grazingCircuitDao().assignmentForHerd(herd))
        assertEquals("arrival", db.movementDao().getById(movement)?.notes)
        assertEquals(200L, db.herdDao().getById(herd)?.archivedAt)
    }

    private suspend fun pasture(name: String): Long = db.pastureDao().insertWithVertices(
        requestedName = name,
        notes = "",
        vertices = listOf(
            PastureCoordinate(40.0, -80.0),
            PastureCoordinate(40.0, -79.99),
            PastureCoordinate(40.01, -79.99)
        )
    )
}
