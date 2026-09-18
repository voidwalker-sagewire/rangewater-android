package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val databaseName = "rangewater-migration-test"
    private lateinit var context: Context

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationOneToTwoPreservesWaterAndCreatesPastureSchema() = runBlocking {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `water_points` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `name` TEXT NOT NULL,
                    `sourceType` TEXT NOT NULL,
                    `notes` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "INSERT INTO water_points VALUES (1, 40.0, -100.0, 'Old Tank', 'TANK', '', 10, 10)"
            )
            database.version = 1
        }

        val migrated = Room.databaseBuilder(context, RangeWaterDatabase::class.java, databaseName)
            .addMigrations(RangeWaterDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val water = migrated.waterPointDao().getById(1)
            assertNotNull(water)
            assertEquals("Old Tank", water?.name)
            assertEquals(WaterSourceType.TANK, water?.sourceType)

            val pastureId = migrated.pastureDao().insertWithVertices(
                "Migration Field",
                "",
                listOf(
                    PastureCoordinate(40.0, -100.0),
                    PastureCoordinate(40.0, -99.99),
                    PastureCoordinate(40.01, -99.99)
                ),
                now = 20
            )
            assertEquals("Migration Field", migrated.pastureDao().getById(pastureId)?.pasture?.name)
        } finally {
            migrated.close()
        }
    }
}
