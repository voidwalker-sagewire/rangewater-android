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

@RunWith(AndroidJUnit4::class)
class PastureDaoTest {
    private lateinit var database: RangeWaterDatabase
    private lateinit var dao: PastureDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            RangeWaterDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.pastureDao()
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun insertReplaceAndCascadeDeleteRemainAtomic() = runBlocking {
        val original = listOf(
            PastureCoordinate(40.0, -81.0),
            PastureCoordinate(40.0, -80.99),
            PastureCoordinate(40.01, -80.99)
        )
        val id = dao.insertWithVertices("", "", original, now = 1_000L)

        assertEquals("Pasture $id", dao.getById(id)?.pasture?.name)
        assertEquals(original, dao.getById(id)?.vertices?.sortedBy { it.sequence }?.map {
            PastureCoordinate(it.latitude, it.longitude)
        })

        val replacement = original + PastureCoordinate(40.01, -81.0)
        dao.replaceVertices(id, replacement, now = 2_000L)
        assertEquals(4, dao.getById(id)?.vertices?.size)
        assertEquals(2_000L, dao.getById(id)?.pasture?.updatedAt)

        dao.deleteById(id)
        assertNull(dao.getById(id))
        assertEquals(emptyList<PastureWithVertices>(), dao.observeAll().first())
    }
}
