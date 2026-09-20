package com.sagewire.rangewater.ui.map

import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.PastureCoordinate
import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.PastureWithVertices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class SharedJunctionPreviewTest {
    @Test
    fun oneEphemeralMoveUpdatesEveryConnectedPastureWithoutMutatingInput() {
        val shared = FenceJunctionEntity(1, 40.0, -100.0)
        val first = pasture(10, shared)
        val second = pasture(20, shared)

        val preview = applyJunctionMovePreview(
            listOf(first, second),
            mapOf(1L to PastureCoordinate(40.005, -100.005, junctionId = 1))
        )

        assertEquals(40.005, preview[0].vertices.first().latitude, 0.0)
        assertEquals(40.005, preview[1].vertices.first().latitude, 0.0)
        assertEquals(40.0, first.vertices.first().latitude, 0.0)
        assertNotSame(first, preview[0])
    }

    private fun pasture(id: Long, shared: FenceJunctionEntity) = PastureWithVertices(
        pasture = PastureEntity(id, "P$id", createdAt = 1, updatedAt = 1),
        vertices = listOf(
            PastureVertexWithJunction(PastureVertexEntity(id * 10, id, 0, shared.id), shared),
            PastureVertexWithJunction(
                PastureVertexEntity(id * 10 + 1, id, 1, id * 10 + 1),
                FenceJunctionEntity(id * 10 + 1, 40.01, -100.0)
            ),
            PastureVertexWithJunction(
                PastureVertexEntity(id * 10 + 2, id, 2, id * 10 + 2),
                FenceJunctionEntity(id * 10 + 2, 40.0, -99.99)
            )
        )
    )
}
