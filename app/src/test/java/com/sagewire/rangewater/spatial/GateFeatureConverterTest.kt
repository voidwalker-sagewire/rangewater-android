package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.GateEntity
import com.sagewire.rangewater.data.GateWithConnectivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class GateFeatureConverterTest {
    @Test
    fun openGateEmitsBadgeLeafArcAndTouchTarget() {
        val features = GateFeatureConverter.toGateFeatures(
            listOf(gateItem(GateEntity.STATUS_OPEN)),
            selectedGateId = 5
        ).features().orEmpty()

        assertEquals(4, features.size)
        assertTrue(features.any { it.geometry() is Point && it.hasProperty("isOverview") })
        assertTrue(features.any { it.geometry() is LineString && it.hasProperty("isArc") })
        assertTrue(features.any { it.geometry() is Point && it.hasProperty("isTouchTarget") })
    }

    @Test
    fun closedGateOmitsSwingArc() {
        val features = GateFeatureConverter.toGateFeatures(
            listOf(gateItem(GateEntity.STATUS_CLOSED)),
            selectedGateId = null
        ).features().orEmpty()
        assertEquals(3, features.size)
        assertTrue(features.none { it.hasProperty("isArc") })
    }

    private fun gateItem(status: String) = GateWithConnectivity(
        gate = GateEntity(
            id = 5,
            name = "East Gate",
            junctionAId = 1,
            junctionBId = 2,
            segmentRatio = 0.5,
            status = status
        ),
        derivedCoordinate = LatLng(40.0, -100.0),
        hingeCoordinate = LatLng(40.0, -100.000025),
        latchCoordinate = LatLng(40.0, -99.999975),
        pastureAId = 10,
        pastureAName = "P1",
        pastureBId = 20,
        pastureBName = "P2",
        isShared = true
    )
}
