package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureCoordinate
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfMeasurement

object AcreageCalculator {
    const val SQUARE_METERS_PER_ACRE = 4046.8564224

    fun calculateAcres(vertices: List<PastureCoordinate>): Double {
        if (vertices.size < 3) return 0.0
        val ring = vertices.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        ring.add(ring.first())
        return squareMetersToAcres(TurfMeasurement.area(Polygon.fromLngLats(listOf(ring))))
    }

    fun squareMetersToAcres(squareMeters: Double): Double = squareMeters / SQUARE_METERS_PER_ACRE
}
