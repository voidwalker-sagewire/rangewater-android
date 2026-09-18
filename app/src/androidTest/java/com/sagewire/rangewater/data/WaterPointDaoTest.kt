package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * 🪨 BLOCK 1 — REAL ROOM DATABASE TEST
 * Purpose: Exercises generated SQL, atomic naming, Flow reads, updates, and deletion
 *          against an in-memory Android SQLite database.
 */
@RunWith(AndroidJUnit4::class)
class WaterPointDaoTest {
    private lateinit var database: RangeWaterDatabase
    private lateinit var dao: WaterPointDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            RangeWaterDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.waterPointDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertUpdateAndDeletePreserveExpectedRecordState() = runBlocking {
        val firstId = dao.insertWithDefaultName(40.3600, -80.6300, now = 1_000L)
        val secondId = dao.insertWithDefaultName(40.3650, -80.6350, now = 2_000L)

        assertEquals(1L, firstId)
        assertEquals(2L, secondId)
        val inserted = dao.observeAll().first()
        assertEquals(listOf("Water Point 1", "Water Point 2"), inserted.map { it.name })

        val updated = inserted.first().copy(
            name = "North Pasture Tank",
            sourceType = WaterSourceType.TANK,
            notes = "Float sticks in winter",
            updatedAt = 3_000L
        )
        dao.update(updated)

        assertEquals(updated, dao.getById(firstId))
        dao.deleteById(firstId)
        assertNull(dao.getById(firstId))
        assertEquals(listOf(secondId), dao.observeAll().first().map { it.id })
    }
}
