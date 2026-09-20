package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.PastureWithVertices
import com.sagewire.rangewater.data.FenceJunctionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.Polygon

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

    private fun vertex(id: Long, sequence: Int, latitude: Double, longitude: Double) =
        PastureVertexWithJunction(
            vertex = PastureVertexEntity(id, 7, sequence, id),
            junction = FenceJunctionEntity(id, latitude, longitude)
        )
}
