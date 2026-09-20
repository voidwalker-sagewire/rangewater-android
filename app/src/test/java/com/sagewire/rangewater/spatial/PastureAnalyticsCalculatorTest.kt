package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureEntity
import com.sagewire.rangewater.data.PastureVertexEntity
import com.sagewire.rangewater.data.PastureVertexWithJunction
import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.PastureWithVertices
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PastureAnalyticsCalculatorTest {
    private val standardPasture = pasture(
        id = 1L,
        west = -100.005,
        south = 39.995,
        east = -99.995,
        north = 40.005
    )

    @Test
    fun unassignedPastureIsEntirelyBeyondPlannedCoverage() {
        val metrics = PastureAnalyticsCalculator.computeMetrics(standardPasture, emptyList())

        assertTrue(metrics.totalAcreage > 100.0)
        assertEquals(0, metrics.assignedWaterCount)
        assertEquals(0.0, metrics.preferredAcreage, 0.0)
        assertEquals(0.0, metrics.transitionOnlyAcreage, 0.0)
        assertEquals(metrics.totalAcreage, metrics.beyondAcreage, 0.0)
        assertEquals(100.0, metrics.beyondPercentage, 0.0)
    }

    @Test
    fun oneCenteredSourceProducesExpectedTierAreasAndCompletePartition() {
        val metrics = PastureAnalyticsCalculator.computeMetrics(
            standardPasture,
            listOf(water(10L, 40.0, -100.0))
        )

        assertEquals(1, metrics.assignedWaterCount)
        assertEquals(46.15, metrics.preferredAcreage, 1.0)
        assertEquals(25.96, metrics.transitionOnlyAcreage, 1.0)
        assertTrue(metrics.beyondAcreage > 0.0)
        assertPartitionReconciles(metrics)
    }

    @Test
    fun overlappingSourcesAreUnionedInsteadOfDoubleCounted() {
        val metrics = PastureAnalyticsCalculator.computeMetrics(
            standardPasture,
            listOf(
                water(1L, 40.0, -100.0),
                water(2L, 40.0015, -100.0)
            )
        )

        assertEquals(2, metrics.assignedWaterCount)
        assertTrue(metrics.preferredAcreage > 46.15)
        assertTrue("Overlapping preferred land must not be counted twice", metrics.preferredAcreage < 92.30)
        assertPartitionReconciles(metrics)
    }

    @Test
    fun preferredCoverageWinsWhereAnotherSourcesTransitionOverlaps() {
        val metrics = PastureAnalyticsCalculator.computeMetrics(
            standardPasture,
            listOf(
                water(1L, 40.0, -100.0),
                water(2L, 40.0025, -100.0)
            )
        )

        assertTrue(metrics.preferredAcreage > 0.0)
        assertTrue(metrics.transitionOnlyAcreage > 0.0)
        assertPartitionReconciles(metrics)
    }

    @Test
    fun duplicateInputEntityIsCountedOnlyOnce() {
        val source = water(1L, 40.0, -100.0)
        val metrics = PastureAnalyticsCalculator.computeMetrics(
            standardPasture,
            listOf(source, source)
        )

        assertEquals(1, metrics.assignedWaterCount)
        assertEquals(46.15, metrics.preferredAcreage, 1.0)
        assertPartitionReconciles(metrics)
    }

    @Test
    fun invalidPastureReturnsZeroMetricsWithoutThrowing() {
        val invalid = PastureWithVertices(
            pasture = PastureEntity(
                id = 99L,
                name = "Line",
                createdAt = 1L,
                updatedAt = 1L
            ),
            vertices = listOf(
                vertex(1L, 99L, 0, 40.0, -100.0),
                vertex(2L, 99L, 1, 40.1, -100.1)
            )
        )

        val metrics = PastureAnalyticsCalculator.computeMetrics(invalid, emptyList())

        assertEquals(0.0, metrics.totalAcreage, 0.0)
        assertEquals(0.0, metrics.preferredAcreage, 0.0)
        assertEquals(0.0, metrics.beyondPercentage, 0.0)
    }

    private fun assertPartitionReconciles(metrics: PastureCoverageMetrics) {
        val acreageSum = metrics.preferredAcreage +
            metrics.transitionOnlyAcreage +
            metrics.beyondAcreage
        assertEquals(metrics.totalAcreage, acreageSum, metrics.totalAcreage * 0.005)
        assertEquals(
            100.0,
            metrics.preferredPercentage +
                metrics.transitionOnlyPercentage +
                metrics.beyondPercentage,
            0.5
        )
    }

    private fun water(id: Long, latitude: Double, longitude: Double) = WaterPointEntity(
        id = id,
        latitude = latitude,
        longitude = longitude,
        name = "Water $id",
        sourceType = WaterSourceType.TANK,
        createdAt = 10L,
        updatedAt = 10L
    )

    private fun pasture(
        id: Long,
        west: Double,
        south: Double,
        east: Double,
        north: Double
    ) = PastureWithVertices(
        pasture = PastureEntity(
            id = id,
            name = "Section $id",
            createdAt = 1L,
            updatedAt = 1L
        ),
        vertices = listOf(
            vertex(1L, id, 0, south, west),
            vertex(2L, id, 1, north, west),
            vertex(3L, id, 2, north, east),
            vertex(4L, id, 3, south, east)
        )
    )

    private fun vertex(
        id: Long,
        pastureId: Long,
        sequence: Int,
        latitude: Double,
        longitude: Double
    ) = PastureVertexWithJunction(
        vertex = PastureVertexEntity(id, pastureId, sequence, id),
        junction = FenceJunctionEntity(id, latitude, longitude)
    )
}
