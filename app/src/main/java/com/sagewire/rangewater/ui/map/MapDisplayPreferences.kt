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
    val pastureFillEnabled: Boolean = true,
    val pastureBoundariesEnabled: Boolean = true,
    val waterPointsEnabled: Boolean = true,
    val gatesEnabled: Boolean = true,
    val herdBadgesEnabled: Boolean = true
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
        pastureFillEnabled = preferences.getBoolean(KEY_PASTURE_FILL, true),
        pastureBoundariesEnabled = preferences.getBoolean(KEY_PASTURE_BOUNDARIES, true),
        waterPointsEnabled = preferences.getBoolean(KEY_WATER_POINTS, true),
        gatesEnabled = preferences.getBoolean(KEY_GATES, true),
        herdBadgesEnabled = preferences.getBoolean(KEY_HERD_BADGES, true)
    )

    fun saveCoverageMode(mode: WaterCoverageMode) {
        preferences.edit().putString(KEY_COVERAGE_MODE, mode.name).apply()
    }

    fun savePastureFillEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PASTURE_FILL, enabled).apply()
    }

    fun savePastureBoundariesEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PASTURE_BOUNDARIES, enabled).apply()
    }

    fun saveWaterPointsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WATER_POINTS, enabled).apply()
    }

    fun saveGatesEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_GATES, enabled).apply()
    }

    fun saveHerdBadgesEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_HERD_BADGES, enabled).apply()
    }

    fun saveCoverageScope(scope: SpatialCoverageScope) {
        preferences.edit().putString(KEY_COVERAGE_SCOPE, scope.name).apply()
    }

    /** Synchronous because restore must know whether the complete recovery succeeded. */
    fun replacePreferences(value: DisplayPreferences): Boolean = preferences.edit()
        .putString(KEY_COVERAGE_MODE, value.coverageMode.name)
        .putString(KEY_COVERAGE_SCOPE, value.coverageScope.name)
        .putBoolean(KEY_PASTURE_FILL, value.pastureFillEnabled)
        .putBoolean(KEY_PASTURE_BOUNDARIES, value.pastureBoundariesEnabled)
        .putBoolean(KEY_WATER_POINTS, value.waterPointsEnabled)
        .putBoolean(KEY_GATES, value.gatesEnabled)
        .putBoolean(KEY_HERD_BADGES, value.herdBadgesEnabled)
        .commit()

    companion object {
        const val PREFERENCES_NAME = "rangewater_display_prefs"
        const val KEY_COVERAGE_MODE = "water_coverage_mode"
        const val KEY_COVERAGE_SCOPE = "spatial_coverage_scope"
        const val KEY_PASTURE_FILL = "pasture_fill_enabled"
        const val KEY_PASTURE_BOUNDARIES = "pasture_boundaries_enabled"
        const val KEY_WATER_POINTS = "water_points_enabled"
        const val KEY_GATES = "gates_enabled"
        const val KEY_HERD_BADGES = "herd_badges_enabled"
    }
}
