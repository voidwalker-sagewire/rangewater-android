package com.sagewire.rangewater.ui.map

/**
 * Wide ranch views favor polygon interiors over large invisible gate targets. A visible
 * overview marker remains directly tappable; the expanded gate target returns at detail zoom.
 */
internal object MapTapSelectionPolicy {
    const val GATE_DETAIL_ZOOM = 16.0

    fun useExpandedGateTarget(zoom: Double): Boolean = zoom >= GATE_DETAIL_ZOOM
}
