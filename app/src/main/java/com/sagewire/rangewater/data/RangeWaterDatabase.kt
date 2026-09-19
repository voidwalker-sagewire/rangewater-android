package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * 🪨 BLOCK 1 — DURABLE LOCAL DATABASE
 * Purpose: Owns RangeWater's device-local SQLite database.
 * 🪨 Protected: No destructive-migration fallback; saved ranch data must not be erased.
 */
@Database(
    entities = [
        WaterPointEntity::class,
        PastureEntity::class,
        PastureVertexEntity::class,
        WaterPastureAssignmentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RangeWaterDatabase : RoomDatabase() {
    abstract fun waterPointDao(): WaterPointDao
    abstract fun pastureDao(): PastureDao
    abstract fun waterPastureAssignmentDao(): WaterPastureAssignmentDao

    companion object {
        @Volatile
        private var instance: RangeWaterDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pastures` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pasture_vertices` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pastureId` INTEGER NOT NULL,
                        `sequence` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `elevationMeters` REAL,
                        `elevationSource` TEXT,
                        `verticalDatum` TEXT,
                        `verticalAccuracyMeters` REAL,
                        `elevationCapturedAt` INTEGER,
                        FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pasture_vertices_pastureId` " +
                        "ON `pasture_vertices` (`pastureId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pasture_vertices_pastureId_sequence` " +
                        "ON `pasture_vertices` (`pastureId`, `sequence`)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `water_pasture_assignments` (
                        `waterPointId` INTEGER NOT NULL,
                        `pastureId` INTEGER NOT NULL,
                        `assignedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`waterPointId`, `pastureId`),
                        FOREIGN KEY(`waterPointId`) REFERENCES `water_points`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_water_pasture_assignments_waterPointId` " +
                        "ON `water_pasture_assignments` (`waterPointId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_water_pasture_assignments_pastureId` " +
                        "ON `water_pasture_assignments` (`pastureId`)"
                )
            }
        }

        fun getDatabase(context: Context): RangeWaterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RangeWaterDatabase::class.java,
                    "rangewater_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
