package com.sagewire.rangewater.ui.map

import android.content.Context

data class SavedMapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double
)

/**
 * Keeps the operator's working viewport outside Room so rotation and ordinary relaunches
 * return to the same piece of ground without changing any ranch record.
 */
class MapCameraStateRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): SavedMapCamera? {
        if (!preferences.getBoolean(KEY_PRESENT, false)) return null
        return SavedMapCamera(
            latitude = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_LATITUDE, 0L)),
            longitude = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_LONGITUDE, 0L)),
            zoom = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_ZOOM, 0L)),
            bearing = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_BEARING, 0L)),
            tilt = java.lang.Double.longBitsToDouble(preferences.getLong(KEY_TILT, 0L))
        )
    }

    fun save(camera: SavedMapCamera) {
        preferences.edit()
            .putBoolean(KEY_PRESENT, true)
            .putLong(KEY_LATITUDE, java.lang.Double.doubleToRawLongBits(camera.latitude))
            .putLong(KEY_LONGITUDE, java.lang.Double.doubleToRawLongBits(camera.longitude))
            .putLong(KEY_ZOOM, java.lang.Double.doubleToRawLongBits(camera.zoom))
            .putLong(KEY_BEARING, java.lang.Double.doubleToRawLongBits(camera.bearing))
            .putLong(KEY_TILT, java.lang.Double.doubleToRawLongBits(camera.tilt))
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "rangewater_map_camera"
        private const val KEY_PRESENT = "present"
        private const val KEY_LATITUDE = "latitude_bits"
        private const val KEY_LONGITUDE = "longitude_bits"
        private const val KEY_ZOOM = "zoom_bits"
        private const val KEY_BEARING = "bearing_bits"
        private const val KEY_TILT = "tilt_bits"
    }
}
