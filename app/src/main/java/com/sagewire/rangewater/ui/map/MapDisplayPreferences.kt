package com.sagewire.rangewater.ui.map

import android.content.Context
import android.content.SharedPreferences

enum class WaterCoverageMode {
    FULL,
    LINES_ONLY,
    OFF;

    companion object {
        fun fromStoredValue(value: String?): WaterCoverageMode =
            entries.firstOrNull { it.name == value } ?: FULL
    }
}

data class DisplayPreferences(
    val coverageMode: WaterCoverageMode = WaterCoverageMode.FULL,
    val pastureFillEnabled: Boolean = true
)

class DisplayPreferencesRepository(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getPreferences(): DisplayPreferences = DisplayPreferences(
        coverageMode = WaterCoverageMode.fromStoredValue(
            preferences.getString(KEY_COVERAGE_MODE, WaterCoverageMode.FULL.name)
        ),
        pastureFillEnabled = preferences.getBoolean(KEY_PASTURE_FILL, true)
    )

    fun saveCoverageMode(mode: WaterCoverageMode) {
        preferences.edit().putString(KEY_COVERAGE_MODE, mode.name).apply()
    }

    fun savePastureFillEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PASTURE_FILL, enabled).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "rangewater_display_prefs"
        const val KEY_COVERAGE_MODE = "water_coverage_mode"
        const val KEY_PASTURE_FILL = "pasture_fill_enabled"
    }
}
