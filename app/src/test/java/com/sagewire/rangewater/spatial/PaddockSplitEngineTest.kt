package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.PastureCoordinate
import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.PastureWithVertices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaddockSplitEngineTest {
    @Test
    fun straightBoundaryToBoundaryLineCreatesTwoReconciledRegions() {
        val pasture = squarePasture()
        val start = PaddockSplitEngine.resolveAnchor(pasture, 1, 2, 0.5)
        val end = PaddockSplitEngine.resolveAnchor(pasture, 3, 4, 0.5)

        val result = PaddockSplitEngine.split(pasture, start, end)

        assertTrue(result.sideAAcres > 0.0)
        assertTrue(result.sideBAcres > 0.0)
        assertEquals(result.parentAcres, result.sideAAcres + result.sideBAcres, result.parentAcres * 0.005)
        assertEquals(result.sideAAcres, result.sideBAcres, result.parentAcres * 0.01)
    }

    @Test
    fun nearestBoundaryAnchorReturnsCanonicalSegmentAndRatio() {
        val pasture = squarePasture()
        val anchor = PaddockSplitEngine.nearestBoundaryAnchor(
            PastureCoordinate(40.00001, -79.995),
            pasture,
            toleranceMeters = 5.0
        )

        assertNotNull(anchor)
        assertEquals(1L, anchor?.junctionAId)
        assertEquals(2L, anchor?.junctionBId)
        assertEquals(0.5, anchor?.segmentRatio ?: Double.NaN, 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundaryFollowingLineIsRejected() {
        val pasture = squarePasture()
        val start = PaddockSplitEngine.resolveAnchor(pasture, 1, 2, 0.2)
        val end = PaddockSplitEngine.resolveAnchor(pasture, 1, 2, 0.8)
        PaddockSplitEngine.split(pasture, start, end)
    }

    @Test(expected = IllegalArgumentException::class)
    fun detachedAnchorIsRejected() {
        PaddockSplitEngine.resolveAnchor(squarePasture(), 1, 3, 0.5)
    }

    private fun squarePasture(): PastureWithVertices {
        val junctions = listOf(
            FenceJunctionEntity(1, 40.0, -80.0),
            FenceJunctionEntity(2, 40.0, -79.99),
            FenceJunctionEntity(3, 40.01, -79.99),
            FenceJunctionEntity(4, 40.01, -80.0)
        )
        return PastureWithVertices(
            pasture = PastureEntity(10, "Square", "", 1, 1),
            vertices = junctions.mapIndexed { index, junction ->
                PastureVertexWithJunction(
                    PastureVertexEntity(index.toLong() + 20, 10, index, junction.id),
                    junction
                )
            }
        )
    }
}
