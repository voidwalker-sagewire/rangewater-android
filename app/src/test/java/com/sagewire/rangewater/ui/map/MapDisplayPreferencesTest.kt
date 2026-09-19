package com.sagewire.rangewater.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDisplayPreferencesTest {
    @Test
    fun defaultsEnableFullCoverageAndPastureFill() {
        val preferences = DisplayPreferences()

        assertEquals(WaterCoverageMode.FULL, preferences.coverageMode)
        assertTrue(preferences.pastureFillEnabled)
    }

    @Test
    fun storedCoverageModeParsingFallsBackSafely() {
        assertEquals(WaterCoverageMode.FULL, WaterCoverageMode.fromStoredValue(null))
        assertEquals(WaterCoverageMode.FULL, WaterCoverageMode.fromStoredValue("UNKNOWN"))
        assertEquals(WaterCoverageMode.LINES_ONLY, WaterCoverageMode.fromStoredValue("LINES_ONLY"))
        assertEquals(WaterCoverageMode.OFF, WaterCoverageMode.fromStoredValue("OFF"))
    }
}
