package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.FenceJunctionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class CornerSnappingEngineTest {
    @Test
    fun nearestScreenJunctionHonorsTolerance() {
        val near = FenceJunctionEntity(1, 40.0, -100.0)
        val far = FenceJunctionEntity(2, 41.0, -101.0)
        val screens = listOf(near to (100f to 100f), far to (500f to 500f))

        assertEquals(
            near,
            CornerSnappingEngine.findNearestJunctionScreenSpace(110f, 105f, screens, 32f)
        )
        assertNull(
            CornerSnappingEngine.findNearestJunctionScreenSpace(150f, 100f, screens, 32f)
        )
    }

    @Test
    fun nearbyJunctionsUseMeterThresholdAndDistanceOrder() {
        val first = FenceJunctionEntity(2, 40.0001, -100.0)
        val second = FenceJunctionEntity(3, 40.0002, -100.0)
        val far = FenceJunctionEntity(4, 40.01, -100.0)
        val result = CornerSnappingEngine.findNearbyJunctions(
            LatLng(40.0, -100.0),
            listOf(second, far, first),
            excludedJunctionId = 1,
            maxDistanceMeters = 50.0
        )
        assertEquals(listOf(2L, 3L), result.map { it.first.id })
    }
}
