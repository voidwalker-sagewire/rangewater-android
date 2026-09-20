package com.sagewire.rangewater.data

import org.maplibre.android.geometry.LatLng

data class GateWithConnectivity(
    val gate: GateEntity,
    val derivedCoordinate: LatLng,
    val hingeCoordinate: LatLng,
    val latchCoordinate: LatLng,
    val pastureAId: Long?,
    val pastureAName: String?,
    val pastureBId: Long?,
    val pastureBName: String?,
    val isShared: Boolean
) {
    val connectivityDescription: String
        get() = when {
            isShared && pastureAName != null && pastureBName != null ->
                "$pastureAName ⟷ $pastureBName"
            pastureAName != null -> "$pastureAName ⟷ Outside"
            else -> "Unassigned / Outside"
        }
}
