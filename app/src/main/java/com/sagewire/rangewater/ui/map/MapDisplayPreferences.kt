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

enum class SpatialCoverageScope {
    PHYSICAL_RADIUS,
    ACCESSIBLE_COVERAGE;

    companion object {
        fun fromStoredValue(value: String?): SpatialCoverageScope =
            entries.firstOrNull { it.name == value } ?: PHYSICAL_RADIUS
    }
}

data class DisplayPreferences(
    val coverageMode: WaterCoverageMode = WaterCoverageMode.FULL,
    val coverageScope: SpatialCoverageScope = SpatialCoverageScope.PHYSICAL_RADIUS,
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
        coverageScope = SpatialCoverageScope.fromStoredValue(
            preferences.getString(KEY_COVERAGE_SCOPE, SpatialCoverageScope.PHYSICAL_RADIUS.name)
        ),
        pastureFillEnabled = preferences.getBoolean(KEY_PASTURE_FILL, true)
    )

    fun saveCoverageMode(mode: WaterCoverageMode) {
        preferences.edit().putString(KEY_COVERAGE_MODE, mode.name).apply()
    }

    fun savePastureFillEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PASTURE_FILL, enabled).apply()
    }

    fun saveCoverageScope(scope: SpatialCoverageScope) {
        preferences.edit().putString(KEY_COVERAGE_SCOPE, scope.name).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "rangewater_display_prefs"
        const val KEY_COVERAGE_MODE = "water_coverage_mode"
        const val KEY_COVERAGE_SCOPE = "spatial_coverage_scope"
        const val KEY_PASTURE_FILL = "pasture_fill_enabled"
    }
}
