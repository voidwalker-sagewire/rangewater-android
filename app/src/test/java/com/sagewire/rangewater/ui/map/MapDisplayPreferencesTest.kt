package com.sagewire.rangewater.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDisplayPreferencesTest {
    @Test
    fun defaultsEnableFullCoverageAndPastureFill() {
        val preferences = DisplayPreferences()

        assertEquals(WaterCoverageMode.FULL, preferences.coverageMode)
        assertEquals(SpatialCoverageScope.PHYSICAL_RADIUS, preferences.coverageScope)
        assertTrue(preferences.pastureFillEnabled)
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
}
