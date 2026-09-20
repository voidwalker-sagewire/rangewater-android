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
        FenceJunctionEntity::class,
        PastureVertexEntity::class,
        WaterPastureAssignmentEntity::class,
        GateEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class RangeWaterDatabase : RoomDatabase() {
    abstract fun waterPointDao(): WaterPointDao
    abstract fun pastureDao(): PastureDao
    abstract fun fenceJunctionDao(): FenceJunctionDao
    abstract fun waterPastureAssignmentDao(): WaterPastureAssignmentDao
    abstract fun gateDao(): GateDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fence_junctions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `elevationMeters` REAL,
                        `elevationSource` TEXT,
                        `verticalDatum` TEXT,
                        `verticalAccuracyMeters` REAL,
                        `elevationCapturedAt` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `fence_junctions`
                        (`id`, `latitude`, `longitude`, `elevationMeters`, `elevationSource`,
                         `verticalDatum`, `verticalAccuracyMeters`, `elevationCapturedAt`)
                    SELECT `id`, `latitude`, `longitude`, `elevationMeters`, `elevationSource`,
                           `verticalDatum`, `verticalAccuracyMeters`, `elevationCapturedAt`
                    FROM `pasture_vertices`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE `pasture_vertices_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pastureId` INTEGER NOT NULL,
                        `sequence` INTEGER NOT NULL,
                        `junctionId` INTEGER NOT NULL,
                        FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`junctionId`) REFERENCES `fence_junctions`(`id`)
                            ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `pasture_vertices_new` (`id`, `pastureId`, `sequence`, `junctionId`)
                    SELECT `id`, `pastureId`, `sequence`, `id` FROM `pasture_vertices`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `pasture_vertices`")
                db.execSQL("ALTER TABLE `pasture_vertices_new` RENAME TO `pasture_vertices`")
                db.execSQL(
                    "CREATE INDEX `index_pasture_vertices_pastureId` ON `pasture_vertices` (`pastureId`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_pasture_vertices_junctionId` ON `pasture_vertices` (`junctionId`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_pasture_vertices_pastureId_sequence` " +
                        "ON `pasture_vertices` (`pastureId`, `sequence`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_pasture_vertices_pastureId_junctionId` " +
                        "ON `pasture_vertices` (`pastureId`, `junctionId`)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `gates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `junctionAId` INTEGER NOT NULL,
                        `junctionBId` INTEGER NOT NULL,
                        `segmentRatio` REAL NOT NULL,
                        `widthMeters` REAL NOT NULL,
                        `gateType` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`junctionAId`) REFERENCES `fence_junctions`(`id`)
                            ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`junctionBId`) REFERENCES `fence_junctions`(`id`)
                            ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_gates_junctionAId` ON `gates` (`junctionAId`)")
                db.execSQL("CREATE INDEX `index_gates_junctionBId` ON `gates` (`junctionBId`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX `index_gates_junctionAId_junctionBId_segmentRatio` " +
                        "ON `gates` (`junctionAId`, `junctionBId`, `segmentRatio`)"
                )
            }
        }

        fun getDatabase(context: Context): RangeWaterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RangeWaterDatabase::class.java,
                    "rangewater_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
