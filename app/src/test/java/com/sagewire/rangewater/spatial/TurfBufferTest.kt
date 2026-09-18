package com.sagewire.rangewater.spatial

import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.Point
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfMeasurement
import org.maplibre.turf.TurfTransformation

/*
 * 🪨 BLOCK 1 — SPATIAL GEODESIC MATH UNIT TESTS
 * Purpose: Verifies the native Turf Java construction of RangeWater's 800-foot ring.
 * 🎮 Behavior: Checks closure, vertex count, and radius at representative latitudes.
 */
class TurfBufferTest {
    private val targetRadiusMeters = 243.84
    private val steps = 64
    private val toleranceMeters = 0.5

    private data class TestLocation(
        val name: String,
        val longitude: Double,
        val latitude: Double
    )

    private val testLocations = listOf(
        TestLocation("Eastern Ohio", -80.6300, 40.3600),
        TestLocation("Texas Panhandle", -101.8300, 35.2200),
        TestLocation("Nebraska Sandhills", -102.0000, 42.0000)
    )

    @Test
    fun geodesicBufferIsClosedAndAccurateAcrossConusLatitudes() {
        testLocations.forEach { location ->
            val center = Point.fromLngLat(location.longitude, location.latitude)
            val polygon = TurfTransformation.circle(
                center,
                targetRadiusMeters,
                steps,
                TurfConstants.UNIT_METERS
            )
            val rings = polygon.coordinates()

            assertEquals("Expected one ring at ${location.name}", 1, rings.size)
            val coordinates = rings.single()
            assertEquals(
                "A 64-step circle must contain 64 vertices plus closure at ${location.name}",
                65,
                coordinates.size
            )

            assertEquals(
                "Closing longitude differs at ${location.name}",
                coordinates.first().longitude(),
                coordinates.last().longitude(),
                1e-9
            )
            assertEquals(
                "Closing latitude differs at ${location.name}",
                coordinates.first().latitude(),
                coordinates.last().latitude(),
                1e-9
            )

            // Sample four quarter-turn vertices; the closing vertex duplicates index 0.
            listOf(0, 16, 32, 48).forEach { index ->
                val measuredDistance = TurfMeasurement.distance(
                    center,
                    coordinates[index],
                    TurfConstants.UNIT_METERS
                )
                assertEquals(
                    "Radius differs at vertex $index for ${location.name}",
                    targetRadiusMeters,
                    measuredDistance,
                    toleranceMeters
                )
            }
        }
    }
}
