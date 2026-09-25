package com.sagewire.rangewater.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayerVisibilityControllerTest {
    @Test
    fun fullModeShowsCoveragePastureFillAndWaterPins() {
        val state = MapLayerVisibilityController.computeVisibility(
            DisplayPreferences(),
            isMovingWater = false
        )

        assertTrue(state.preferredFillVisible)
        assertTrue(state.transitionFillVisible)
        assertTrue(state.ringsLineVisible)
        assertEquals(MapLayerVisibilityController.PASTURE_FILL_VISIBLE_OPACITY, state.pastureFillOpacity)
        assertTrue(state.pastureBoundariesVisible)
        assertTrue(state.waterPinsVisible)
        assertTrue(state.gatesVisible)
        assertTrue(state.herdBadgesVisible)
    }

    @Test
    fun linesOnlyHidesCoverageFillsButKeepsOutlinesAndPins() {
        val state = MapLayerVisibilityController.computeVisibility(
            DisplayPreferences(coverageMode = WaterCoverageMode.LINES_ONLY),
            isMovingWater = false
        )

        assertFalse(state.preferredFillVisible)
        assertFalse(state.transitionFillVisible)
        assertTrue(state.ringsLineVisible)
        assertTrue(state.waterPinsVisible)
    }

    @Test
    fun offHidesCoverageGeometryButKeepsPins() {
        val state = MapLayerVisibilityController.computeVisibility(
            DisplayPreferences(coverageMode = WaterCoverageMode.OFF),
            isMovingWater = false
        )

        assertFalse(state.preferredFillVisible)
        assertFalse(state.transitionFillVisible)
        assertFalse(state.ringsLineVisible)
        assertTrue(state.waterPinsVisible)
    }

    @Test
    fun pastureFillOffUsesZeroOpacitySoHitLayerRemainsActive() {
        val state = MapLayerVisibilityController.computeVisibility(
            DisplayPreferences(pastureFillEnabled = false),
            isMovingWater = false
        )

        assertEquals(MapLayerVisibilityController.PASTURE_FILL_HIDDEN_OPACITY, state.pastureFillOpacity)
        assertTrue(state.preferredFillVisible)
        assertTrue(state.waterPinsVisible)
    }

    @Test
    fun movingWaterForcesFullCoverageWithoutChangingOtherPreferences() {
        val preferences = DisplayPreferences(
            coverageMode = WaterCoverageMode.OFF,
            pastureFillEnabled = false
        )

        val moving = MapLayerVisibilityController.computeVisibility(preferences, isMovingWater = true)
        val restored = MapLayerVisibilityController.computeVisibility(preferences, isMovingWater = false)

        assertTrue(moving.preferredFillVisible)
        assertTrue(moving.transitionFillVisible)
        assertTrue(moving.ringsLineVisible)
        assertEquals(MapLayerVisibilityController.PASTURE_FILL_HIDDEN_OPACITY, moving.pastureFillOpacity)
        assertFalse(restored.preferredFillVisible)
        assertFalse(restored.transitionFillVisible)
        assertFalse(restored.ringsLineVisible)
    }

    @Test
    fun individualAssetLayersCanBeHidden() {
        val state = MapLayerVisibilityController.computeVisibility(
            DisplayPreferences(
                pastureBoundariesEnabled = false,
                waterPointsEnabled = false,
                gatesEnabled = false,
                herdBadgesEnabled = false
            ),
            isMovingWater = false
        )

        assertFalse(state.pastureBoundariesVisible)
        assertFalse(state.waterPinsVisible)
        assertFalse(state.gatesVisible)
        assertFalse(state.herdBadgesVisible)
    }

    @Test
    fun activeEditingTemporarilyRevealsRequiredLayers() {
        val preferences = DisplayPreferences(
            pastureBoundariesEnabled = false,
            waterPointsEnabled = false,
            gatesEnabled = false
        )

        val editing = MapLayerVisibilityController.computeVisibility(
            preferences,
            isMovingWater = true,
            isEditingWater = true,
            isEditingPasture = true,
            isEditingGate = true
        )

        assertTrue(editing.pastureBoundariesVisible)
        assertTrue(editing.waterPinsVisible)
        assertTrue(editing.gatesVisible)
    }
}
