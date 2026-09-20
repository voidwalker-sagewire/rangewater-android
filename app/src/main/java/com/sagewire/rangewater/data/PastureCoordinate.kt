package com.sagewire.rangewater.data

import org.maplibre.android.geometry.LatLng

/** In-memory fence coordinate. A non-null junctionId points at a persisted physical corner. */
data class PastureCoordinate(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val elevationSource: String? = null,
    val verticalDatum: String? = null,
    val verticalAccuracyMeters: Double? = null,
    val elevationCapturedAt: Long? = null,
    val junctionId: Long? = null
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)

    companion object {
        fun fromLatLng(latLng: LatLng, junctionId: Long? = null) = PastureCoordinate(
            latitude = latLng.latitude,
            longitude = latLng.longitude,
            junctionId = junctionId
        )
    }
}
