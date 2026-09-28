package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PaddockSplitPlanEntity
import com.sagewire.rangewater.data.PastureCoordinate
import com.sagewire.rangewater.data.PastureWithVertices
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import kotlin.math.cos
import kotlin.math.sqrt

data class PaddockBoundaryAnchor(
    val junctionAId: Long,
    val junctionBId: Long,
    val segmentRatio: Double,
    val coordinate: PastureCoordinate
)

data class PaddockSplitResult(
    val start: PaddockBoundaryAnchor,
    val end: PaddockBoundaryAnchor,
    val sideA: List<PastureCoordinate>,
    val sideB: List<PastureCoordinate>,
    val sideAAcres: Double,
    val sideBAcres: Double,
    val parentAcres: Double
)

/** Robust, non-destructive split geometry for a single straight temporary-polywire plan. */
object PaddockSplitEngine {
    private const val MIN_SIDE_SQUARE_METERS = 1.0
    private const val RECONCILIATION_TOLERANCE = 0.005
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    fun nearestBoundaryAnchor(
        tap: PastureCoordinate,
        pasture: PastureWithVertices,
        toleranceMeters: Double
    ): PaddockBoundaryAnchor? {
        require(toleranceMeters >= 0.0)
        val vertices = pasture.vertices.sortedBy { it.sequence }
        if (vertices.size < 3) return null
        var nearest: PaddockBoundaryAnchor? = null
        var nearestDistance = toleranceMeters
        vertices.indices.forEach { index ->
            val start = vertices[index]
            val end = vertices[(index + 1) % vertices.size]
            val projected = project(tap, start.latitude, start.longitude, end.latitude, end.longitude)
            if (projected.distanceMeters <= nearestDistance) {
                val forward = start.junctionId < end.junctionId
                nearestDistance = projected.distanceMeters
                nearest = PaddockBoundaryAnchor(
                    junctionAId = minOf(start.junctionId, end.junctionId),
                    junctionBId = maxOf(start.junctionId, end.junctionId),
                    segmentRatio = if (forward) projected.ratio else 1.0 - projected.ratio,
                    coordinate = projected.coordinate
                )
            }
        }
        return nearest
    }

    fun resolve(plan: PaddockSplitPlanEntity, pasture: PastureWithVertices): PaddockSplitResult {
        require(plan.pastureId == pasture.pasture.id) { "Paddock split belongs to another pasture" }
        val start = resolveAnchor(
            pasture,
            plan.startJunctionAId,
            plan.startJunctionBId,
            plan.startSegmentRatio
        )
        val end = resolveAnchor(
            pasture,
            plan.endJunctionAId,
            plan.endJunctionBId,
            plan.endSegmentRatio
        )
        return split(pasture, start, end)
    }

