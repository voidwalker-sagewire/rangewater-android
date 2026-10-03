package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.PaddockDividerEntity
import com.sagewire.rangewater.data.PaddockDividerNodeRefEntity
import com.sagewire.rangewater.data.PaddockPlanEntity
import com.sagewire.rangewater.data.PaddockPlanNodeEntity
import com.sagewire.rangewater.data.PaddockPlanNodeKind
import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.PastureWithVertices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolystrandPlanEngineTest {
    private val plan = PaddockPlanEntity(10, 1, "Test Plan", 1, 1)

    @Test
    fun parallelDividersProduceThreeReconciledRegions() {
        val nodes = listOf(
            boundary(1, 1, 2, 0.33), boundary(2, 3, 4, 0.67),
            boundary(3, 1, 2, 0.67), boundary(4, 3, 4, 0.33)
        )
        val dividers = listOf(divider(1, 0), divider(2, 1))
        val result = resolve(nodes, dividers, refs(1, 1, 2) + refs(2, 3, 4))

        assertEquals(3, result.regions.size)
        assertEquals(result.parentAcreage, result.regions.sumOf { it.acreage }, result.parentAcreage * 0.005)
    }

    @Test
    fun waypointDividerRemainsInsideAndProducesTwoRegions() {
        val nodes = listOf(
            boundary(1, 1, 2, 0.25),
            interior(2, PaddockPlanNodeKind.INTERIOR_WAYPOINT, 40.005, -79.994),
            boundary(3, 3, 4, 0.75)
        )
        val result = resolve(listOf(nodes[0], nodes[1], nodes[2]), listOf(divider(1, 0)), refs(1, 1, 2, 3))
        assertEquals(2, result.regions.size)
    }

    @Test
    fun irregularGpsBoundaryWithInterpolatedAnchorsProducesReconciledRegions() {
        val fieldPlan = plan.copy(pastureId = 7)
        val nodes = listOf(
            PaddockPlanNodeEntity(
                id = 1,
                planId = fieldPlan.id,
                nodeKind = PaddockPlanNodeKind.BOUNDARY_ANCHOR,
                boundaryJunctionAId = 101,
                boundaryJunctionBId = 205,
                boundarySegmentRatio = 0.318271639,
                createdAt = 1,
                updatedAt = 1
            ),
            PaddockPlanNodeEntity(
                id = 2,
                planId = fieldPlan.id,
                nodeKind = PaddockPlanNodeKind.BOUNDARY_ANCHOR,
                boundaryJunctionAId = 409,
                boundaryJunctionBId = 511,
                boundarySegmentRatio = 0.672811473,
                createdAt = 1,
                updatedAt = 1
            )
        )
        val result = PolystrandPlanEngine.resolve(
            fieldPlan,
            nodes,
            listOf(PaddockDividerEntity(1, fieldPlan.id, "Field divider", 0, 1, 1)),
            refs(1, 1, 2),
            emptyList(),
            irregularGpsPasture()
        )

        assertEquals(2, result.regions.size)
        assertEquals(result.parentAcreage, result.regions.sumOf { it.acreage }, result.parentAcreage * 0.005)
    }

    @Test
    fun deliberateSharedJunctionProducesFourRegions() {
        val nodes = listOf(
            boundary(1, 1, 4, 0.5), boundary(2, 2, 3, 0.5),
            boundary(3, 1, 2, 0.5), boundary(4, 3, 4, 0.5),
            interior(5, PaddockPlanNodeKind.INTERIOR_JUNCTION, 40.005, -79.995)
        )
        val result = resolve(
            nodes,
            listOf(divider(1, 0), divider(2, 1)),
            refs(1, 1, 5, 2) + refs(2, 3, 5, 4)
        )
        assertEquals(4, result.regions.size)
    }

    @Test
    fun visualCrossingWithoutSharedDurableNodeIsRejected() {
        val nodes = listOf(
            boundary(1, 1, 4, 0.5), boundary(2, 2, 3, 0.5),
            boundary(3, 1, 2, 0.5), boundary(4, 3, 4, 0.5),
            interior(5, PaddockPlanNodeKind.INTERIOR_WAYPOINT, 40.005, -79.995),
            interior(6, PaddockPlanNodeKind.INTERIOR_WAYPOINT, 40.005, -79.995)
        )
        val failure = runCatching {
            resolve(
                nodes,
                listOf(divider(1, 0), divider(2, 1)),
                refs(1, 1, 5, 2) + refs(2, 3, 6, 4)
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("shared node"))
    }

    private fun resolve(
        nodes: List<PaddockPlanNodeEntity>,
        dividers: List<PaddockDividerEntity>,
        refs: List<PaddockDividerNodeRefEntity>
    ) = PolystrandPlanEngine.resolve(plan, nodes, dividers, refs, emptyList(), squarePasture())

    private fun boundary(id: Long, a: Long, b: Long, ratio: Double) = PaddockPlanNodeEntity(
        id, plan.id, PaddockPlanNodeKind.BOUNDARY_ANCHOR, a, b, ratio, createdAt = 1, updatedAt = 1
    )

    private fun interior(id: Long, kind: PaddockPlanNodeKind, latitude: Double, longitude: Double) =
        PaddockPlanNodeEntity(
            id = id,
            planId = plan.id,
            nodeKind = kind,
            latitude = latitude,
            longitude = longitude,
            createdAt = 1,
            updatedAt = 1
        )

    private fun divider(id: Long, sequence: Int) =
        PaddockDividerEntity(id, plan.id, "Divider ${sequence + 1}", sequence, 1, 1)

    private fun refs(dividerId: Long, vararg nodeIds: Long) = nodeIds.mapIndexed { index, nodeId ->
        PaddockDividerNodeRefEntity(dividerId, index, nodeId)
    }

    private fun squarePasture(): PastureWithVertices {
        val junctions = listOf(
            FenceJunctionEntity(1, 40.0, -80.0),
            FenceJunctionEntity(2, 40.0, -79.99),
            FenceJunctionEntity(3, 40.01, -79.99),
            FenceJunctionEntity(4, 40.01, -80.0)
        )
        return PastureWithVertices(
            PastureEntity(1, "Square", "", 1, 1),
            junctions.mapIndexed { index, junction ->
                PastureVertexWithJunction(
                    PastureVertexEntity(index.toLong() + 20, 1, index, junction.id),
                    junction
                )
            }
        )
    }

    private fun irregularGpsPasture(): PastureWithVertices {
        val junctions = listOf(
            FenceJunctionEntity(101, 40.000110927341, -80.000370618227),
            FenceJunctionEntity(205, 39.999930381552, -79.998810427613),
            FenceJunctionEntity(307, 40.000740736841, -79.997920186432),
            FenceJunctionEntity(409, 40.002190583764, -79.998330914725),
            FenceJunctionEntity(511, 40.002520196438, -79.999760327519),
            FenceJunctionEntity(613, 40.001410842367, -80.000620744103)
        )
        return PastureWithVertices(
            PastureEntity(7, "Irregular GPS field", "", 1, 1),
            junctions.mapIndexed { index, junction ->
                PastureVertexWithJunction(
                    PastureVertexEntity(index.toLong() + 70, 7, index, junction.id),
                    junction
                )
            }
        )
    }
}
