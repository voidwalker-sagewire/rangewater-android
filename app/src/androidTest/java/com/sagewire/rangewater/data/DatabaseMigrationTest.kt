package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Database(
    entities = [
        WaterPointEntity::class,
        PastureEntity::class,
        FenceJunctionEntity::class,
        PastureVertexEntity::class,
        WaterPastureAssignmentEntity::class,
        GateEntity::class,
        HerdEntity::class,
        CattleMovementEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SchemaSixTestDatabase : RoomDatabase() {
    abstract fun seedDao(): SchemaSixSeedDao
}

@Dao
interface SchemaSixSeedDao {
    @Insert suspend fun insertWater(row: WaterPointEntity)
    @Insert suspend fun insertPasture(row: PastureEntity)
    @Insert suspend fun insertJunctions(rows: List<FenceJunctionEntity>)
    @Insert suspend fun insertVertices(rows: List<PastureVertexEntity>)
    @Insert suspend fun insertAssignment(row: WaterPastureAssignmentEntity)
    @Insert suspend fun insertGate(row: GateEntity)
    @Insert suspend fun insertHerd(row: HerdEntity)
    @Insert suspend fun insertMovement(row: CattleMovementEntity)
}

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
    fun migrationOneToThreePreservesWaterAndCreatesPastureAndAssignmentSchema() = runBlocking {
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
            .addMigrations(
                RangeWaterDatabase.MIGRATION_1_2,
                RangeWaterDatabase.MIGRATION_2_3,
                RangeWaterDatabase.MIGRATION_3_4,
                RangeWaterDatabase.MIGRATION_4_5,
                RangeWaterDatabase.MIGRATION_5_6,
                RangeWaterDatabase.MIGRATION_6_7
            )
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

            migrated.waterPastureAssignmentDao().replaceForWaterPoint(
                waterPointId = 1L,
                pastureIds = listOf(pastureId),
                assignedAt = 30L
            )
            assertEquals(
                listOf(pastureId),
                migrated.waterPastureAssignmentDao().getPastureIdsForWaterPoint(1L)
            )
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrationTwoToThreePreservesExistingWaterPastureAndOrderedVertices() = runBlocking {
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
            database.execSQL(
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
                    FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_pasture_vertices_pastureId` ON `pasture_vertices` (`pastureId`)"
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pasture_vertices_pastureId_sequence` ON `pasture_vertices` (`pastureId`, `sequence`)"
            )
            database.execSQL(
                "INSERT INTO water_points VALUES (5, 40.5, -99.5, 'Creek Tank', 'TANK', '', 100, 200)"
            )
            database.execSQL(
                "INSERT INTO pastures VALUES (10, 'North Meadow', '', 300, 400)"
            )
            database.execSQL(
                """
                INSERT INTO pasture_vertices
                    (id, pastureId, sequence, latitude, longitude, elevationMeters,
                     elevationSource, verticalDatum, verticalAccuracyMeters, elevationCapturedAt)
                VALUES (1, 10, 0, 40.0, -100.0, 310.5, 'USGS_3DEP', 'NAVD88', 1.5, 1700000000000)
                """.trimIndent()
            )
            database.execSQL(
                "INSERT INTO pasture_vertices (id, pastureId, sequence, latitude, longitude) VALUES (2, 10, 1, 40.1, -100.0)"
            )
            database.execSQL(
                "INSERT INTO pasture_vertices (id, pastureId, sequence, latitude, longitude) VALUES (3, 10, 2, 40.0, -99.9)"
            )
            database.version = 2
        }

        val migrated = Room.databaseBuilder(context, RangeWaterDatabase::class.java, databaseName)
            .addMigrations(
                RangeWaterDatabase.MIGRATION_2_3,
                RangeWaterDatabase.MIGRATION_3_4,
                RangeWaterDatabase.MIGRATION_4_5,
                RangeWaterDatabase.MIGRATION_5_6,
                RangeWaterDatabase.MIGRATION_6_7
            )
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Creek Tank", migrated.waterPointDao().getById(5L)?.name)
            val pasture = migrated.pastureDao().getById(10L)
            assertEquals("North Meadow", pasture?.pasture?.name)
            assertEquals(listOf(0, 1, 2), pasture?.vertices?.sortedBy { it.sequence }?.map { it.sequence })
            val firstVertex = pasture?.vertices?.first { it.sequence == 0 }
            assertEquals(1L, firstVertex?.junctionId)
            assertEquals(40.0, firstVertex?.latitude ?: Double.NaN, 0.0)
            assertEquals(-100.0, firstVertex?.longitude ?: Double.NaN, 0.0)
            assertEquals(310.5, firstVertex?.elevationMeters ?: Double.NaN, 0.0)
            assertEquals("USGS_3DEP", firstVertex?.elevationSource)
            assertEquals("NAVD88", firstVertex?.verticalDatum)
            assertEquals(1.5, firstVertex?.verticalAccuracyMeters ?: Double.NaN, 0.0)
            assertEquals(1700000000000L, firstVertex?.elevationCapturedAt)

            migrated.waterPastureAssignmentDao().replaceForWaterPoint(
                waterPointId = 5L,
                pastureIds = listOf(10L),
                assignedAt = 500L
            )
            assertEquals(
                listOf(10L),
                migrated.waterPastureAssignmentDao().getPastureIdsForWaterPoint(5L)
            )
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrationFourToFiveCreatesDurableGateSchema() = runBlocking {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                "CREATE TABLE `water_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `name` TEXT NOT NULL, " +
                    "`sourceType` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE `pastures` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE `fence_junctions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `elevationMeters` REAL, " +
                    "`elevationSource` TEXT, `verticalDatum` TEXT, `verticalAccuracyMeters` REAL, " +
                    "`elevationCapturedAt` INTEGER)"
            )
            database.execSQL(
                "CREATE TABLE `pasture_vertices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`pastureId` INTEGER NOT NULL, `sequence` INTEGER NOT NULL, `junctionId` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`junctionId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)"
            )
            database.execSQL("CREATE INDEX `index_pasture_vertices_pastureId` ON `pasture_vertices` (`pastureId`)")
            database.execSQL("CREATE INDEX `index_pasture_vertices_junctionId` ON `pasture_vertices` (`junctionId`)")
            database.execSQL("CREATE UNIQUE INDEX `index_pasture_vertices_pastureId_sequence` ON `pasture_vertices` (`pastureId`, `sequence`)")
            database.execSQL("CREATE UNIQUE INDEX `index_pasture_vertices_pastureId_junctionId` ON `pasture_vertices` (`pastureId`, `junctionId`)")
            database.execSQL(
                "CREATE TABLE `water_pasture_assignments` (`waterPointId` INTEGER NOT NULL, " +
                    "`pastureId` INTEGER NOT NULL, `assignedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`waterPointId`, `pastureId`), " +
                    "FOREIGN KEY(`waterPointId`) REFERENCES `water_points`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            database.execSQL("CREATE INDEX `index_water_pasture_assignments_waterPointId` ON `water_pasture_assignments` (`waterPointId`)")
            database.execSQL("CREATE INDEX `index_water_pasture_assignments_pastureId` ON `water_pasture_assignments` (`pastureId`)")
            database.execSQL("INSERT INTO fence_junctions (id, latitude, longitude) VALUES (1, 40.0, -100.0)")
            database.execSQL("INSERT INTO fence_junctions (id, latitude, longitude) VALUES (2, 40.0, -99.99)")
            database.version = 4
        }

        val migrated = Room.databaseBuilder(context, RangeWaterDatabase::class.java, databaseName)
            .addMigrations(
                RangeWaterDatabase.MIGRATION_4_5,
                RangeWaterDatabase.MIGRATION_5_6,
                RangeWaterDatabase.MIGRATION_6_7
            )
            .allowMainThreadQueries()
            .build()
        try {
            val id = migrated.gateDao().insertValidatedGate(
                GateEntity(
                    name = "East Gate",
                    junctionAId = 1,
                    junctionBId = 2,
                    segmentRatio = 0.5
                )
            )
            val gate = migrated.gateDao().getById(id)
            assertNotNull(gate)
            assertEquals("East Gate", gate?.name)
            assertEquals(4.27, gate?.widthMeters ?: 0.0, 0.001)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrationFiveToSixCreatesHerdAndMovementSchemaAndPreservesData() = runBlocking {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL("CREATE TABLE `water_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `name` TEXT NOT NULL, `sourceType` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE `pastures` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
            database.execSQL("CREATE TABLE `fence_junctions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `elevationMeters` REAL, `elevationSource` TEXT, `verticalDatum` TEXT, `verticalAccuracyMeters` REAL, `elevationCapturedAt` INTEGER)")
            database.execSQL("CREATE TABLE `pasture_vertices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pastureId` INTEGER NOT NULL, `sequence` INTEGER NOT NULL, `junctionId` INTEGER NOT NULL, FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`junctionId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            database.execSQL("CREATE INDEX `index_pasture_vertices_pastureId` ON `pasture_vertices` (`pastureId`)")
            database.execSQL("CREATE INDEX `index_pasture_vertices_junctionId` ON `pasture_vertices` (`junctionId`)")
            database.execSQL("CREATE UNIQUE INDEX `index_pasture_vertices_pastureId_sequence` ON `pasture_vertices` (`pastureId`, `sequence`)")
            database.execSQL("CREATE UNIQUE INDEX `index_pasture_vertices_pastureId_junctionId` ON `pasture_vertices` (`pastureId`, `junctionId`)")
            database.execSQL("CREATE TABLE `water_pasture_assignments` (`waterPointId` INTEGER NOT NULL, `pastureId` INTEGER NOT NULL, `assignedAt` INTEGER NOT NULL, PRIMARY KEY(`waterPointId`, `pastureId`), FOREIGN KEY(`waterPointId`) REFERENCES `water_points`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`pastureId`) REFERENCES `pastures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            database.execSQL("CREATE INDEX `index_water_pasture_assignments_waterPointId` ON `water_pasture_assignments` (`waterPointId`)")
            database.execSQL("CREATE INDEX `index_water_pasture_assignments_pastureId` ON `water_pasture_assignments` (`pastureId`)")
            database.execSQL("CREATE TABLE `gates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `junctionAId` INTEGER NOT NULL, `junctionBId` INTEGER NOT NULL, `segmentRatio` REAL NOT NULL, `widthMeters` REAL NOT NULL, `gateType` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`junctionAId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`junctionBId`) REFERENCES `fence_junctions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            database.execSQL("CREATE INDEX `index_gates_junctionAId` ON `gates` (`junctionAId`)")
            database.execSQL("CREATE INDEX `index_gates_junctionBId` ON `gates` (`junctionBId`)")
            database.execSQL("CREATE UNIQUE INDEX `index_gates_junctionAId_junctionBId_segmentRatio` ON `gates` (`junctionAId`, `junctionBId`, `segmentRatio`)")
            database.execSQL("INSERT INTO water_points VALUES (1, 40.0, -100.0, 'Trough 1', 'TROUGH', '', 10, 10)")
            database.execSQL("INSERT INTO pastures VALUES (1, 'North Meadow', '', 100, 100)")
            database.version = 5
        }

        val migrated = Room.databaseBuilder(context, RangeWaterDatabase::class.java, databaseName)
            .addMigrations(RangeWaterDatabase.MIGRATION_5_6, RangeWaterDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Trough 1", migrated.waterPointDao().getById(1)?.name)
            val id = migrated.herdDao().createHerd(
                HerdEntity(
                    name = "Calvers", quantity = 40, countUnit = CountUnit.PAIRS,
                    stockClass = StockClass.COW_CALF_PAIRS, markerColorHex = "#FF9100",
                    locationKind = HerdLocationKind.PASTURE, currentPastureId = 1
                )
            )
            assertEquals("Calvers", migrated.herdDao().getById(id)?.name)

            val circuitId = migrated.grazingCircuitDao().createCircuit(
                name = "Cow Rotation",
                notes = "Migration acceptance",
                pastures = listOf(
                    GrazingCircuitPastureDraft(
                        pastureId = 1,
                        roles = setOf(SeasonalPastureRole.ROTATION, SeasonalPastureRole.CALVING)
                    )
                ),
                now = 200
            )
            migrated.grazingCircuitDao().assignHerd(id, circuitId, now = 201)
            assertEquals(circuitId, migrated.grazingCircuitDao().assignmentForHerd(id)?.circuitId)

            val forageId = migrated.forageObservationDao().record(
                PastureForageObservationEntity(
                    pastureId = 1,
                    observedAt = 300,
                    averageHeightInches = 8.0,
                    sampleCount = 5,
                    forageStandType = ForageStandType.TALL_FESCUE_CLOVER,
                    standCondition = ForageStandCondition.GOOD,
                    dmPerAcreInchLow = 300.0,
                    dmPerAcreInchHigh = 350.0,
                    calibrationSource = ForageCalibrationSource.OHIO_NRCS_GLCI_GRAZING_STICK,
                    acreageSnapshot = 12.5
                )
            )
            assertEquals(forageId, migrated.forageObservationDao().latestForPasture(1)?.id)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrationSixToSevenPreservesEveryExistingEntityAndCreatesWritableSeasonalTables() = runBlocking {
        val schemaSix = Room.databaseBuilder(context, SchemaSixTestDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        val pastureId = 10L
        val waterId = 1L
        val gateId = 40L
        val herdId = 50L
        val movementId = 60L
        try {
            val seed = schemaSix.seedDao()
            seed.insertWater(
                WaterPointEntity(waterId, 40.001, -79.999, "Migration Trough", WaterSourceType.TROUGH, "schema six water", 101, 101)
            )
            seed.insertPasture(PastureEntity(pastureId, "Migration Ranch", "schema six pasture", 100, 100))
            seed.insertJunctions(
                listOf(
                    FenceJunctionEntity(20, 40.0, -80.0),
                    FenceJunctionEntity(21, 40.0, -79.99),
                    FenceJunctionEntity(22, 40.01, -79.99)
                )
            )
            seed.insertVertices(
                listOf(
                    PastureVertexEntity(30, pastureId, 0, 20),
                    PastureVertexEntity(31, pastureId, 1, 21),
                    PastureVertexEntity(32, pastureId, 2, 22)
                )
            )
            seed.insertAssignment(WaterPastureAssignmentEntity(waterId, pastureId, 102))
            seed.insertGate(
                GateEntity(
                    id = gateId,
                    name = "Migration Gate",
                    junctionAId = 20,
                    junctionBId = 21,
                    segmentRatio = 0.5,
                    createdAt = 103,
                    updatedAt = 103
                )
            )
            seed.insertHerd(
                HerdEntity(
                    id = herdId,
                    name = "Migration Herd",
                    quantity = 25,
                    countUnit = CountUnit.HEAD,
                    stockClass = StockClass.DRY_COWS,
                    markerColorHex = "#FF9100",
                    locationKind = HerdLocationKind.PASTURE,
                    currentPastureId = pastureId
                )
            )
            seed.insertMovement(
                CattleMovementEntity(
                    id = movementId,
                    herdId = herdId,
                    originLocationKind = HerdLocationKind.OFF_RANCH,
                    originNameSnapshot = "OFF_RANCH",
                    destinationLocationKind = HerdLocationKind.PASTURE,
                    destinationPastureId = pastureId,
                    destinationNameSnapshot = "Migration Ranch",
                    quantity = 25,
                    countUnit = CountUnit.HEAD,
                    status = MovementStatus.COMPLETED,
                    completedAt = 104,
                    unmappedRoute = true,
                    notes = "schema six movement",
                    createdAt = 104,
                    updatedAt = 104
                )
            )
        } finally {
            schemaSix.close()
        }

        val migrated = Room.databaseBuilder(context, RangeWaterDatabase::class.java, databaseName)
            .addMigrations(RangeWaterDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Migration Trough", migrated.waterPointDao().getById(waterId)?.name)
            assertEquals("Migration Ranch", migrated.pastureDao().getById(pastureId)?.pasture?.name)
            assertEquals(listOf(pastureId), migrated.waterPastureAssignmentDao().getPastureIdsForWaterPoint(waterId))
            assertEquals("Migration Gate", migrated.gateDao().getById(gateId)?.name)
            assertEquals("Migration Herd", migrated.herdDao().getById(herdId)?.name)
            assertEquals("schema six movement", migrated.movementDao().getById(movementId)?.notes)
            assertEquals(3, migrated.pastureDao().getById(pastureId)?.vertices?.size)
            assertTrue(migrated.grazingCircuitDao().observeAllCircuits().first().isEmpty())
            assertTrue(migrated.forageObservationDao().observeAll().first().isEmpty())

            val circuitId = migrated.grazingCircuitDao().createCircuit(
                "Migration Circuit",
                "",
                listOf(GrazingCircuitPastureDraft(pastureId, setOf(SeasonalPastureRole.WINTER)))
            )
            migrated.grazingCircuitDao().assignHerd(herdId, circuitId)
            assertEquals(circuitId, migrated.grazingCircuitDao().assignmentForHerd(herdId)?.circuitId)
        } finally {
            migrated.close()
        }
    }
}
