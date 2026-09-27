package com.sagewire.rangewater.ui.map

import com.sagewire.rangewater.data.GateWithConnectivity
import com.sagewire.rangewater.data.HerdEntity
import com.sagewire.rangewater.data.HerdLocationKind
import com.sagewire.rangewater.data.WaterPointEntity

data class HerdFocusState(
    val herdId: Long,
    val pastureId: Long,
    val pastureIds: Set<Long>,
    val colorHex: String,
    val relatedWaterPointIds: Set<Long>
)

/*
 * 🪨 BLOCK 1 — HERD FOCUS DERIVATION
 * Purpose: Derives a temporary map view from accepted herd location and water-access
 * records. It never creates a permanent herd-to-pasture circuit.
 */
object HerdFocusController {
    fun derive(
        herd: HerdEntity?,
        assignments: Map<Long, List<Long>>,
        circuitPastureIds: Set<Long> = emptySet()
    ): HerdFocusState? {
        if (herd == null || herd.locationKind != HerdLocationKind.PASTURE) return null
        val pastureId = herd.currentPastureId ?: return null
        val focusedPastures = circuitPastureIds + pastureId
        return HerdFocusState(
            herdId = herd.id,
            pastureId = pastureId,
            pastureIds = focusedPastures,
            colorHex = herd.markerColorHex,
            relatedWaterPointIds = assignments
                .filterValues { assignedPastures -> assignedPastures.any { it in focusedPastures } }
                .keys
        )
    }

    fun filterWaterPoints(
        points: List<WaterPointEntity>,
        focus: HerdFocusState?
    ): List<WaterPointEntity> = if (focus == null) {
        points
    } else {
        points.filter { it.id in focus.relatedWaterPointIds }
    }

    fun filterGates(
        gates: List<GateWithConnectivity>,
        focus: HerdFocusState?
    ): List<GateWithConnectivity> = if (focus == null) {
        gates
    } else {
        gates.filter { it.pastureAId in focus.pastureIds || it.pastureBId in focus.pastureIds }
    }

    fun filterHerds(
        herds: List<HerdEntity>,
        focus: HerdFocusState?
    ): List<HerdEntity> = if (focus == null) {
        herds
    } else {
        herds.filter { it.id == focus.herdId }
    }
}