    fun split(
        pasture: PastureWithVertices,
        start: PaddockBoundaryAnchor,
        end: PaddockBoundaryAnchor
    ): PaddockSplitResult {
        require(start.junctionAId != end.junctionAId ||
            start.junctionBId != end.junctionBId ||
            kotlin.math.abs(start.segmentRatio - end.segmentRatio) > 1e-9
        ) { "Paddock split endpoints must be different" }

        // Re-resolve against the selected pasture so callers cannot supply detached points.
        val resolvedStart = resolveAnchor(
            pasture, start.junctionAId, start.junctionBId, start.segmentRatio
        )
        val resolvedEnd = resolveAnchor(
            pasture, end.junctionAId, end.junctionBId, end.segmentRatio
        )
        val parentCoordinates = pasture.vertices.sortedBy { it.sequence }.map {
            PastureCoordinate(it.latitude, it.longitude, junctionId = it.junctionId)
        }
        require(parentCoordinates.size >= 3) { "Pasture requires at least three corners" }
        val parent = parentCoordinates.toJtsPolygon()
        require(parent.isValid && parent.area > 0.0) { "Pasture boundary is invalid" }

        val line = geometryFactory.createLineString(
            arrayOf(resolvedStart.coordinate.toJts(), resolvedEnd.coordinate.toJts())
        )
        require(line.length > 1e-12) { "Paddock split endpoints must be different" }
        require(parent.covers(line)) {
            "Paddock split must pass through the pasture interior"
        }

        // Build both rings by walking the authoritative ordered fence boundary.
        // This avoids polygonizer precision differences while preserving the exact
        // persisted split anchors and their shared straight divider.
        val startEdge = boundaryEdgeIndex(pasture, resolvedStart)
        val endEdge = boundaryEdgeIndex(pasture, resolvedEnd)
        require(startEdge != endEdge) { "Paddock split cannot follow one boundary segment" }
        val firstRing = boundaryPath(
            parentCoordinates,
            startEdge,
            endEdge,
            resolvedStart.coordinate,
            resolvedEnd.coordinate
        )
        val secondRing = boundaryPath(
            parentCoordinates,
            endEdge,
            startEdge,
            resolvedEnd.coordinate,
            resolvedStart.coordinate
        )
        val pieces = listOf(firstRing.toJtsPolygon(), secondRing.toJtsPolygon())
        require(pieces.all { it.isValid && it.area > 0.0 && parent.covers(it) }) {
            "Paddock split must create exactly two valid regions"
        }

        val ordered = pieces.sortedBy { polygon ->
            sideOfLine(resolvedStart.coordinate, resolvedEnd.coordinate, polygon.interiorPoint.coordinate)
        }
        val sideA = ordered.first().toPastureCoordinates()
        val sideB = ordered.last().toPastureCoordinates()
        val sideAAcres = AcreageCalculator.calculateAcres(sideA)
        val sideBAcres = AcreageCalculator.calculateAcres(sideB)
        val parentAcres = AcreageCalculator.calculateAcres(parentCoordinates)
        val minimumAcres = AcreageCalculator.squareMetersToAcres(MIN_SIDE_SQUARE_METERS)
        require(sideAAcres >= minimumAcres && sideBAcres >= minimumAcres) {
            "Paddock split creates a region too small for reliable map geometry"
        }
        val difference = kotlin.math.abs((sideAAcres + sideBAcres) - parentAcres)
        require(parentAcres > 0.0 && difference / parentAcres <= RECONCILIATION_TOLERANCE) {
            "Paddock acreages do not reconcile with the parent pasture"
        }
        return PaddockSplitResult(
            start = resolvedStart,
            end = resolvedEnd,
            sideA = sideA,
            sideB = sideB,
            sideAAcres = sideAAcres,
            sideBAcres = sideBAcres,
            parentAcres = parentAcres
        )
    }

    fun resolveAnchor(
        pasture: PastureWithVertices,
        junctionAId: Long,
        junctionBId: Long,
        segmentRatio: Double
    ): PaddockBoundaryAnchor {
        require(junctionAId < junctionBId) { "Paddock endpoint anchors must be canonical" }
        require(segmentRatio.isFinite() && segmentRatio in 0.0..1.0) {
            "Paddock endpoint ratio must be finite and between 0 and 1"
        }
        val ordered = pasture.vertices.sortedBy { it.sequence }
        val segmentExists = ordered.indices.any { index ->
            val first = ordered[index].junctionId
            val second = ordered[(index + 1) % ordered.size].junctionId
            minOf(first, second) == junctionAId && maxOf(first, second) == junctionBId
        }
        require(segmentExists) { "Paddock endpoint does not belong to this pasture boundary" }
        val first = ordered.firstOrNull { it.junctionId == junctionAId }
            ?: throw IllegalArgumentException("Fence junction #$junctionAId not found in pasture")
        val second = ordered.firstOrNull { it.junctionId == junctionBId }
            ?: throw IllegalArgumentException("Fence junction #$junctionBId not found in pasture")
        return PaddockBoundaryAnchor(
            junctionAId = junctionAId,
            junctionBId = junctionBId,
            segmentRatio = segmentRatio,
            coordinate = PastureCoordinate(
                latitude = first.latitude + segmentRatio * (second.latitude - first.latitude),
                longitude = first.longitude + segmentRatio * (second.longitude - first.longitude)
            )
        )
    }

