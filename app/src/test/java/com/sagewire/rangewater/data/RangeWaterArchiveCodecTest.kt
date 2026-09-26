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
            pastureForageObservations = emptyList()
        )
        val dataJson = gson.toJsonTree(legacyData).asJsonObject.apply {
            remove("grazingCircuits")
            remove("circuitPastures")
            remove("circuitPastureRoles")
            remove("herdCircuitAssignments")
            remove("pastureForageObservations")
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
    }

    private fun completeData(): RangeWaterBackupData {
        val now = 100L
        return RangeWaterBackupData(
            waterPoints = listOf(WaterPointEntity(1, 40.0, -100.0, "Tank 1", WaterSourceType.TANK, "", now, now)),
            pastures = listOf(PastureEntity(10, "North", "", now, now)),
            junctions = listOf(
                FenceJunctionEntity(20, 40.0, -100.0),
                FenceJunctionEntity(21, 40.0, -99.99),
                FenceJunctionEntity(22, 40.01, -99.99)
            ),
            vertices = listOf(
                PastureVertexEntity(30, 10, 0, 20),
                PastureVertexEntity(31, 10, 1, 21),
                PastureVertexEntity(32, 10, 2, 22)
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
