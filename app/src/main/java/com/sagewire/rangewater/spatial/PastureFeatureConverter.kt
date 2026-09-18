package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.PastureCoordinate
import com.sagewire.rangewater.data.PastureWithVertices
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object PastureFeatureConverter {
    fun toPastureFeatures(
        pastures: List<PastureWithVertices>,
        selectedId: Long?
    ): FeatureCollection = FeatureCollection.fromFeatures(
        pastures.mapNotNull { pasture ->
            val coordinates = pasture.orderedCoordinates()
            if (coordinates.size < 3) return@mapNotNull null
            val properties = JsonObject().apply {
                addProperty("id", pasture.pasture.id)
                addProperty("name", pasture.pasture.name)
                addProperty("selected", pasture.pasture.id == selectedId)
                addProperty("acres", AcreageCalculator.calculateAcres(coordinates))
            }
            Feature.fromGeometry(coordinates.toPolygon(), properties)
        }
    )

    fun draftFeature(vertices: List<PastureCoordinate>): FeatureCollection {
        if (vertices.isEmpty()) return emptyCollection()
        val points = vertices.map { Point.fromLngLat(it.longitude, it.latitude) }
        val geometry = when (points.size) {
            1 -> points.first()
            2 -> LineString.fromLngLats(points)
            else -> vertices.toPolygon()
        }
        return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(geometry)))
    }

    fun handleFeatures(
        vertices: List<PastureCoordinate>,
        includeMidpoints: Boolean,
        selectedVertexIndex: Int?
    ): FeatureCollection {
        val features = vertices.mapIndexed { index, coordinate ->
            Feature.fromGeometry(
                Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                JsonObject().apply {
                    addProperty("index", index)
                    addProperty("handleType", "vertex")
                    addProperty("selected", index == selectedVertexIndex)
                }
            )
        }.toMutableList()
        if (includeMidpoints) {
            GeometryValidator.midpoints(vertices).forEachIndexed { index, coordinate ->
                features += Feature.fromGeometry(
                    Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                    JsonObject().apply {
                        addProperty("index", index)
                        addProperty("handleType", "midpoint")
                        addProperty("selected", false)
                    }
                )
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    fun emptyCollection(): FeatureCollection = FeatureCollection.fromFeatures(emptyList<Feature>())

    fun PastureWithVertices.orderedCoordinates(): List<PastureCoordinate> =
        vertices.sortedBy { it.sequence }.map {
            PastureCoordinate(
                latitude = it.latitude,
                longitude = it.longitude,
                elevationMeters = it.elevationMeters,
                elevationSource = it.elevationSource,
                verticalDatum = it.verticalDatum,
                verticalAccuracyMeters = it.verticalAccuracyMeters,
                elevationCapturedAt = it.elevationCapturedAt
            )
        }

    private fun List<PastureCoordinate>.toPolygon(): Polygon {
        val ring = map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        ring.add(ring.first())
        return Polygon.fromLngLats(listOf(ring))
    }
}
