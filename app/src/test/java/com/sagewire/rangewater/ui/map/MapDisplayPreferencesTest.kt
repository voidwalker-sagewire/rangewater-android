package com.sagewire.rangewater.ui.map

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDisplayPreferencesTest {
    @Test
    fun defaultsEnableFullCoverageAndPastureFill() {
        val preferences = DisplayPreferences()

        assertEquals(WaterCoverageMode.FULL, preferences.coverageMode)
        assertEquals(SpatialCoverageScope.PHYSICAL_RADIUS, preferences.coverageScope)
        assertTrue(preferences.pastureFillEnabled)
        assertTrue(preferences.pastureBoundariesEnabled)
        assertTrue(preferences.waterPointsEnabled)
        assertTrue(preferences.gatesEnabled)
        assertTrue(preferences.herdBadgesEnabled)
    }

    @Test
    fun storedCoverageModeParsingFallsBackSafely() {
        assertEquals(WaterCoverageMode.FULL, WaterCoverageMode.fromStoredValue(null))
        assertEquals(WaterCoverageMode.FULL, WaterCoverageMode.fromStoredValue("UNKNOWN"))
        assertEquals(WaterCoverageMode.LINES_ONLY, WaterCoverageMode.fromStoredValue("LINES_ONLY"))
        assertEquals(WaterCoverageMode.OFF, WaterCoverageMode.fromStoredValue("OFF"))
    }

    @Test
    fun storedCoverageScopeParsingFallsBackSafely() {
        assertEquals(
            SpatialCoverageScope.PHYSICAL_RADIUS,
            SpatialCoverageScope.fromStoredValue(null)
        )
        assertEquals(
            SpatialCoverageScope.PHYSICAL_RADIUS,
            SpatialCoverageScope.fromStoredValue("UNKNOWN")
        )
        assertEquals(
            SpatialCoverageScope.ACCESSIBLE_COVERAGE,
            SpatialCoverageScope.fromStoredValue("ACCESSIBLE_COVERAGE")
        )
    }

    @Test
    fun legacyBackupPreferencesEnableNewLayersByDefault() {
        val restored = Gson().fromJson(
            """{"coverageMode":"LINES_ONLY","coverageScope":"ACCESSIBLE_COVERAGE","pastureFillEnabled":false}""",
            DisplayPreferences::class.java
        )

        assertFalse(restored.pastureFillEnabled)
        assertTrue(restored.pastureBoundariesEnabled)
        assertTrue(restored.waterPointsEnabled)
        assertTrue(restored.gatesEnabled)
        assertTrue(restored.herdBadgesEnabled)
    }
}
