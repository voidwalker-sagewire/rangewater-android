package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureWithVertices
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.ui.map.MapConfig
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfMeasurement
import org.maplibre.turf.TurfTransformation

data class PastureCoverageMetrics(
    val pastureId: Long,
    val totalAcreage: Double,
    val assignedWaterCount: Int,
    val preferredAcreage: Double,
    val preferredPercentage: Double,
    val transitionOnlyAcreage: Double,
    val transitionOnlyPercentage: Double,
    val beyondAcreage: Double,
    val beyondPercentage: Double
)

/*
 * 🪨 BLOCK 1 — MUTUALLY EXCLUSIVE PASTURE COVERAGE ANALYTICS
 * Purpose: Partitions one pasture into preferred, transition-only, and beyond areas.
 * 🪨 Protected: Callers supply only explicitly assigned water; nearby unassigned water
 *    must never enter this calculation.
 */
object PastureAnalyticsCalculator {
    fun computeMetrics(
        pasture: PastureWithVertices,
        assignedWaterPoints: List<WaterPointEntity>
    ): PastureCoverageMetrics {
        val orderedVertices = pasture.vertices.sortedBy { it.sequence }
        val assigned = assignedWaterPoints.distinctBy { it.id }
        if (orderedVertices.size < 3) {
            return emptyMetrics(pasture.pasture.id, assigned.size)
        }

        val pasturePolygon = Polygon.fromLngLats(
            listOf(
                orderedVertices.map { vertex ->
                    Point.fromLngLat(vertex.longitude, vertex.latitude)
                }.let { it + it.first() }
            )
        )
        val totalAcreage = polygonAcreage(pasturePolygon)
        if (totalAcreage <= 0.0) {
            return emptyMetrics(pasture.pasture.id, assigned.size)
        }
        if (assigned.isEmpty()) {
            return PastureCoverageMetrics(
                pastureId = pasture.pasture.id,
                totalAcreage = totalAcreage,
                assignedWaterCount = 0,
                preferredAcreage = 0.0,
                preferredPercentage = 0.0,
                transitionOnlyAcreage = 0.0,
                transitionOnlyPercentage = 0.0,
                beyondAcreage = totalAcreage,
                beyondPercentage = 100.0
            )
        }

        val preferredDisks = assigned.map { point ->
            coverageDisk(point, MapConfig.PREFERRED_RADIUS_METERS)
        }
        val outerDisks = assigned.map { point ->
            coverageDisk(point, MapConfig.TRANSITION_RADIUS_METERS)
        }

        val pastureGeometry = JtsGeometryConverter.toJtsPolygon(pasturePolygon)
        val preferredUnion = OverlayNGRobust.union(preferredDisks.map(JtsGeometryConverter::toJtsPolygon))
        val outerUnion = OverlayNGRobust.union(outerDisks.map(JtsGeometryConverter::toJtsPolygon))

        val preferred = OverlayNGRobust.overlay(
            pastureGeometry,
            preferredUnion,
            OverlayNG.INTERSECTION
        )
        val withinOuterRadius = OverlayNGRobust.overlay(
            pastureGeometry,
            outerUnion,
            OverlayNG.INTERSECTION
        )
        val transitionOnly = OverlayNGRobust.overlay(
            withinOuterRadius,
            preferredUnion,
            OverlayNG.DIFFERENCE
        )
        val beyond = OverlayNGRobust.overlay(
            pastureGeometry,
            outerUnion,
            OverlayNG.DIFFERENCE
        )

        val preferredAcreage = geometryAcreage(preferred)
        val transitionOnlyAcreage = geometryAcreage(transitionOnly)
        val beyondAcreage = geometryAcreage(beyond)

        return PastureCoverageMetrics(
            pastureId = pasture.pasture.id,
            totalAcreage = totalAcreage,
            assignedWaterCount = assigned.size,
            preferredAcreage = preferredAcreage,
            preferredPercentage = percentage(preferredAcreage, totalAcreage),
            transitionOnlyAcreage = transitionOnlyAcreage,
            transitionOnlyPercentage = percentage(transitionOnlyAcreage, totalAcreage),
            beyondAcreage = beyondAcreage,
            beyondPercentage = percentage(beyondAcreage, totalAcreage)
        )
    }

    private fun coverageDisk(point: WaterPointEntity, radiusMeters: Double): Polygon =
        TurfTransformation.circle(
            Point.fromLngLat(point.longitude, point.latitude),
            radiusMeters,
            MapConfig.BUFFER_CIRCLE_STEPS,
            TurfConstants.UNIT_METERS
        )

    private fun geometryAcreage(geometry: Geometry): Double =
        JtsGeometryConverter.toMapLibrePolygons(geometry).sumOf(::polygonAcreage)

    private fun polygonAcreage(polygon: Polygon): Double =
        AcreageCalculator.squareMetersToAcres(TurfMeasurement.area(polygon))

    private fun percentage(acres: Double, totalAcres: Double): Double =
        if (totalAcres > 0.0) acres / totalAcres * 100.0 else 0.0

    private fun emptyMetrics(pastureId: Long, assignedWaterCount: Int) =
        PastureCoverageMetrics(
            pastureId = pastureId,
            totalAcreage = 0.0,
            assignedWaterCount = assignedWaterCount,
            preferredAcreage = 0.0,
            preferredPercentage = 0.0,
            transitionOnlyAcreage = 0.0,
            transitionOnlyPercentage = 0.0,
            beyondAcreage = 0.0,
            beyondPercentage = 0.0
        )
}
