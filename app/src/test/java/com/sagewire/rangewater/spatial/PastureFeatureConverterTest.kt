package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureWithVertices
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
                PastureVertexEntity(id = 3, pastureId = 7, sequence = 2, latitude = 40.01, longitude = -81.0),
                PastureVertexEntity(id = 1, pastureId = 7, sequence = 0, latitude = 40.0, longitude = -81.0),
                PastureVertexEntity(id = 2, pastureId = 7, sequence = 1, latitude = 40.0, longitude = -80.99)
            )
        )

        val feature = PastureFeatureConverter.toPastureFeatures(listOf(pasture), 7).features()!!.single()
        val ring = (feature.geometry() as Polygon).coordinates().single()

        assertEquals(4, ring.size)
        assertEquals(ring.first(), ring.last())
        assertEquals(7L, feature.getNumberProperty("id").toLong())
        assertTrue(feature.getBooleanProperty("selected"))
    }
}
