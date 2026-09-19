package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * 🪨 BLOCK 1 — DURABLE WATER RELOCATION TEST
 * Purpose: Proves a coordinate-only update survives a real database close/reopen
 *          while metadata and unrelated assets remain unchanged.
 */
@RunWith(AndroidJUnit4::class)
class WaterPointMoveTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private var database: RangeWaterDatabase? = null

    @Before
    fun prepareDatabase() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "water-move-${UUID.randomUUID()}.db"
    }

    @After
    fun removeDatabase() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun movedCoordinatesSurviveReopenWithoutChangingMetadataOrOtherAssets() = runBlocking {
        val opened = openDatabase()
        val dao = opened.waterPointDao()
        val original = waterPoint(
            id = 1L,
            latitude = 40.10000,
            longitude = -81.20000,
            name = "Creek Tub",
            type = WaterSourceType.TANK,
            notes = "Float valve checked",
            createdAt = 1_000L,
            updatedAt = 1_000L
        )
        val untouched = waterPoint(
            id = 2L,
            latitude = 40.20000,
            longitude = -81.30000,
            name = "South Pond",
            type = WaterSourceType.POND,
            notes = "Seasonal",
            createdAt = 2_000L,
            updatedAt = 2_000L
        )
        dao.insert(original)
        dao.insert(untouched)

        dao.update(
            original.copy(
                latitude = 40.10555,
                longitude = -81.20555,
                updatedAt = 3_000L
            )
        )
        opened.close()
        database = null

        val reopenedDao = openDatabase().waterPointDao()
        val persisted = reopenedDao.getById(original.id)
        val persistedUntouched = reopenedDao.getById(untouched.id)

        assertNotNull(persisted)
        assertEquals(40.10555, persisted!!.latitude, 0.000001)
        assertEquals(-81.20555, persisted.longitude, 0.000001)
        assertEquals(original.name, persisted.name)
        assertEquals(original.sourceType, persisted.sourceType)
        assertEquals(original.notes, persisted.notes)
        assertEquals(original.createdAt, persisted.createdAt)
        assertEquals(3_000L, persisted.updatedAt)
        assertEquals(untouched, persistedUntouched)
    }

    private fun openDatabase(): RangeWaterDatabase = Room.databaseBuilder(
        context,
        RangeWaterDatabase::class.java,
        databaseName
    ).addMigrations(RangeWaterDatabase.MIGRATION_1_2)
        .allowMainThreadQueries()
        .build()
        .also { database = it }

    private fun waterPoint(
        id: Long,
        latitude: Double,
        longitude: Double,
        name: String,
        type: WaterSourceType,
        notes: String,
        createdAt: Long,
        updatedAt: Long
    ) = WaterPointEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        name = name,
        sourceType = type,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
