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
        GateEntity::class,
        HerdEntity::class,
        CattleMovementEntity::class,
        GrazingCircuitEntity::class,
        GrazingCircuitPastureEntity::class,
        GrazingCircuitPastureRoleEntity::class,
        HerdGrazingCircuitAssignmentEntity::class,
        PastureForageObservationEntity::class,
        PaddockPlanEntity::class,
        PaddockPlanNodeEntity::class,
        PaddockDividerEntity::class,
        PaddockDividerNodeRefEntity::class,
        PaddockRegionLabelEntity::class,
        FieldRecordEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class RangeWaterDatabase : RoomDatabase() {
    abstract fun waterPointDao(): WaterPointDao
    abstract fun pastureDao(): PastureDao
    abstract fun fenceJunctionDao(): FenceJunctionDao
    abstract fun waterPastureAssignmentDao(): WaterPastureAssignmentDao
    abstract fun gateDao(): GateDao
    abstract fun herdDao(): HerdDao
    abstract fun movementDao(): MovementDao
    abstract fun grazingCircuitDao(): GrazingCircuitDao
    abstract fun forageObservationDao(): ForageObservationDao
    abstract fun pastureRestDao(): PastureRestDao
    abstract fun paddockPlanDao(): PaddockPlanDao
    abstract fun fieldRecordDao(): FieldRecordDao
    abstract fun backupDao(): BackupDao

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `herds` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL COLLATE NOCASE,
                        `quantity` INTEGER NOT NULL,
                        `countUnit` TEXT NOT NULL,
                        `stockClass` TEXT NOT NULL,
                        `averageWeightLbs` REAL,
                        `markerColorHex` TEXT NOT NULL,
                        `shortMarkerLabel` TEXT,
                        `notes` TEXT NOT NULL,
                        `locationKind` TEXT NOT NULL,
                        `currentPastureId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `archivedAt` INTEGER,
                        FOREIGN KEY(`currentPastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_herds_name` ON `herds` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_herds_currentPastureId` ON `herds` (`currentPastureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_herds_archivedAt` ON `herds` (`archivedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cattle_movements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `herdId` INTEGER NOT NULL,
                        `originLocationKind` TEXT NOT NULL,
                        `originPastureId` INTEGER,
                        `originNameSnapshot` TEXT NOT NULL,
                        `destinationLocationKind` TEXT NOT NULL,
                        `destinationPastureId` INTEGER,
                        `destinationNameSnapshot` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL,
                        `countUnit` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `plannedAt` INTEGER,
                        `completedAt` INTEGER,
                        `unmappedRoute` INTEGER NOT NULL,
                        `gateId` INTEGER,
                        `gateSnapshot` TEXT,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`herdId`) REFERENCES `herds`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`originPastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`destinationPastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`gateId`) REFERENCES `gates`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cattle_movements_herdId_completedAt` ON `cattle_movements` (`herdId`, `completedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cattle_movements_status_plannedAt` ON `cattle_movements` (`status`, `plannedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cattle_movements_originPastureId` ON `cattle_movements` (`originPastureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cattle_movements_destinationPastureId` ON `cattle_movements` (`destinationPastureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cattle_movements_gateId` ON `cattle_movements` (`gateId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `grazing_circuits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT COLLATE NOCASE NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `archivedAt` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_grazing_circuits_name` ON `grazing_circuits` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grazing_circuits_archivedAt` ON `grazing_circuits` (`archivedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `grazing_circuit_pastures` (
                        `circuitId` INTEGER NOT NULL,
                        `pastureId` INTEGER NOT NULL,
                        `sequence` INTEGER NOT NULL,
                        `assignedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`circuitId`, `pastureId`),
                        FOREIGN KEY(`circuitId`) REFERENCES `grazing_circuits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_grazing_circuit_pastures_circuitId_sequence` ON `grazing_circuit_pastures` (`circuitId`, `sequence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grazing_circuit_pastures_pastureId` ON `grazing_circuit_pastures` (`pastureId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `grazing_circuit_pasture_roles` (
                        `circuitId` INTEGER NOT NULL,
                        `pastureId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        PRIMARY KEY(`circuitId`, `pastureId`, `role`),
                        FOREIGN KEY(`circuitId`, `pastureId`) REFERENCES `grazing_circuit_pastures`(`circuitId`, `pastureId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_grazing_circuit_pasture_roles_circuitId_pastureId` ON `grazing_circuit_pasture_roles` (`circuitId`, `pastureId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `herd_grazing_circuit_assignments` (
                        `herdId` INTEGER NOT NULL,
                        `circuitId` INTEGER NOT NULL,
                        `assignedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`herdId`),
                        FOREIGN KEY(`herdId`) REFERENCES `herds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`circuitId`) REFERENCES `grazing_circuits`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_herd_grazing_circuit_assignments_circuitId` ON `herd_grazing_circuit_assignments` (`circuitId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pasture_forage_observations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pastureId` INTEGER NOT NULL,
                        `observedAt` INTEGER NOT NULL,
                        `averageHeightInches` REAL NOT NULL,
                        `sampleCount` INTEGER NOT NULL,
                        `forageStandType` TEXT NOT NULL,
                        `standCondition` TEXT NOT NULL,
                        `residualHeightInches` REAL NOT NULL,
                        `dmPerAcreInchLow` REAL NOT NULL,
                        `dmPerAcreInchHigh` REAL NOT NULL,
                        `calibrationSource` TEXT NOT NULL,
                        `acreageSnapshot` REAL NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pasture_forage_observations_pastureId` ON `pasture_forage_observations` (`pastureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pasture_forage_observations_pastureId_observedAt` ON `pasture_forage_observations` (`pastureId`, `observedAt`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `paddock_split_plans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pastureId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `sideALabel` TEXT NOT NULL,
                        `sideBLabel` TEXT NOT NULL,
                        `startJunctionAId` INTEGER NOT NULL,
                        `startJunctionBId` INTEGER NOT NULL,
                        `startSegmentRatio` REAL NOT NULL,
                        `endJunctionAId` INTEGER NOT NULL,
                        `endJunctionBId` INTEGER NOT NULL,
                        `endSegmentRatio` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `archivedAt` INTEGER,
                        FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`startJunctionAId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`startJunctionBId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`endJunctionAId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`endJunctionBId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_split_plans_pastureId` ON `paddock_split_plans` (`pastureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_split_plans_startJunctionAId` ON `paddock_split_plans` (`startJunctionAId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_split_plans_startJunctionBId` ON `paddock_split_plans` (`startJunctionBId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_split_plans_endJunctionAId` ON `paddock_split_plans` (`endJunctionAId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_split_plans_endJunctionBId` ON `paddock_split_plans` (`endJunctionBId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_split_plans_archivedAt` ON `paddock_split_plans` (`archivedAt`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `paddock_plans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `pastureId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `archivedAt` INTEGER,
                        FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_plans_pastureId` ON `paddock_plans` (`pastureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_plans_archivedAt` ON `paddock_plans` (`archivedAt`)")
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `paddock_plans_one_active_insert`
                    BEFORE INSERT ON `paddock_plans`
                    WHEN NEW.`archivedAt` IS NULL AND EXISTS (
                        SELECT 1 FROM `paddock_plans` WHERE `pastureId` = NEW.`pastureId` AND `archivedAt` IS NULL
                    )
                    BEGIN SELECT RAISE(ABORT, 'one active paddock plan per pasture'); END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `paddock_plans_one_active_update`
                    BEFORE UPDATE OF `pastureId`, `archivedAt` ON `paddock_plans`
                    WHEN NEW.`archivedAt` IS NULL AND EXISTS (
                        SELECT 1 FROM `paddock_plans`
                        WHERE `pastureId` = NEW.`pastureId` AND `archivedAt` IS NULL AND `id` != NEW.`id`
                    )
                    BEGIN SELECT RAISE(ABORT, 'one active paddock plan per pasture'); END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `paddock_plan_nodes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `planId` INTEGER NOT NULL,
                        `nodeKind` TEXT NOT NULL,
                        `boundaryJunctionAId` INTEGER,
                        `boundaryJunctionBId` INTEGER,
                        `boundarySegmentRatio` REAL,
                        `latitude` REAL,
                        `longitude` REAL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`planId`) REFERENCES `paddock_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`boundaryJunctionAId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(`boundaryJunctionBId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_plan_nodes_planId` ON `paddock_plan_nodes` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_plan_nodes_boundaryJunctionAId` ON `paddock_plan_nodes` (`boundaryJunctionAId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_plan_nodes_boundaryJunctionBId` ON `paddock_plan_nodes` (`boundaryJunctionBId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `paddock_dividers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `planId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `sequence` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `archivedAt` INTEGER,
                        FOREIGN KEY(`planId`) REFERENCES `paddock_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_dividers_planId` ON `paddock_dividers` (`planId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_paddock_dividers_planId_sequence` ON `paddock_dividers` (`planId`, `sequence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_dividers_archivedAt` ON `paddock_dividers` (`archivedAt`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `paddock_divider_node_refs` (
                        `dividerId` INTEGER NOT NULL,
                        `sequence` INTEGER NOT NULL,
                        `nodeId` INTEGER NOT NULL,
                        PRIMARY KEY(`dividerId`, `sequence`),
                        FOREIGN KEY(`dividerId`) REFERENCES `paddock_dividers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`nodeId`) REFERENCES `paddock_plan_nodes`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_divider_node_refs_nodeId` ON `paddock_divider_node_refs` (`nodeId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `paddock_region_labels` (
                        `planId` INTEGER NOT NULL,
                        `regionKey` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        PRIMARY KEY(`planId`, `regionKey`),
                        FOREIGN KEY(`planId`) REFERENCES `paddock_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paddock_region_labels_planId` ON `paddock_region_labels` (`planId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `field_records` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordType` TEXT NOT NULL,
                        `note` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `pastureId` INTEGER,
                        `taskStatus` TEXT,
                        `observedAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `completedAt` INTEGER,
                        `archivedAt` INTEGER,
                        FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_records_pastureId` ON `field_records` (`pastureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_records_recordType` ON `field_records` (`recordType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_records_taskStatus` ON `field_records` (`taskStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_records_observedAt` ON `field_records` (`observedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_field_records_archivedAt` ON `field_records` (`archivedAt`)")

                db.execSQL(
                    """
                    INSERT INTO `paddock_plans` (`id`, `pastureId`, `name`, `createdAt`, `updatedAt`, `archivedAt`)
                    SELECT `id`, `pastureId`, `name`, `createdAt`, `updatedAt`, `archivedAt`
                    FROM `paddock_split_plans`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `paddock_plan_nodes`
                        (`id`, `planId`, `nodeKind`, `boundaryJunctionAId`, `boundaryJunctionBId`,
                         `boundarySegmentRatio`, `latitude`, `longitude`, `createdAt`, `updatedAt`)
                    SELECT (`id` * 2 - 1), `id`, 'BOUNDARY_ANCHOR', `startJunctionAId`,
                           `startJunctionBId`, `startSegmentRatio`, NULL, NULL, `createdAt`, `updatedAt`
                    FROM `paddock_split_plans`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `paddock_plan_nodes`
                        (`id`, `planId`, `nodeKind`, `boundaryJunctionAId`, `boundaryJunctionBId`,
                         `boundarySegmentRatio`, `latitude`, `longitude`, `createdAt`, `updatedAt`)
                    SELECT (`id` * 2), `id`, 'BOUNDARY_ANCHOR', `endJunctionAId`,
                           `endJunctionBId`, `endSegmentRatio`, NULL, NULL, `createdAt`, `updatedAt`
                    FROM `paddock_split_plans`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `paddock_dividers` (`id`, `planId`, `name`, `sequence`, `createdAt`, `updatedAt`, `archivedAt`)
                    SELECT `id`, `id`, `name`, 0, `createdAt`, `updatedAt`, `archivedAt`
                    FROM `paddock_split_plans`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `paddock_divider_node_refs` (`dividerId`, `sequence`, `nodeId`)
                    SELECT `id`, 0, (`id` * 2 - 1) FROM `paddock_split_plans`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `paddock_divider_node_refs` (`dividerId`, `sequence`, `nodeId`)
                    SELECT `id`, 1, (`id` * 2) FROM `paddock_split_plans`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `paddock_region_labels` (`planId`, `regionKey`, `label`)
                    SELECT `id`, 'legacy-side-a', `sideALabel` FROM `paddock_split_plans`
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `paddock_region_labels` (`planId`, `regionKey`, `label`)
                    SELECT `id`, 'legacy-side-b', `sideBLabel` FROM `paddock_split_plans`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `paddock_split_plans`")
            }
        }

        fun getDatabase(context: Context): RangeWaterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RangeWaterDatabase::class.java,
                    "rangewater_database"
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9
                )
                    .build()
                    .also { instance = it }
            }
    }
}
