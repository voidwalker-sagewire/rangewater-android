package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.*
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.LineString

class MovementRoutingTest {
    private val p1 = pasture(10, "P1", listOf(Triple(1, 40.0, -100.0), Triple(2, 40.0, -99.99), Triple(3, 40.01, -99.99)))
    private val p2 = pasture(20, "P2", listOf(Triple(2, 40.0, -99.99), Triple(1, 40.0, -100.0), Triple(4, 39.99, -99.99)))

    @Test
    fun pastureToPastureUsesInteriorGateInterior() {
        val features = MovementFeatureConverter.toMovementRouteLines(
            movement(100, 10, HerdLocationKind.PASTURE, 20, false, 50), listOf(p1, p2), listOf(gate(50, true, 10, 20))
        ).features()!!
        assertEquals(1, features.size)
        assertEquals(3, (features[0].geometry() as LineString).coordinates().size)
        assertTrue(features[0].getBooleanProperty("isPlanned"))
    }

    @Test
    fun pastureToExteriorStopsAtGate() {
        val features = MovementFeatureConverter.toMovementRouteLines(
            movement(101, 10, HerdLocationKind.OFF_RANCH, null, false, 51), listOf(p1), listOf(gate(51, false, 10, null))
        ).features()!!
        assertEquals(2, (features.single().geometry() as LineString).coordinates().size)
    }

    @Test
    fun unmappedRouteProducesHighlightsButNoLine() {
        val movement = movement(102, 10, HerdLocationKind.PASTURE, 20, true, null)
        assertTrue(MovementFeatureConverter.toMovementRouteLines(movement, listOf(p1, p2), emptyList()).features().isNullOrEmpty())
        assertEquals(2, MovementFeatureConverter.toMovementHighlights(movement, listOf(p1, p2)).features()!!.size)
    }

    private fun movement(id: Long, origin: Long, destinationKind: HerdLocationKind, destination: Long?, unmapped: Boolean, gateId: Long?) =
        CattleMovementEntity(
            id = id, herdId = 1, originLocationKind = HerdLocationKind.PASTURE, originPastureId = origin,
            originNameSnapshot = "P1", destinationLocationKind = destinationKind, destinationPastureId = destination,
            destinationNameSnapshot = destination?.let { "P2" } ?: destinationKind.name,
            quantity = 30, countUnit = CountUnit.HEAD, status = MovementStatus.PLANNED,
            gateId = gateId, unmappedRoute = unmapped, notes = if (unmapped) "County road" else ""
        )

    private fun gate(id: Long, shared: Boolean, pastureA: Long, pastureB: Long?) = GateWithConnectivity(
        GateEntity(id = id, name = "Gate", junctionAId = 1, junctionBId = 2, segmentRatio = .5),
        LatLng(40.0, -99.995), LatLng(40.0, -99.996), LatLng(40.0, -99.994),
        pastureA, "P1", pastureB, pastureB?.let { "P2" }, shared
    )

    private fun pasture(id: Long, name: String, points: List<Triple<Int, Double, Double>>) = PastureWithVertices(
        PastureEntity(id, name, createdAt = 100, updatedAt = 100),
        points.mapIndexed { index, point ->
            PastureVertexWithJunction(
                PastureVertexEntity(point.first.toLong(), id, index, point.first.toLong()),
                FenceJunctionEntity(point.first.toLong(), point.second, point.third)
            )
        }
    )
}
