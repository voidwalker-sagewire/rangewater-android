package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.GateEntity
import com.sagewire.rangewater.data.GateWithConnectivity
import com.sagewire.rangewater.data.PastureWithVertices
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Projection
import kotlin.math.cos
import kotlin.math.sqrt

data class ScreenCoordinate(val x: Double, val y: Double)

data class SnappedGateCandidate(
    val junctionA: FenceJunctionEntity,
    val junctionB: FenceJunctionEntity,
    val segmentRatio: Double,
    val derivedCoordinate: LatLng,
    val hingeCoordinate: LatLng,
    val latchCoordinate: LatLng,
    val segmentLengthMeters: Double,
    val pastureAId: Long,
    val pastureAName: String,
    val pastureBId: Long?,
    val pastureBName: String?,
    val isShared: Boolean
)

sealed interface GateSnapResult {
    data class Snapped(val candidate: SnappedGateCandidate) : GateSnapResult
    data object FenceTooShort : GateSnapResult
    data object NoFenceInRange : GateSnapResult
}

object GateSnappingEngine {
    fun calculateSegmentLengthMeters(first: LatLng, second: LatLng): Double {
        val latitudeRadians = Math.toRadians((first.latitude + second.latitude) / 2.0)
        val dx = (second.longitude - first.longitude) * 111_320.0 * cos(latitudeRadians)
        val dy = (second.latitude - first.latitude) * 110_540.0
        return sqrt(dx * dx + dy * dy)
    }

    fun findCandidateSegmentScreenSpace(
        tapPoint: LatLng,
        pastures: List<PastureWithVertices>,
        projection: Projection,
        tolerancePx: Float,
        gateWidthMeters: Double = GateEntity.WIDTH_14_FT
    ): GateSnapResult = findCandidateSegment(
        tapPoint = tapPoint,
        pastures = pastures,
        tolerancePx = tolerancePx.toDouble(),
        gateWidthMeters = gateWidthMeters
    ) { coordinate ->
        projection.toScreenLocation(coordinate).let { ScreenCoordinate(it.x.toDouble(), it.y.toDouble()) }
    }

    /** Pure projection seam keeps canonical snapping and clearance math JVM-testable. */
    fun findCandidateSegment(
        tapPoint: LatLng,
        pastures: List<PastureWithVertices>,
        tolerancePx: Double,
        gateWidthMeters: Double = GateEntity.WIDTH_14_FT,
        project: (LatLng) -> ScreenCoordinate
    ): GateSnapResult {
        val tapScreen = project(tapPoint)
        var closest: SnappedGateCandidate? = null
        var closestDistance = tolerancePx
        var shortFenceInRange = false

        pastures.forEach pastureLoop@ { pasture ->
            val vertices = pasture.vertices.sortedBy { it.sequence }
            if (vertices.size < 3) return@pastureLoop

            vertices.indices.forEach segmentLoop@ { index ->
                val start = vertices[index]
                val end = vertices[(index + 1) % vertices.size]
                val startCoordinate = LatLng(start.latitude, start.longitude)
                val endCoordinate = LatLng(end.latitude, end.longitude)
                val startScreen = project(startCoordinate)
                val endScreen = project(endCoordinate)
                val screenDx = endScreen.x - startScreen.x
                val screenDy = endScreen.y - startScreen.y
                val screenLengthSquared = screenDx * screenDx + screenDy * screenDy
                if (screenLengthSquared < 1e-6) return@segmentLoop

                val rawTraversalRatio = (
                    (tapScreen.x - startScreen.x) * screenDx +
                        (tapScreen.y - startScreen.y) * screenDy
                    ) / screenLengthSquared
                val screenRatio = rawTraversalRatio.coerceIn(0.0, 1.0)
                val nearestX = startScreen.x + screenRatio * screenDx
                val nearestY = startScreen.y + screenRatio * screenDy
                val distance = sqrt(
                    (tapScreen.x - nearestX) * (tapScreen.x - nearestX) +
                        (tapScreen.y - nearestY) * (tapScreen.y - nearestY)
                )
                if (distance > tolerancePx) return@segmentLoop

                val segmentLength = calculateSegmentLengthMeters(startCoordinate, endCoordinate)
                if (segmentLength < gateWidthMeters + 1.0) {
                    shortFenceInRange = true
                    return@segmentLoop
                }

                val forward = start.junctionId < end.junctionId
                val junctionA = if (forward) start.junction else end.junction
                val junctionB = if (forward) end.junction else start.junction
                val canonicalRawRatio = if (forward) rawTraversalRatio else 1.0 - rawTraversalRatio
                val clearanceRatio = ((gateWidthMeters / 2.0) + 0.5) / segmentLength
                val canonicalRatio = canonicalRawRatio.coerceIn(clearanceRatio, 1.0 - clearanceRatio)

                if (distance < closestDistance) {
                    closestDistance = distance
                    val center = interpolate(junctionA, junctionB, canonicalRatio)
                    val halfGateRatio = (gateWidthMeters / 2.0) / segmentLength
                    val hinge = interpolate(junctionA, junctionB, canonicalRatio - halfGateRatio)
                    val latch = interpolate(junctionA, junctionB, canonicalRatio + halfGateRatio)
                    val otherPasture = pastures.firstOrNull { other ->
                        other.pasture.id != pasture.pasture.id &&
                            other.hasSegment(junctionA.id, junctionB.id)
                    }
                    closest = SnappedGateCandidate(
                        junctionA = junctionA,
                        junctionB = junctionB,
                        segmentRatio = canonicalRatio,
                        derivedCoordinate = center,
                        hingeCoordinate = hinge,
                        latchCoordinate = latch,
                        segmentLengthMeters = segmentLength,
                        pastureAId = pasture.pasture.id,
                        pastureAName = pasture.pasture.name,
                        pastureBId = otherPasture?.pasture?.id,
                        pastureBName = otherPasture?.pasture?.name,
                        isShared = otherPasture != null
                    )
                }
            }
        }

        return when {
            closest != null -> GateSnapResult.Snapped(closest!!)
            shortFenceInRange -> GateSnapResult.FenceTooShort
            else -> GateSnapResult.NoFenceInRange
        }
    }

