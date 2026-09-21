package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.GateEntity
import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.PastureWithVertices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class GateSnappingEngineTest {
    @Test
    fun reverseTraversalProducesCanonicalEndpointsAndRatio() {
        val junctionA = junction(10, 40.0, -100.0)
        val junctionB = junction(20, 40.0, -99.98)
        val pasture = pasture(
            1,
            "Reverse",
            listOf(
                vertex(1, 1, 0, junctionB),
                vertex(2, 1, 1, junctionA),
                vertex(3, 1, 2, junction(30, 40.01, -100.0))
            )
        )
        val result = GateSnappingEngine.findCandidateSegment(
            tapPoint = LatLng(40.0, -99.995),
            pastures = listOf(pasture),
            tolerancePx = 10.0,
            project = ::linearProjection
        )

        assertTrue(result is GateSnapResult.Snapped)
        val candidate = (result as GateSnapResult.Snapped).candidate
        assertEquals(10L, candidate.junctionA.id)
        assertEquals(20L, candidate.junctionB.id)
        assertEquals(0.25, candidate.segmentRatio, 0.01)
    }

    @Test
    fun tapNearPostClampsCenterToPhysicalClearance() {
        val junctionA = junction(1, 40.0, -100.0)
        val junctionB = junction(2, 40.0, -99.9999)
        val pasture = pasture(
            2,
            "Clamp",
            listOf(
                vertex(1, 2, 0, junctionA),
                vertex(2, 2, 1, junctionB),
                vertex(3, 2, 2, junction(3, 40.0001, -99.9999))
            )
        )
        val result = GateSnappingEngine.findCandidateSegment(
            tapPoint = LatLng(40.0, -100.0),
            pastures = listOf(pasture),
            tolerancePx = 10.0,
            project = ::linearProjection
        ) as GateSnapResult.Snapped
        val expected = ((GateEntity.WIDTH_14_FT / 2.0) + 0.5) / result.candidate.segmentLengthMeters
        assertEquals(expected, result.candidate.segmentRatio, 1e-6)
        assertEquals(
            GateEntity.WIDTH_14_FT,
            GateSnappingEngine.calculateSegmentLengthMeters(
                result.candidate.hingeCoordinate,
                result.candidate.latchCoordinate
            ),
            0.02
        )
    }

    @Test
    fun shortFenceReportsSpecificRejection() {
        val pasture = pasture(
            3,
            "Tiny",
            listOf(
                vertex(1, 3, 0, junction(1, 40.0, -100.0)),
                vertex(2, 3, 1, junction(2, 40.00001, -100.0)),
                vertex(3, 3, 2, junction(3, 40.00001, -99.99999))
            )
        )
        val result = GateSnappingEngine.findCandidateSegment(
            tapPoint = LatLng(40.000005, -100.0),
            pastures = listOf(pasture),
            tolerancePx = 20.0,
            project = ::linearProjection
        )
        assertEquals(GateSnapResult.FenceTooShort, result)
    }

    @Test
    fun missingAnchorMakesConnectivityUnresolvable() {
        val gate = GateEntity(name = "Broken", junctionAId = 1, junctionBId = 2, segmentRatio = 0.5)
        assertNull(
            GateSnappingEngine.resolveConnectivity(
                gate,
                emptyList(),
                mapOf(1L to LatLng(40.0, -100.0))
            )
        )
    }

    @Test
    fun relocationCandidateOnDifferentEdgeRecalculatesCanonicalEndpointsAndRatio() {
        val first = junction(100, 40.0, -100.0)
        val second = junction(200, 40.0, -99.98)
        val third = junction(300, 40.02, -99.98)
        val pasture = pasture(
            5,
            "Relocation",
            listOf(
                vertex(1, 5, 0, first),
                vertex(2, 5, 1, second),
                vertex(3, 5, 2, third)
            )
        )

        val result = GateSnappingEngine.findCandidateSegment(
            tapPoint = LatLng(40.015, -99.98),
            pastures = listOf(pasture),
            tolerancePx = 10.0,
            project = ::linearProjection
        )

        assertTrue(result is GateSnapResult.Snapped)
        val candidate = (result as GateSnapResult.Snapped).candidate
        assertEquals(200L, candidate.junctionA.id)
        assertEquals(300L, candidate.junctionB.id)
        assertEquals(0.75, candidate.segmentRatio, 0.01)
        assertEquals(5L, candidate.pastureAId)
    }

    @Test
    fun closeZoomWithOffscreenEndpointsStillUsesPhysicalFenceRatio() {
        val tap = LatLng(40.0, -99.985)
        val pasture = pasture(
            6,
            "Close zoom",
            listOf(
                vertex(1, 6, 0, junction(100, 40.0, -100.0)),
                vertex(2, 6, 1, junction(200, 40.0, -99.98)),
                vertex(3, 6, 2, junction(300, 40.01, -99.98)),
                vertex(4, 6, 3, junction(400, 40.01, -100.0))
            )
        )
        val clampedViewportProjection: (LatLng) -> ScreenCoordinate = { coordinate ->
            ScreenCoordinate(
                ((coordinate.longitude - tap.longitude) * 10_000_000.0).coerceIn(-1_000.0, 1_000.0),
                ((coordinate.latitude - tap.latitude) * 10_000_000.0).coerceIn(-1_000.0, 1_000.0)
            )
        }

        val result = GateSnappingEngine.findCandidateSegment(
            tapPoint = tap,
            pastures = listOf(pasture),
            tolerancePx = 20.0,
            project = clampedViewportProjection
        )

        assertTrue(result is GateSnapResult.Snapped)
        val candidate = (result as GateSnapResult.Snapped).candidate
        assertEquals(100L, candidate.junctionA.id)
        assertEquals(200L, candidate.junctionB.id)
        assertEquals(0.75, candidate.segmentRatio, 0.01)
    }

    private fun linearProjection(coordinate: LatLng) = ScreenCoordinate(
        coordinate.longitude * 100_000.0,
        coordinate.latitude * 100_000.0
    )

    private fun junction(id: Long, latitude: Double, longitude: Double) =
        FenceJunctionEntity(id = id, latitude = latitude, longitude = longitude)

    private fun vertex(id: Long, pastureId: Long, sequence: Int, junction: FenceJunctionEntity) =
        PastureVertexWithJunction(
            PastureVertexEntity(id = id, pastureId = pastureId, sequence = sequence, junctionId = junction.id),
            junction
        )

    private fun pasture(id: Long, name: String, vertices: List<PastureVertexWithJunction>) =
        PastureWithVertices(
            PastureEntity(id = id, name = name, createdAt = 1, updatedAt = 1),
            vertices
        )
}
