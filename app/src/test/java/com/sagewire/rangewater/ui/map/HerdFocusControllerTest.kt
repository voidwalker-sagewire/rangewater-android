package com.sagewire.rangewater.ui.map

import com.sagewire.rangewater.data.CountUnit
import com.sagewire.rangewater.data.GateEntity
import com.sagewire.rangewater.data.GateWithConnectivity
import com.sagewire.rangewater.data.HerdEntity
import com.sagewire.rangewater.data.HerdLocationKind
import com.sagewire.rangewater.data.StockClass
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.geometry.LatLng

class HerdFocusControllerTest {
    @Test
    fun focusDerivesCurrentPastureAndAssignedWaters() {
        val focus = HerdFocusController.derive(
            herd = herd(id = 7, pastureId = 20),
            assignments = mapOf(1L to listOf(20L), 2L to listOf(21L, 20L), 3L to listOf(30L))
        )!!

        assertEquals(7L, focus.herdId)
        assertEquals(20L, focus.pastureId)
        assertEquals(setOf(20L), focus.pastureIds)
        assertEquals(setOf(1L, 2L), focus.relatedWaterPointIds)
    }

    @Test
    fun assignedCircuitExpandsFocusToEveryMemberPastureAndItsWater() {
        val focus = HerdFocusController.derive(
            herd = herd(id = 7, pastureId = 20),
            assignments = mapOf(1L to listOf(20L), 2L to listOf(21L), 3L to listOf(30L)),
            circuitPastureIds = setOf(20L, 21L)
        )!!

        assertEquals(setOf(20L, 21L), focus.pastureIds)
        assertEquals(setOf(1L, 2L), focus.relatedWaterPointIds)
        val gates = listOf(gate(10, 20, null), gate(11, 21, 31), gate(12, 30, 31))
        assertEquals(listOf(10L, 11L), HerdFocusController.filterGates(gates, focus).map { it.gate.id })
    }

    @Test
    fun unmappedHerdCannotCreateFalsePastureFocus() {
        assertNull(
            HerdFocusController.derive(
                herd = herd(id = 7, pastureId = null),
                assignments = emptyMap()
            )
        )
    }

    @Test
    fun focusFiltersWaterGatesAndHerdBadgesWithoutMutatingRecords() {
        val focus = HerdFocusController.derive(
            herd = herd(id = 7, pastureId = 20),
            assignments = mapOf(1L to listOf(20L), 2L to listOf(30L))
        )!!
        val waters = listOf(water(1), water(2))
        val gates = listOf(gate(10, 20, null), gate(11, 30, 31), gate(12, 40, 20))
        val herds = listOf(herd(7, 20), herd(8, 30))

        assertEquals(listOf(1L), HerdFocusController.filterWaterPoints(waters, focus).map { it.id })
        assertEquals(listOf(10L, 12L), HerdFocusController.filterGates(gates, focus).map { it.gate.id })
        assertEquals(listOf(7L), HerdFocusController.filterHerds(herds, focus).map { it.id })
        assertEquals(2, waters.size)
        assertEquals(3, gates.size)
        assertEquals(2, herds.size)
    }

    private fun herd(id: Long, pastureId: Long?) = HerdEntity(
        id = id,
        name = "Herd $id",
        quantity = 10,
        countUnit = CountUnit.HEAD,
        stockClass = StockClass.COW_CALF_PAIRS,
        markerColorHex = "#FF9100",
        locationKind = if (pastureId == null) HerdLocationKind.OFF_RANCH else HerdLocationKind.PASTURE,
        currentPastureId = pastureId
    )

    private fun water(id: Long) = WaterPointEntity(
        id = id,
        latitude = 40.0,
        longitude = -80.0,
        name = "Water $id",
        sourceType = WaterSourceType.TROUGH,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun gate(id: Long, pastureAId: Long?, pastureBId: Long?) = GateWithConnectivity(
        gate = GateEntity(id = id, name = "Gate $id", junctionAId = id, junctionBId = id + 100, segmentRatio = 0.5),
        derivedCoordinate = LatLng(40.0, -80.0),
        hingeCoordinate = LatLng(40.0, -80.0),
        latchCoordinate = LatLng(40.0, -79.999),
        pastureAId = pastureAId,
        pastureAName = pastureAId?.let { "Pasture $it" },
        pastureBId = pastureBId,
        pastureBName = pastureBId?.let { "Pasture $it" },
        isShared = pastureBId != null
    )
}