    fun resolveConnectivity(
        gate: GateEntity,
        pastures: List<PastureWithVertices>,
        junctionMap: Map<Long, LatLng>
    ): GateWithConnectivity? {
        val junctionA = junctionMap[gate.junctionAId] ?: return null
        val junctionB = junctionMap[gate.junctionBId] ?: return null
        val segmentLength = calculateSegmentLengthMeters(junctionA, junctionB)
        if (segmentLength <= 0.0 || segmentLength < gate.widthMeters) return null

        val center = interpolate(junctionA, junctionB, gate.segmentRatio)
        val halfGateRatio = (gate.widthMeters / 2.0) / segmentLength
        val hinge = interpolate(junctionA, junctionB, gate.segmentRatio - halfGateRatio)
        val latch = interpolate(junctionA, junctionB, gate.segmentRatio + halfGateRatio)
        val matchingPastures = pastures.filter { it.hasSegment(gate.junctionAId, gate.junctionBId) }
        val pastureA = matchingPastures.getOrNull(0)?.pasture
        val pastureB = matchingPastures.getOrNull(1)?.pasture

        return GateWithConnectivity(
            gate = gate,
            derivedCoordinate = center,
            hingeCoordinate = hinge,
            latchCoordinate = latch,
            pastureAId = pastureA?.id,
            pastureAName = pastureA?.name,
            pastureBId = pastureB?.id,
            pastureBName = pastureB?.name,
            isShared = pastureA != null && pastureB != null
        )
    }

    private fun PastureWithVertices.hasSegment(firstId: Long, secondId: Long): Boolean {
        val ordered = vertices.sortedBy { it.sequence }
        if (ordered.size < 3) return false
        return ordered.indices.any { index ->
            val first = ordered[index].junctionId
            val second = ordered[(index + 1) % ordered.size].junctionId
            (first == firstId && second == secondId) || (first == secondId && second == firstId)
        }
    }

    private fun interpolate(
        first: FenceJunctionEntity,
        second: FenceJunctionEntity,
        ratio: Double
    ): LatLng = LatLng(
        first.latitude + ratio * (second.latitude - first.latitude),
        first.longitude + ratio * (second.longitude - first.longitude)
    )

    private fun interpolate(first: LatLng, second: LatLng, ratio: Double): LatLng = LatLng(
        first.latitude + ratio * (second.latitude - first.latitude),
        first.longitude + ratio * (second.longitude - first.longitude)
    )
}
