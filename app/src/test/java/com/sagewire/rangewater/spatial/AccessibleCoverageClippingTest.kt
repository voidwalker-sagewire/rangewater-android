package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.ui.map.MapConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfMeasurement
import org.maplibre.turf.TurfTransformation

class AccessibleCoverageClippingTest {
    private val center = Point.fromLngLat(-100.0, 40.0)

    @Test
    fun waterInsideSmallerPastureClipsPreferredCircle() {
        val preferred = circle(MapConfig.PREFERRED_RADIUS_METERS)
        val clipped = JtsGeometryConverter.clipZoneToPastures(
            preferred,
            listOf(rectangle(-100.002, 39.998, -99.998, 40.002))
        )

        assertEquals(1, clipped.size)
        val clippedArea = TurfMeasurement.area(clipped.single())
        assertTrue(clippedArea > 0.0)
        assertTrue(clippedArea < TurfMeasurement.area(preferred))
    }

    @Test
    fun fenceStraddlingWaterClipsToApproximatelyHalfCircle() {
        val preferred = circle(MapConfig.PREFERRED_RADIUS_METERS)
        val clipped = JtsGeometryConverter.clipZoneToPastures(
            preferred,
            listOf(rectangle(-100.0, 39.995, -99.990, 40.005))
        )

        val fullArea = TurfMeasurement.area(preferred)
        assertEquals(1, clipped.size)
        assertEquals(fullArea / 2.0, TurfMeasurement.area(clipped.single()), fullArea * 0.05)
    }

    @Test
    fun fullPastureIntersectionPreservesTransitionHole() {
        val preferred = circle(MapConfig.PREFERRED_RADIUS_METERS)
        val transitionBoundary = circle(MapConfig.TRANSITION_RADIUS_METERS)
        val annulus = Polygon.fromLngLats(
            listOf(
                transitionBoundary.coordinates().single(),
                preferred.coordinates().single().asReversed()
            )
        )

        val clipped = JtsGeometryConverter.clipZoneToPastures(
            annulus,
            listOf(rectangle(-100.01, 39.99, -99.99, 40.01))
        )

        assertEquals(1, clipped.size)
        assertEquals(2, clipped.single().coordinates().size)
        assertEquals(105_070.9, TurfMeasurement.area(clipped.single()), 2_500.0)
    }

    @Test
    fun disjointPastureProducesNoCoverage() {
        val clipped = JtsGeometryConverter.clipZoneToPastures(
            circle(MapConfig.PREFERRED_RADIUS_METERS),
            listOf(rectangle(-100.0, 41.0, -99.99, 41.01))
        )

        assertTrue(clipped.isEmpty())
    }

    @Test
    fun adjoiningAssignedPasturesAreUnionedBeforeClipping() {
        val preferred = circle(MapConfig.PREFERRED_RADIUS_METERS)
        val clipped = JtsGeometryConverter.clipZoneToPastures(
            preferred,
            listOf(
                rectangle(-100.01, 40.0, -99.99, 40.01),
                rectangle(-100.01, 39.99, -99.99, 40.0)
            )
        )

        assertEquals(1, clipped.size)
        val fullArea = TurfMeasurement.area(preferred)
        assertEquals(fullArea, TurfMeasurement.area(clipped.single()), fullArea * 0.02)
    }

    private fun circle(radiusMeters: Double): Polygon = TurfTransformation.circle(
        center,
        radiusMeters,
        MapConfig.BUFFER_CIRCLE_STEPS,
        TurfConstants.UNIT_METERS
    )

    private fun rectangle(west: Double, south: Double, east: Double, north: Double): Polygon =
        Polygon.fromLngLats(
            listOf(
                listOf(
                    Point.fromLngLat(west, south),
                    Point.fromLngLat(west, north),
                    Point.fromLngLat(east, north),
                    Point.fromLngLat(east, south),
                    Point.fromLngLat(west, south)
                )
            )
        )
}
