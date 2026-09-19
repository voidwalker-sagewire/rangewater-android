package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import com.sagewire.rangewater.ui.map.applyWaterMovePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
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

        val remainingPoints = samplePoints.filterNot { it.id == 2L }
        val afterDelete = WaterFeatureConverter.toRingFeatures(remainingPoints, null).features()!!
        assertEquals(1, afterDelete.size)
        assertEquals(1L, afterDelete.single().getNumberProperty("id").toLong())
        assertFalse(afterDelete.single().getBooleanProperty("selected"))
    }

    @Test
    fun movePreviewReplacesOnlyTargetCoordinatesAndMovesItsGeoJson() {
        val draft = LatLng(40.3725, -80.6415)
        val preview = applyWaterMovePreview(samplePoints, samplePoints[0], draft)

        assertEquals(samplePoints[0].id, preview[0].id)
        assertEquals(samplePoints[0].name, preview[0].name)
        assertEquals(samplePoints[0].sourceType, preview[0].sourceType)
        assertEquals(samplePoints[0].createdAt, preview[0].createdAt)
        assertEquals(draft.latitude, preview[0].latitude, 0.0)
        assertEquals(draft.longitude, preview[0].longitude, 0.0)
        assertEquals(samplePoints[1], preview[1])

        val point = WaterFeatureConverter.toPointFeatures(preview, 1).features()!![0]
            .geometry() as Point
        assertEquals(draft.longitude, point.longitude(), 0.000001)
        assertEquals(draft.latitude, point.latitude(), 0.000001)

        val originalRing = WaterFeatureConverter.toRingFeatures(samplePoints, 1)
            .features()!![0].geometry() as Polygon
        val previewRing = WaterFeatureConverter.toRingFeatures(preview, 1)
            .features()!![0].geometry() as Polygon
        assertFalse(originalRing.coordinates()[0][0] == previewRing.coordinates()[0][0])
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