    private fun boundaryEdgeIndex(
        pasture: PastureWithVertices,
        anchor: PaddockBoundaryAnchor
    ): Int {
        val ordered = pasture.vertices.sortedBy { it.sequence }
        return ordered.indices.first { index ->
            val first = ordered[index].junctionId
            val second = ordered[(index + 1) % ordered.size].junctionId
            minOf(first, second) == anchor.junctionAId && maxOf(first, second) == anchor.junctionBId
        }
    }

    private fun boundaryPath(
        vertices: List<PastureCoordinate>,
        startEdge: Int,
        endEdge: Int,
        start: PastureCoordinate,
        end: PastureCoordinate
    ): List<PastureCoordinate> {
        val path = mutableListOf(start)
        var vertexIndex = (startEdge + 1) % vertices.size
        while (true) {
            path += vertices[vertexIndex]
            if (vertexIndex == endEdge) break
            vertexIndex = (vertexIndex + 1) % vertices.size
        }
        path += end
        return path.removeConsecutiveDuplicates()
    }

    private fun List<PastureCoordinate>.removeConsecutiveDuplicates(): List<PastureCoordinate> =
        fold(mutableListOf()) { result, coordinate ->
            if (result.lastOrNull()?.let {
                    kotlin.math.abs(it.latitude - coordinate.latitude) <= 1e-12 &&
                        kotlin.math.abs(it.longitude - coordinate.longitude) <= 1e-12
                } != true
            ) {
                result += coordinate
            }
            result
        }

    private data class Projection(
        val ratio: Double,
        val coordinate: PastureCoordinate,
        val distanceMeters: Double
    )

    private fun project(
        tap: PastureCoordinate,
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Projection {
        val latitudeRadians = Math.toRadians(tap.latitude)
        val metersPerLongitudeDegree = 111_320.0 * cos(latitudeRadians)
        val startX = (startLongitude - tap.longitude) * metersPerLongitudeDegree
        val startY = (startLatitude - tap.latitude) * 110_540.0
        val endX = (endLongitude - tap.longitude) * metersPerLongitudeDegree
        val endY = (endLatitude - tap.latitude) * 110_540.0
        val dx = endX - startX
        val dy = endY - startY
        val lengthSquared = dx * dx + dy * dy
        val ratio = if (lengthSquared <= 1e-12) 0.0 else (-(startX * dx + startY * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val nearestX = startX + ratio * dx
        val nearestY = startY + ratio * dy
        return Projection(
            ratio = ratio,
            coordinate = PastureCoordinate(
                latitude = startLatitude + ratio * (endLatitude - startLatitude),
                longitude = startLongitude + ratio * (endLongitude - startLongitude)
            ),
            distanceMeters = sqrt(nearestX * nearestX + nearestY * nearestY)
        )
    }

    private fun List<PastureCoordinate>.toJtsPolygon(): Polygon {
        val coordinates = map { it.toJts() }.toMutableList()
        coordinates += coordinates.first().copy()
        return geometryFactory.createPolygon(coordinates.toTypedArray())
    }

    private fun PastureCoordinate.toJts() = Coordinate(longitude, latitude)

    private fun Polygon.toPastureCoordinates(): List<PastureCoordinate> =
        exteriorRing.coordinates.dropLast(1).map { PastureCoordinate(it.y, it.x) }

    private fun sideOfLine(
        start: PastureCoordinate,
        end: PastureCoordinate,
        point: Coordinate
    ): Double = (end.longitude - start.longitude) * (point.y - start.latitude) -
        (end.latitude - start.latitude) * (point.x - start.longitude)
}
