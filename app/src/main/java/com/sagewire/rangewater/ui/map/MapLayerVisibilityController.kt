package com.sagewire.rangewater.ui.map

data class LayerVisibilityState(
    val preferredFillVisible: Boolean,
    val transitionFillVisible: Boolean,
    val ringsLineVisible: Boolean,
    val pastureFillOpacity: Float,
    val pastureBoundariesVisible: Boolean,
    val waterPinsVisible: Boolean,
    val gatesVisible: Boolean,
    val herdBadgesVisible: Boolean
)

object MapLayerVisibilityController {
    const val PASTURE_FILL_VISIBLE_OPACITY = 0.08f
    const val PASTURE_FILL_HIDDEN_OPACITY = 0f

    fun computeVisibility(
        preferences: DisplayPreferences,
        isMovingWater: Boolean,
        isEditingWater: Boolean = false,
        isEditingPasture: Boolean = false,
        isEditingGate: Boolean = false
    ): LayerVisibilityState {
        val effectiveCoverageMode = if (isMovingWater) {
            WaterCoverageMode.FULL
        } else {
            preferences.coverageMode
        }
        val coverageVisibility = when (effectiveCoverageMode) {
            WaterCoverageMode.FULL -> Triple(true, true, true)
            WaterCoverageMode.LINES_ONLY -> Triple(false, false, true)
            WaterCoverageMode.OFF -> Triple(false, false, false)
        }

        return LayerVisibilityState(
            preferredFillVisible = coverageVisibility.first,
            transitionFillVisible = coverageVisibility.second,
            ringsLineVisible = coverageVisibility.third,
            pastureFillOpacity = if (preferences.pastureFillEnabled) {
                PASTURE_FILL_VISIBLE_OPACITY
            } else {
                PASTURE_FILL_HIDDEN_OPACITY
            },
            pastureBoundariesVisible = preferences.pastureBoundariesEnabled || isEditingPasture,
            waterPinsVisible = preferences.waterPointsEnabled || isMovingWater || isEditingWater,
            gatesVisible = preferences.gatesEnabled || isEditingGate,
            herdBadgesVisible = preferences.herdBadgesEnabled
        )
    }
}
