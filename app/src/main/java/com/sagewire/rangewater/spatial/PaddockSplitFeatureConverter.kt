package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.PastureCoordinate
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object PaddockSplitFeatureConverter {
    fun regionFeatures(
        resolvedPlans: List<Pair<Long, PaddockSplitResult>>,
        selectedPlanId: Long?
    ): FeatureCollection = FeatureCollection.fromFeatures(
        resolvedPlans.flatMap { (planId, result) ->
            listOf(
                regionFeature(planId, "A", result.sideA, result.sideAAcres, planId == selectedPlanId),
                regionFeature(planId, "B", result.sideB, result.sideBAcres, planId == selectedPlanId)
            )
        }
    )

    fun lineFeatures(
        resolvedPlans: List<Pair<Long, PaddockSplitResult>>,
        selectedPlanId: Long?
    ): FeatureCollection = FeatureCollection.fromFeatures(
        resolvedPlans.map { (planId, result) ->
            lineFeature(planId, result.start.coordinate, result.end.coordinate, planId == selectedPlanId)
        }
    )

    fun draftFeatures(
        start: PaddockBoundaryAnchor?,
        result: PaddockSplitResult?,
        customPath: List<PastureCoordinate> = emptyList()
    ): FeatureCollection {
        val features = mutableListOf<Feature>()
        if (customPath.isNotEmpty()) {
            if (customPath.size >= 2) {
                features += Feature.fromGeometry(
                    LineString.fromLngLats(
                        customPath.map { Point.fromLngLat(it.longitude, it.latitude) }
                    ),
                    JsonObject().apply {
                        addProperty("selected", true)
                        addProperty("kind", "line")
                    }
                )
            }
            features += customPath.mapIndexed { index, coordinate ->
                Feature.fromGeometry(
                    Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                    JsonObject().apply {
                        addProperty("endpoint", index == 0 || index == customPath.lastIndex)
                        addProperty("waypoint", index > 0 && index < customPath.lastIndex)
                    }
                )
            }
        } else if (result != null) {
            features += regionFeature(-1, "A", result.sideA, result.sideAAcres, true)
            features += regionFeature(-1, "B", result.sideB, result.sideBAcres, true)
            features += lineFeature(-1, result.start.coordinate, result.end.coordinate, true)
        } else if (start != null) {
            features += Feature.fromGeometry(
                Point.fromLngLat(start.coordinate.longitude, start.coordinate.latitude),
                JsonObject().apply { addProperty("endpoint", true) }
            )
        }
        return FeatureCollection.fromFeatures(features)
    }

    private fun regionFeature(
        planId: Long,
        side: String,
        coordinates: List<PastureCoordinate>,
        acres: Double,
        selected: Boolean
    ): Feature = Feature.fromGeometry(
        coordinates.toPolygon(),
        JsonObject().apply {
            addProperty("planId", planId)
            addProperty("side", side)
            addProperty("acres", acres)
            addProperty("selected", selected)
            addProperty("kind", "region")
        }
    )

    private fun lineFeature(
        planId: Long,
        start: PastureCoordinate,
        end: PastureCoordinate,
        selected: Boolean
    ): Feature = Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(
                Point.fromLngLat(start.longitude, start.latitude),
                Point.fromLngLat(end.longitude, end.latitude)
            )
        ),
        JsonObject().apply {
            addProperty("planId", planId)
            addProperty("selected", selected)
            addProperty("kind", "line")
        }
    )

    private fun List<PastureCoordinate>.toPolygon(): Polygon {
        val ring = map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        ring += ring.first()
        return Polygon.fromLngLats(listOf(ring))
    }
}
