package com.sagewire.rangewater.spatial

import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PastureAnalyticsIntegrationTest {
    @Test
    fun jtsAndTurfPartitionExecutesOnAndroidRuntime() {
        val pasture = PastureWithVertices(
            pasture = PastureEntity(
                id = 5L,
                name = "South Field",
                createdAt = 1L,
                updatedAt = 1L
            ),
            vertices = listOf(
                vertex(1L, 0, 41.00, -95.00),
                vertex(2L, 1, 41.01, -95.00),
                vertex(3L, 2, 41.01, -94.99),
                vertex(4L, 3, 41.00, -94.99)
            )
        )
        val water = WaterPointEntity(
            id = 20L,
            latitude = 41.005,
            longitude = -94.995,
            name = "Tub 20",
            sourceType = WaterSourceType.TANK,
            createdAt = 2L,
            updatedAt = 2L
        )

        val metrics = PastureAnalyticsCalculator.computeMetrics(pasture, listOf(water))

        assertEquals(5L, metrics.pastureId)
        assertEquals(1, metrics.assignedWaterCount)
        assertTrue(metrics.totalAcreage > 0.0)
        assertTrue(metrics.preferredAcreage > 0.0)
        assertTrue(metrics.transitionOnlyAcreage > 0.0)
        assertTrue(metrics.beyondAcreage > 0.0)
        assertEquals(
            100.0,
            metrics.preferredPercentage +
                metrics.transitionOnlyPercentage +
                metrics.beyondPercentage,
            0.5
        )
    }

    private fun vertex(
        id: Long,
        sequence: Int,
        latitude: Double,
        longitude: Double
    ) = PastureVertexWithJunction(
        vertex = PastureVertexEntity(id, 5L, sequence, id),
        junction = FenceJunctionEntity(id, latitude, longitude)
    )
}
