package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureCoordinate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * 🪨 BLOCK 1 — LIGHTWEIGHT PASTURE GEOMETRY VALIDATION
 * Purpose: Rejects duplicate, collapsed, and self-crossing rings without a new GIS library.
 */
object GeometryValidator {
    private const val EPSILON = 1e-12

    data class SegmentProjection(
        val insertionIndex: Int,
        val coordinate: PastureCoordinate,
        val distanceMeters: Double
    )

    fun validationError(vertices: List<PastureCoordinate>): String? = when {
        vertices.distinctBy { it.latitude to it.longitude }.size < 3 ->
            "Place at least three different fence corners"
        hasAdjacentDuplicates(vertices) -> "Two neighboring fence corners are identical"
        !hasNonZeroArea(vertices) -> "The fence corners do not enclose an area"
        isSelfIntersecting(vertices) -> "The pasture boundary crosses itself"
        else -> null
    }

    fun isSelfIntersecting(vertices: List<PastureCoordinate>): Boolean {
        if (vertices.size < 4) return false
        for (firstEdge in vertices.indices) {
            val firstNext = (firstEdge + 1) % vertices.size
            for (secondEdge in firstEdge + 1 until vertices.size) {
                val secondNext = (secondEdge + 1) % vertices.size
                val adjacent = firstEdge == secondEdge || firstNext == secondEdge || secondNext == firstEdge
                if (adjacent) continue
                if (segmentsIntersect(
                        vertices[firstEdge], vertices[firstNext],
                        vertices[secondEdge], vertices[secondNext]
                    )
                ) return true
            }
        }
        return false
    }

    /** Projects a map tap onto the closest closed-polygon segment using local meters. */
    fun projectOntoClosestSegment(
        tap: PastureCoordinate,
        vertices: List<PastureCoordinate>
    ): SegmentProjection? {
        if (vertices.size < 2) return null
        val latitudeRadians = Math.toRadians(tap.latitude)
        val metersPerLongitudeDegree = 111_320.0 * cos(latitudeRadians)
        val metersPerLatitudeDegree = 110_540.0
        var best: SegmentProjection? = null

        vertices.indices.forEach { index ->
            val start = vertices[index]
            val end = vertices[(index + 1) % vertices.size]
            val startX = (start.longitude - tap.longitude) * metersPerLongitudeDegree
            val startY = (start.latitude - tap.latitude) * metersPerLatitudeDegree
            val endX = (end.longitude - tap.longitude) * metersPerLongitudeDegree
            val endY = (end.latitude - tap.latitude) * metersPerLatitudeDegree
            val deltaX = endX - startX
            val deltaY = endY - startY
            val lengthSquared = deltaX * deltaX + deltaY * deltaY
            val fraction = if (lengthSquared <= EPSILON) {
                0.0
            } else {
                (-(startX * deltaX + startY * deltaY) / lengthSquared).coerceIn(0.0, 1.0)
            }
            val projectedX = startX + fraction * deltaX
            val projectedY = startY + fraction * deltaY
            val distance = sqrt(projectedX * projectedX + projectedY * projectedY)
            if (best == null || distance < best!!.distanceMeters) {
                best = SegmentProjection(
                    insertionIndex = index + 1,
                    coordinate = PastureCoordinate(
                        latitude = start.latitude + fraction * (end.latitude - start.latitude),
                        longitude = start.longitude + fraction * (end.longitude - start.longitude)
                    ),
                    distanceMeters = distance
                )
            }
        }
        return best
    }

    private fun hasAdjacentDuplicates(vertices: List<PastureCoordinate>): Boolean =
        vertices.indices.any { index ->
            val next = vertices[(index + 1) % vertices.size]
            samePoint(vertices[index], next)
        }

    private fun hasNonZeroArea(vertices: List<PastureCoordinate>): Boolean {
        var twiceArea = 0.0
        vertices.indices.forEach { index ->
            val next = vertices[(index + 1) % vertices.size]
            twiceArea += vertices[index].longitude * next.latitude - next.longitude * vertices[index].latitude
        }
        return abs(twiceArea) > EPSILON
    }

    private fun segmentsIntersect(
        a: PastureCoordinate,
        b: PastureCoordinate,
        c: PastureCoordinate,
        d: PastureCoordinate
    ): Boolean {
        val o1 = orientation(a, b, c)
        val o2 = orientation(a, b, d)
        val o3 = orientation(c, d, a)
        val o4 = orientation(c, d, b)
        if (o1 != o2 && o3 != o4) return true
        return (o1 == 0 && onSegment(a, c, b)) ||
            (o2 == 0 && onSegment(a, d, b)) ||
            (o3 == 0 && onSegment(c, a, d)) ||
            (o4 == 0 && onSegment(c, b, d))
    }

    private fun orientation(a: PastureCoordinate, b: PastureCoordinate, c: PastureCoordinate): Int {
        val cross = (b.longitude - a.longitude) * (c.latitude - a.latitude) -
            (b.latitude - a.latitude) * (c.longitude - a.longitude)
        return when {
            abs(cross) <= EPSILON -> 0
            cross > 0 -> 1
            else -> 2
        }
    }

    private fun onSegment(a: PastureCoordinate, point: PastureCoordinate, b: PastureCoordinate) =
        point.longitude <= max(a.longitude, b.longitude) + EPSILON &&
            point.longitude >= min(a.longitude, b.longitude) - EPSILON &&
            point.latitude <= max(a.latitude, b.latitude) + EPSILON &&
            point.latitude >= min(a.latitude, b.latitude) - EPSILON

    private fun samePoint(a: PastureCoordinate, b: PastureCoordinate) =
        abs(a.latitude - b.latitude) <= EPSILON && abs(a.longitude - b.longitude) <= EPSILON
}
