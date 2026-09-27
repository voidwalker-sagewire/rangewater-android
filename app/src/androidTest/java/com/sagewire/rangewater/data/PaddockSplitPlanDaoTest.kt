package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sagewire.rangewater.spatial.PaddockSplitEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddockSplitPlanDaoTest {
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
    fun createArchiveReactivateAndDeletePreserveOneActivePlanRule() = runBlocking {
        val pastureId = squarePasture()
        val pasture = db.pastureDao().getById(pastureId)!!
        val start = PaddockSplitEngine.resolveAnchor(pasture, 1, 2, 0.5)
        val end = PaddockSplitEngine.resolveAnchor(pasture, 3, 4, 0.5)
        val dao = db.paddockSplitPlanDao()

        val id = dao.create(pastureId, "Center Split", "West", "East", start, end, now = 100)
        assertNotNull(dao.getById(id))
        assertEquals(1, dao.observeActive().first().size)

        try {
            dao.create(pastureId, "Second", "A", "B", start, end)
            fail("Expected one-active-plan rejection")
        } catch (_: IllegalStateException) {
        }

        dao.archive(id, now = 200)
        assertTrue(dao.observeActive().first().isEmpty())
        dao.reactivate(id, now = 300)
        assertEquals(null, dao.getById(id)?.archivedAt)

        try {
            db.pastureDao().deleteById(pastureId)
            fail("Expected paddock-plan pasture delete blocker")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("paddock split"))
        }

        dao.delete(id)
        assertTrue(dao.plansForPasture(pastureId).isEmpty())
    }

    private suspend fun squarePasture(): Long {
        val now = 1L
        db.pastureDao().insertPasture(PastureEntity(10, "Square", "", now, now))
        db.pastureDao().insertJunctionEntity(FenceJunctionEntity(1, 40.0, -80.0))
        db.pastureDao().insertJunctionEntity(FenceJunctionEntity(2, 40.0, -79.99))
        db.pastureDao().insertJunctionEntity(FenceJunctionEntity(3, 40.01, -79.99))
        db.pastureDao().insertJunctionEntity(FenceJunctionEntity(4, 40.01, -80.0))
        db.pastureDao().insertVertices(
            listOf(
                PastureVertexEntity(pastureId = 10, sequence = 0, junctionId = 1),
                PastureVertexEntity(pastureId = 10, sequence = 1, junctionId = 2),
                PastureVertexEntity(pastureId = 10, sequence = 2, junctionId = 3),
                PastureVertexEntity(pastureId = 10, sequence = 3, junctionId = 4)
            )
        )
        return 10
    }
}
