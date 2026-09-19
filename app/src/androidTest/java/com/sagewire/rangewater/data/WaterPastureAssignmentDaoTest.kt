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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WaterPastureAssignmentDaoTest {
    private lateinit var database: RangeWaterDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, RangeWaterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun replacementIsAtomicAndDeduplicatesRequestedPastures() = runBlocking {
        val waterId = insertWater()
        val first = insertPasture("North")
        val second = insertPasture("South")
        val third = insertPasture("West")
        val dao = database.waterPastureAssignmentDao()

        dao.replaceForWaterPoint(waterId, listOf(first, second, first), assignedAt = 100L)
        assertEquals(setOf(first, second), dao.getPastureIdsForWaterPoint(waterId).toSet())

        dao.replaceForWaterPoint(waterId, listOf(second, third), assignedAt = 200L)
        assertEquals(setOf(second, third), dao.getPastureIdsForWaterPoint(waterId).toSet())

        dao.replaceForWaterPoint(waterId, emptyList(), assignedAt = 300L)
        assertTrue(dao.getPastureIdsForWaterPoint(waterId).isEmpty())
    }

    @Test
    fun deletingWaterCascadesItsAssignments() = runBlocking {
        val waterId = insertWater()
        val pastureId = insertPasture("North")
        val dao = database.waterPastureAssignmentDao()
        dao.replaceForWaterPoint(waterId, listOf(pastureId), assignedAt = 100L)

        database.waterPointDao().deleteById(waterId)

        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun deletingPastureCascadesItsAssignments() = runBlocking {
        val waterId = insertWater()
        val pastureId = insertPasture("North")
        val dao = database.waterPastureAssignmentDao()
        dao.replaceForWaterPoint(waterId, listOf(pastureId), assignedAt = 100L)

        database.pastureDao().deleteById(pastureId)

        assertTrue(dao.observeAll().first().isEmpty())
    }

    private suspend fun insertWater(): Long = database.waterPointDao().insertWithDefaultName(
        latitude = 40.0,
        longitude = -100.0,
        now = 10L
    )

    private suspend fun insertPasture(name: String): Long = database.pastureDao().insertWithVertices(
        requestedName = name,
        notes = "",
        vertices = listOf(
            PastureCoordinate(39.99, -100.01),
            PastureCoordinate(40.01, -100.01),
            PastureCoordinate(40.0, -99.99)
        ),
        now = 20L
    )
}
