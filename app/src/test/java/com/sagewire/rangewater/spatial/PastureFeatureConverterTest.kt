package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.PastureWithVertices
import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.GateEntity
import com.sagewire.rangewater.data.GateWithConnectivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Polygon
import org.maplibre.geojson.LineString
import org.maplibre.android.geometry.LatLng

class PastureFeatureConverterTest {
    @Test
    fun verticesAreSortedClosedAndSelectionIsPreserved() {
        val pasture = PastureWithVertices(
            pasture = PastureEntity(id = 7, name = "South Field", createdAt = 1, updatedAt = 1),
            vertices = listOf(
                vertex(3, 2, 40.01, -81.0),
                vertex(1, 0, 40.0, -81.0),
                vertex(2, 1, 40.0, -80.99)
            )
        )

        val feature = PastureFeatureConverter.toPastureFeatures(listOf(pasture), 7).features()!!.single()
        val ring = (feature.geometry() as Polygon).coordinates().single()

        assertEquals(4, ring.size)
        assertEquals(ring.first(), ring.last())
        assertEquals(7L, feature.getNumberProperty("id").toLong())
        assertTrue(feature.getBooleanProperty("selected"))
    }

    @Test
    fun gateOpeningIsNotCrossedByBoundaryLine() {
        val pasture = PastureWithVertices(
            pasture = PastureEntity(id = 7, name = "South Field", createdAt = 1, updatedAt = 1),
            vertices = listOf(
                vertex(1, 0, 40.0, -100.0),
                vertex(2, 1, 40.0, -99.9),
                vertex(3, 2, 40.01, -99.9)
            )
        )
        val gate = GateWithConnectivity(
            gate = GateEntity(id = 1, name = "South Gate", junctionAId = 1, junctionBId = 2, segmentRatio = 0.5),
            derivedCoordinate = LatLng(40.0, -99.95),
            hingeCoordinate = LatLng(40.0, -99.952),
            latchCoordinate = LatLng(40.0, -99.948),
            pastureAId = 7,
            pastureAName = "South Field",
            pastureBId = null,
            pastureBName = null,
            isShared = false
        )

        val lines = PastureFeatureConverter.toPastureBoundaryLines(listOf(pasture), listOf(gate), null)
            .features().orEmpty()
        assertEquals(4, lines.size)
        val southFencePieces = lines.map { it.geometry() as LineString }.filter { line ->
            line.coordinates().all { point -> kotlin.math.abs(point.latitude() - 40.0) < 1e-9 }
        }
        assertEquals(2, southFencePieces.size)
        assertTrue(southFencePieces.none { line ->
            val longitudes = line.coordinates().map { it.longitude() }
            longitudes.minOrNull()!! < -99.95 && longitudes.maxOrNull()!! > -99.95
        })
    }

    private fun vertex(id: Long, sequence: Int, latitude: Double, longitude: Double) =
        PastureVertexWithJunction(
            vertex = PastureVertexEntity(id, 7, sequence, id),
            junction = FenceJunctionEntity(id, latitude, longitude)
        )
}
