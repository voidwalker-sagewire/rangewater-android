package com.sagewire.rangewater.data

import com.google.gson.Gson
import com.sagewire.rangewater.ui.map.DisplayPreferences
import com.sagewire.rangewater.ui.map.SpatialCoverageScope
import com.sagewire.rangewater.ui.map.WaterCoverageMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RangeWaterArchiveCodecTest {
    @Test
    fun archiveRoundTrip_preservesEveryRecordAndPreference() {
        val original = completeData()
        val bytes = RangeWaterArchiveCodec.toByteArray(original, "0.12.0", createdAt = 1234L)

        val restored = RangeWaterArchiveCodec.read(ByteArrayInputStream(bytes))

        assertEquals(1234L, restored.manifest.createdAt)
        assertEquals("0.12.0", restored.manifest.appVersionName)
        assertEquals(original, restored.data)
        assertEquals(original.recordCounts(), restored.manifest.recordCounts)
    }

    @Test
    fun changedDataWithOriginalManifest_failsIntegrityCheck() {
        val bytes = RangeWaterArchiveCodec.toByteArray(completeData(), "0.12.0")
        val entries = unzip(bytes).toMutableMap()
        entries["data.json"] = entries.getValue("data.json") + byteArrayOf(' '.code.toByte())

        try {
            RangeWaterArchiveCodec.read(ByteArrayInputStream(zip(entries)))
            fail("Expected integrity rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("integrity"))
        }
    }

    @Test
    fun newerFormat_isRejectedBeforeRecordsAreReturned() {
        val bytes = RangeWaterArchiveCodec.toByteArray(completeData(), "9.9.9")
        val entries = unzip(bytes).toMutableMap()
        val gson = Gson()
        val manifest = gson.fromJson(String(entries.getValue("manifest.json")), RangeWaterBackupManifest::class.java)
        entries["manifest.json"] = gson.toJson(manifest.copy(formatVersion = 99)).toByteArray()

        try {
            RangeWaterArchiveCodec.read(ByteArrayInputStream(zip(entries)))
            fail("Expected future-version rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("newer RangeWater"))
        }
    }

    @Test
    fun missingRelationship_isRejected() {
        val broken = completeData().copy(junctions = emptyList())
        try {
            RangeWaterBackupValidator.validate(broken)
            fail("Expected relationship rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("missing fence junction"))
        }
    }

    @Test
    fun missingCircuitMembershipRelationship_isRejectedBeforeRestore() {
        val broken = completeData().copy(circuitPastures = emptyList())
        try {
            RangeWaterBackupValidator.validate(broken)
            fail("Expected circuit relationship rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("membership"))
        }
    }

    @Test
    fun formatOneArchive_restoresWithEmptySeasonalCollections() {
        val gson = Gson()
        val legacyData = completeData().copy(
            grazingCircuits = emptyList(),
            circuitPastures = emptyList(),
            circuitPastureRoles = emptyList(),
            herdCircuitAssignments = emptyList(),
            pastureForageObservations = emptyList(),
            paddockSplitPlans = emptyList(),
            paddockPlans = emptyList(),
            paddockPlanNodes = emptyList(),
            paddockDividers = emptyList(),
            paddockDividerNodeRefs = emptyList(),
            paddockRegionLabels = emptyList(),
            fieldRecords = emptyList()
        )
        val dataJson = gson.toJsonTree(legacyData).asJsonObject.apply {
            remove("grazingCircuits")
            remove("circuitPastures")
            remove("circuitPastureRoles")
            remove("herdCircuitAssignments")
            remove("pastureForageObservations")
            remove("paddockSplitPlans")
        }
        val dataBytes = gson.toJson(dataJson).toByteArray(StandardCharsets.UTF_8)
        val legacyCounts = legacyData.recordCounts()
        val manifest = RangeWaterBackupManifest(
            formatId = RangeWaterArchiveCodec.FORMAT_ID,
            formatVersion = 1,
            databaseSchemaVersion = 6,
            appVersionName = "1.0.1",
            createdAt = 123L,
            dataSha256 = sha256(dataBytes),
            recordCounts = legacyCounts
        )
        val manifestJson = gson.toJsonTree(manifest).asJsonObject.apply {
            getAsJsonObject("recordCounts").apply {
                remove("grazingCircuits")
                remove("circuitPastures")
                remove("circuitPastureRoles")
                remove("herdCircuitAssignments")
                remove("pastureForageObservations")
                remove("paddockSplitPlans")
            }
        }
        val archive = zip(
            linkedMapOf(
                "manifest.json" to gson.toJson(manifestJson).toByteArray(StandardCharsets.UTF_8),
                "data.json" to dataBytes
            )
        )

        val restored = RangeWaterArchiveCodec.read(ByteArrayInputStream(archive))

        assertEquals(legacyData, restored.data)
        assertTrue(restored.data.grazingCircuits.isEmpty())
        assertTrue(restored.data.pastureForageObservations.isEmpty())
        assertTrue(restored.data.paddockSplitPlans.isEmpty())
    }

    @Test
    fun formatTwoArchive_restoresWithEmptyPaddockPlans() {
        val gson = Gson()
        val legacyData = completeData().copy(
            paddockSplitPlans = emptyList(),
            paddockPlans = emptyList(),
            paddockPlanNodes = emptyList(),
            paddockDividers = emptyList(),
            paddockDividerNodeRefs = emptyList(),
            paddockRegionLabels = emptyList(),
            fieldRecords = emptyList()
        )
        val dataJson = gson.toJsonTree(legacyData).asJsonObject.apply { remove("paddockSplitPlans") }
        val dataBytes = gson.toJson(dataJson).toByteArray(StandardCharsets.UTF_8)
        val manifest = RangeWaterBackupManifest(
            formatId = RangeWaterArchiveCodec.FORMAT_ID,
            formatVersion = 2,
            databaseSchemaVersion = 7,
            appVersionName = "1.1.0",
            createdAt = 456L,
            dataSha256 = sha256(dataBytes),
            recordCounts = legacyData.recordCounts()
        )
        val manifestJson = gson.toJsonTree(manifest).asJsonObject.apply {
            getAsJsonObject("recordCounts").remove("paddockSplitPlans")
        }
        val archive = zip(
            linkedMapOf(
                "manifest.json" to gson.toJson(manifestJson).toByteArray(StandardCharsets.UTF_8),
                "data.json" to dataBytes
            )
        )

        val restored = RangeWaterArchiveCodec.read(ByteArrayInputStream(archive))

        assertEquals(legacyData, restored.data)
        assertTrue(restored.data.paddockSplitPlans.isEmpty())
    }

    @Test
    fun formatThreeArchive_semanticallyUpgradesStraightSplitToSchemaNineGraph() {
        val gson = Gson()
        val legacyData = completeData().copy(
            paddockPlans = emptyList(),
            paddockPlanNodes = emptyList(),
            paddockDividers = emptyList(),
            paddockDividerNodeRefs = emptyList(),
            paddockRegionLabels = emptyList(),
            fieldRecords = emptyList(),
            paddockSplitPlans = listOf(
                PaddockSplitPlanEntity(
                    id = 90,
                    pastureId = 10,
                    name = "Triangle Split",
                    sideALabel = "Upper",
                    sideBLabel = "Lower",
                    startJunctionAId = 20,
                    startJunctionBId = 21,
                    startSegmentRatio = 0.5,
                    endJunctionAId = 22,
                    endJunctionBId = 23,
                    endSegmentRatio = 0.5,
                    createdAt = 100,
                    updatedAt = 100
                )
            )
        )
        val dataJson = gson.toJsonTree(legacyData).asJsonObject.apply {
            remove("paddockPlans")
            remove("paddockPlanNodes")
            remove("paddockDividers")
            remove("paddockDividerNodeRefs")
            remove("paddockRegionLabels")
            remove("fieldRecords")
        }
        val dataBytes = gson.toJson(dataJson).toByteArray(StandardCharsets.UTF_8)
        val manifest = RangeWaterBackupManifest(
            formatId = RangeWaterArchiveCodec.FORMAT_ID,
            formatVersion = 3,
            databaseSchemaVersion = 8,
            appVersionName = "1.2.0",
            createdAt = 789L,
            dataSha256 = sha256(dataBytes),
            recordCounts = legacyData.recordCounts()
        )
        val manifestJson = gson.toJsonTree(manifest).asJsonObject.apply {
            getAsJsonObject("recordCounts").apply {
                remove("paddockPlans")
                remove("paddockPlanNodes")
                remove("paddockDividers")
                remove("paddockDividerNodeRefs")
                remove("paddockRegionLabels")
                remove("fieldRecords")
            }
        }
        val restored = RangeWaterArchiveCodec.read(
            ByteArrayInputStream(
                zip(
                    linkedMapOf(
                        "manifest.json" to gson.toJson(manifestJson).toByteArray(StandardCharsets.UTF_8),
                        "data.json" to dataBytes
                    )
                )
            )
        )

        assertTrue(restored.data.paddockSplitPlans.isEmpty())
        assertEquals(1, restored.data.paddockPlans.size)
        assertEquals(2, restored.data.paddockPlanNodes.size)
        assertEquals(1, restored.data.paddockDividers.size)
        assertEquals(listOf("Upper", "Lower"), restored.data.paddockRegionLabels.map { it.label })
    }

    private fun completeData(): RangeWaterBackupData {
        val now = 100L
        return RangeWaterBackupData(
            waterPoints = listOf(WaterPointEntity(1, 40.0, -100.0, "Tank 1", WaterSourceType.TANK, "", now, now)),
            pastures = listOf(PastureEntity(10, "North", "", now, now)),
            junctions = listOf(
                FenceJunctionEntity(20, 40.0, -100.0),
                FenceJunctionEntity(21, 40.0, -99.99),
                FenceJunctionEntity(22, 40.01, -99.99),
                FenceJunctionEntity(23, 40.01, -100.0)
            ),
            vertices = listOf(
                PastureVertexEntity(30, 10, 0, 20),
                PastureVertexEntity(31, 10, 1, 21),
                PastureVertexEntity(32, 10, 2, 22),
                PastureVertexEntity(33, 10, 3, 23)
            ),
            assignments = listOf(WaterPastureAssignmentEntity(1, 10, now)),
            gates = listOf(GateEntity(40, "North Gate", 20, 21, 0.5, createdAt = now, updatedAt = now)),
            herds = listOf(
                HerdEntity(
                    id = 50,
                    name = "Pairs",
                    quantity = 30,
                    countUnit = CountUnit.PAIRS,
                    stockClass = StockClass.COW_CALF_PAIRS,
                    markerColorHex = "#FF9100",
                    locationKind = HerdLocationKind.PASTURE,
                    currentPastureId = 10,
                    createdAt = now,
                    updatedAt = now
                )
            ),
            movements = listOf(
                CattleMovementEntity(
                    id = 60,
                    herdId = 50,
                    originLocationKind = HerdLocationKind.OFF_RANCH,
                    originNameSnapshot = "OFF_RANCH",
                    destinationLocationKind = HerdLocationKind.PASTURE,
                    destinationPastureId = 10,
                    destinationNameSnapshot = "North",
                    quantity = 30,
                    countUnit = CountUnit.PAIRS,
                    status = MovementStatus.COMPLETED,
                    completedAt = now,
                    gateId = 40,
                    gateSnapshot = "Gate #40 - North Gate",
                    createdAt = now,
                    updatedAt = now
                )
            ),
            grazingCircuits = listOf(
                GrazingCircuitEntity(70, "Cow Rotation", "Seasonal circuit", now, now)
            ),
            circuitPastures = listOf(
                GrazingCircuitPastureEntity(70, 10, 0, now)
            ),
            circuitPastureRoles = listOf(
                GrazingCircuitPastureRoleEntity(70, 10, SeasonalPastureRole.ROTATION),
                GrazingCircuitPastureRoleEntity(70, 10, SeasonalPastureRole.STOCKPILED_WINTER)
            ),
            herdCircuitAssignments = listOf(
                HerdGrazingCircuitAssignmentEntity(50, 70, now)
            ),
            pastureForageObservations = listOf(
                PastureForageObservationEntity(
                    id = 80,
                    pastureId = 10,
                    observedAt = now,
                    averageHeightInches = 8.0,
                    sampleCount = 5,
                    forageStandType = ForageStandType.TALL_FESCUE_CLOVER,
                    standCondition = ForageStandCondition.GOOD,
                    dmPerAcreInchLow = 300.0,
                    dmPerAcreInchHigh = 350.0,
                    calibrationSource = ForageCalibrationSource.OHIO_NRCS_GLCI_GRAZING_STICK,
                    acreageSnapshot = 21.8,
                    createdAt = now,
                    updatedAt = now
                )
            ),
            paddockPlans = listOf(
                PaddockPlanEntity(
                    id = 90,
                    pastureId = 10,
                    name = "Triangle Split",
                    createdAt = now,
                    updatedAt = now
                )
            ),
            paddockPlanNodes = listOf(
                PaddockPlanNodeEntity(91, 90, PaddockPlanNodeKind.BOUNDARY_ANCHOR, 20, 21, 0.5, createdAt = now, updatedAt = now),
                PaddockPlanNodeEntity(92, 90, PaddockPlanNodeKind.BOUNDARY_ANCHOR, 22, 23, 0.5, createdAt = now, updatedAt = now)
            ),
            paddockDividers = listOf(PaddockDividerEntity(90, 90, "Triangle Split", 0, now, now)),
            paddockDividerNodeRefs = listOf(
                PaddockDividerNodeRefEntity(90, 0, 91),
                PaddockDividerNodeRefEntity(90, 1, 92)
            ),
            paddockRegionLabels = listOf(
                PaddockRegionLabelEntity(90, "legacy-side-a", "Upper"),
                PaddockRegionLabelEntity(90, "legacy-side-b", "Lower")
            ),
            fieldRecords = listOf(
                FieldRecordEntity(
                    id = 100,
                    recordType = FieldRecordType.TASK,
                    note = "Repair south fence",
                    latitude = 40.001,
                    longitude = -99.999,
                    pastureId = 10,
                    taskStatus = FieldTaskStatus.OPEN,
                    observedAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            ),
            displayPreferences = DisplayPreferences(
                WaterCoverageMode.LINES_ONLY,
                SpatialCoverageScope.ACCESSIBLE_COVERAGE,
                pastureFillEnabled = false,
                pastureBoundariesEnabled = false,
                waterPointsEnabled = false,
                gatesEnabled = false,
                herdBadgesEnabled = false
            )
        )
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return result
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xff) }
}
