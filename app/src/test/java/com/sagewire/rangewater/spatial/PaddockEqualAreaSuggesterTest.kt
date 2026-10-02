package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.PastureWithVertices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaddockEqualAreaSuggesterTest {
    @Test
    fun squareSuggestionIsDeterministicAndNearEqual() {
        val pasture = squarePasture()

        val first = PaddockEqualAreaSuggester.suggest(pasture)
        val second = PaddockEqualAreaSuggester.suggest(pasture)

        assertEquals(first.start, second.start)
        assertEquals(first.end, second.end)
        assertTrue(kotlin.math.abs(first.sideAAcres - first.sideBAcres) / first.parentAcres < 0.01)
    }

    private fun squarePasture(): PastureWithVertices {
        val junctions = listOf(
            FenceJunctionEntity(1, 40.0, -80.0),
            FenceJunctionEntity(2, 40.0, -79.99),
            FenceJunctionEntity(3, 40.01, -79.99),
            FenceJunctionEntity(4, 40.01, -80.0)
        )
        return PastureWithVertices(
            PastureEntity(10, "Square", "", 1, 1),
            junctions.mapIndexed { index, junction ->
                PastureVertexWithJunction(
                    PastureVertexEntity(index.toLong() + 20, 10, index, junction.id),
                    junction
                )
            }
        )
    }
}
