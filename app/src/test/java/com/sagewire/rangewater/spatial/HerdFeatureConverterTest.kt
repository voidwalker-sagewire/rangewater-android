package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.*
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.geojson.Point

class HerdFeatureConverterTest {
    private val pasture = PastureWithVertices(
        PastureEntity(10, "South Field", createdAt = 100, updatedAt = 100),
        listOf(
            vertex(1, 0, 40.0, -100.0),
            vertex(2, 1, 40.0, -99.98),
            vertex(3, 2, 40.01, -99.98),
            vertex(4, 3, 40.01, -100.0)
        )
    )

    @Test
    fun badgesShareInteriorPointAndCarryStackIndices() {
        val herds = listOf(herd(1, "Group A", 40, CountUnit.PAIRS), herd(2, "Group B", 25, CountUnit.HEAD))
        val features = HerdFeatureConverter.toHerdBadges(herds, listOf(pasture), 1).features()!!
        assertEquals(2, features.size)
        val first = features[0].geometry() as Point
        val second = features[1].geometry() as Point
        assertEquals(first.longitude(), second.longitude(), 0.000001)
        assertEquals(first.latitude(), second.latitude(), 0.000001)
        assertEquals(0, features[0].getNumberProperty("stackIndex").toInt())
        assertEquals(1, features[1].getNumberProperty("stackIndex").toInt())
        assertTrue(features[0].getBooleanProperty("selected"))
        assertEquals("Group A (40 pairs)", features[0].getStringProperty("displayLabel"))
        assertEquals("marker-herd-cow", features[0].getStringProperty("markerIcon"))
    }

    @Test
    fun moreThanThreeHerdsProducesTwoBadgesAndOverflow() {
        val features = HerdFeatureConverter.toHerdBadges(
            (1..5).map { herd(it.toLong(), "Group $it", it * 10, CountUnit.HEAD) }, listOf(pasture), null
        ).features()!!
        assertEquals(3, features.size)
        assertTrue(features[2].getBooleanProperty("isOverflow"))
        assertEquals(3, features[2].getNumberProperty("overflowCount").toInt())
        assertEquals(10L, features[2].getNumberProperty("pastureId").toLong())
        assertEquals("marker-herd-overflow-3", features[2].getStringProperty("markerIcon"))
    }

    private fun herd(id: Long, name: String, quantity: Int, unit: CountUnit) = HerdEntity(
        id = id, name = name, quantity = quantity, countUnit = unit, stockClass = StockClass.MIXED,
        markerColorHex = "#FF9100", locationKind = HerdLocationKind.PASTURE, currentPastureId = 10
    )

    private fun vertex(id: Long, sequence: Int, latitude: Double, longitude: Double) = PastureVertexWithJunction(
        PastureVertexEntity(id, 10, sequence, id), FenceJunctionEntity(id, latitude, longitude)
    )
}
