package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/*
 * 🪨 BLOCK 1 — MULTI-ASSET FEATURE TESTS
 * Purpose: Verifies synchronized GeoJSON conversion and derived ring removal.
 */
class WaterFeatureConverterTest {
    private val samplePoints = listOf(
        waterPoint(1, 40.3600, -80.6300, "North Trough", WaterSourceType.TROUGH),
        waterPoint(2, 40.3650, -80.6350, "Hilltop Tank", WaterSourceType.TANK)
    )

    @Test
    fun pointFeaturesCarryIdentityMetadataAndSelection() {
        val features = WaterFeatureConverter.toPointFeatures(samplePoints, 2).features()!!

        assertEquals(2, features.size)
        assertEquals(1L, features[0].getNumberProperty("id").toLong())
        assertEquals("North Trough", features[0].getStringProperty("name"))
        assertEquals("TROUGH", features[0].getStringProperty("sourceType"))
        assertFalse(features[0].getBooleanProperty("selected"))
        assertTrue(features[0].geometry() is Point)
        assertTrue(features[1].getBooleanProperty("selected"))
    }

    @Test
    fun ringFeaturesAreClosedAndFollowRemainingEntities() {
        val allRings = WaterFeatureConverter.toRingFeatures(samplePoints, 1).features()!!

        assertEquals(2, allRings.size)
        assertTrue(allRings[0].getBooleanProperty("selected"))
        val polygon = allRings[0].geometry() as Polygon
        assertEquals(1, polygon.coordinates().size)
        assertEquals(65, polygon.coordinates().single().size)

        val afterDelete = WaterFeatureConverter.toRingFeatures(samplePoints.drop(1), null).features()!!
        assertEquals(1, afterDelete.size)
        assertEquals(1L, afterDelete.single().getNumberProperty("id").toLong())
        assertFalse(afterDelete.single().getBooleanProperty("selected"))
    }

    private fun waterPoint(
        id: Long,
        latitude: Double,
        longitude: Double,
        name: String,
        sourceType: WaterSourceType
    ) = WaterPointEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        name = name,
        sourceType = sourceType,
        createdAt = 1_000L + id,
        updatedAt = 1_000L + id
    )
}
