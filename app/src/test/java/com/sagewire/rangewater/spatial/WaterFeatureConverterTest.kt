package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.PastureWithVertices
import com.sagewire.rangewater.ui.map.MapConfig
import com.sagewire.rangewater.ui.map.SpatialCoverageScope
import com.sagewire.rangewater.ui.map.applyWaterMovePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfMeasurement

/*
 * 🪨 BLOCK 1 — MULTI-ASSET FEATURE TESTS
 * Purpose: Verifies synchronized GeoJSON conversion and tiered derived coverage.
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
        assertEquals("marker-water-trough", features[0].getStringProperty("markerIcon"))
        assertEquals("marker-water-tank", features[1].getStringProperty("markerIcon"))
        assertFalse(features[0].getBooleanProperty("selected"))
        assertTrue(features[0].geometry() is Point)
        assertTrue(features[1].getBooleanProperty("selected"))
    }

    @Test
    fun ringFeaturesProvideBothZonesAndFollowRemainingEntities() {
        val allRings = WaterFeatureConverter.toRingFeatures(samplePoints, 1).features()!!

        assertEquals(4, allRings.size)
        val firstWater = allRings.filter { it.getNumberProperty("id").toLong() == 1L }
        assertEquals(2, firstWater.size)
        assertEquals(
            setOf(MapConfig.ZONE_PREFERRED, MapConfig.ZONE_TRANSITION),
            firstWater.map { it.getStringProperty("zone") }.toSet()
        )
        assertTrue(firstWater.all { it.getBooleanProperty("selected") })
        val polygon = firstWater.first {
            it.getStringProperty("zone") == MapConfig.ZONE_PREFERRED
        }.geometry() as Polygon
        assertEquals(1, polygon.coordinates().size)
        assertEquals(65, polygon.coordinates().single().size)

        val remainingPoints = samplePoints.filterNot { it.id == 2L }
        val afterDelete = WaterFeatureConverter.toRingFeatures(remainingPoints, null).features()!!
        assertEquals(2, afterDelete.size)
        assertTrue(afterDelete.all { it.getNumberProperty("id").toLong() == 1L })
        assertTrue(afterDelete.none { it.getBooleanProperty("selected") })
    }

    @Test
    fun transitionZoneIsTrueAnnulusWithOppositeWindingAndExpectedArea() {
        val features = WaterFeatureConverter.toRingFeatures(listOf(samplePoints[0]), null)
            .features()!!
        val preferred = features.first {
            it.getStringProperty("zone") == MapConfig.ZONE_PREFERRED
        }.geometry() as Polygon
        val transition = features.first {
            it.getStringProperty("zone") == MapConfig.ZONE_TRANSITION
        }.geometry() as Polygon

        assertEquals(1, preferred.coordinates().size)
        assertEquals(2, transition.coordinates().size)
        assertTrue(transition.coordinates().all { it.size == 65 })

        val outerWinding = signedArea(transition.coordinates()[0])
        val holeWinding = signedArea(transition.coordinates()[1])
        assertTrue("Annulus hole must wind opposite its exterior", outerWinding * holeWinding < 0.0)

        val preferredArea = TurfMeasurement.area(preferred)
        val transitionArea = TurfMeasurement.area(transition)
        assertEquals(186_792.6, preferredArea, 1_500.0)
        assertEquals(105_070.9, transitionArea, 1_500.0)
        assertTrue("Transition must exclude the preferred center", transitionArea < 150_000.0)
    }

    @Test
    fun selectedStatusPropagatesToBothZonesOnlyForSelectedWater() {
        val features = WaterFeatureConverter.toRingFeatures(samplePoints, 1).features()!!
        val selected = features.filter { it.getNumberProperty("id").toLong() == 1L }
        val unselected = features.filter { it.getNumberProperty("id").toLong() == 2L }

        assertEquals(2, selected.size)
        assertEquals(2, unselected.size)
        assertTrue(selected.all { it.getBooleanProperty("selected") })
        assertFalse(unselected.any { it.getBooleanProperty("selected") })
    }

    @Test
    fun accessibleCoverageEmitsNoGeometryForUnassignedWaterButKeepsPin() {
        val coverage = WaterFeatureConverter.toRingFeatures(
            points = listOf(samplePoints[0]),
            selectedId = samplePoints[0].id,
            scope = SpatialCoverageScope.ACCESSIBLE_COVERAGE,
            assignments = emptyMap(),
            pastures = listOf(squarePasture())
        )

        assertTrue(coverage.features()!!.isEmpty())
        assertEquals(
            1,
            WaterFeatureConverter.toPointFeatures(listOf(samplePoints[0]), samplePoints[0].id)
                .features()!!.size
        )
    }

    @Test
    fun accessibleCoverageClipsAssignedWaterAndPreservesSelectionMetadata() {
        val coverage = WaterFeatureConverter.toRingFeatures(
            points = listOf(samplePoints[0]),
            selectedId = samplePoints[0].id,
            scope = SpatialCoverageScope.ACCESSIBLE_COVERAGE,
            assignments = mapOf(samplePoints[0].id to listOf(11L)),
            pastures = listOf(squarePasture())
        ).features()!!

        assertTrue(coverage.isNotEmpty())
        assertTrue(coverage.all { it.getBooleanProperty("selected") })
        assertTrue(coverage.all { it.getNumberProperty("id").toLong() == samplePoints[0].id })
        assertEquals(
            setOf(MapConfig.ZONE_PREFERRED, MapConfig.ZONE_TRANSITION),
            coverage.map { it.getStringProperty("zone") }.toSet()
        )
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

        val originalZones = WaterFeatureConverter.toRingFeatures(samplePoints, 1)
            .features()!!.filter { it.getNumberProperty("id").toLong() == 1L }
        val previewZones = WaterFeatureConverter.toRingFeatures(preview, 1)
            .features()!!.filter { it.getNumberProperty("id").toLong() == 1L }
        originalZones.zip(previewZones).forEach { (original, moved) ->
            val originalPolygon = original.geometry() as Polygon
            val movedPolygon = moved.geometry() as Polygon
            assertFalse(originalPolygon.coordinates()[0][0] == movedPolygon.coordinates()[0][0])
        }
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

    private fun squarePasture(): PastureWithVertices = PastureWithVertices(
        pasture = PastureEntity(
            id = 11L,
            name = "North Field",
            createdAt = 2_000L,
            updatedAt = 2_000L
        ),
        vertices = listOf(
            vertex(1, 0, 40.3560, -80.6300),
            vertex(2, 1, 40.3640, -80.6300),
            vertex(3, 2, 40.3640, -80.6250),
            vertex(4, 3, 40.3560, -80.6250)
        )
    )

    private fun vertex(id: Long, sequence: Int, latitude: Double, longitude: Double) =
        PastureVertexWithJunction(
            PastureVertexEntity(id, 11, sequence, id),
            FenceJunctionEntity(id, latitude, longitude)
        )

    private fun signedArea(ring: List<Point>): Double = ring.zipWithNext().sumOf { (a, b) ->
        (a.longitude() * b.latitude()) - (b.longitude() * a.latitude())
    } / 2.0
}
