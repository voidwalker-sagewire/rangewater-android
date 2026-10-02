package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.PastureCoordinate
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object PolystrandPlanFeatureConverter {
    fun regionFeatures(
        resolvedPlans: List<PolystrandPlanResult>,
        selectedPlanId: Long?
    ): FeatureCollection = FeatureCollection.fromFeatures(
        resolvedPlans.flatMap { result ->
            result.regions.mapIndexed { index, region ->
                Feature.fromGeometry(
                    region.coordinates.toPolygon(),
                    JsonObject().apply {
                        addProperty("planId", result.plan.id)
                        addProperty("regionKey", region.key)
                        addProperty("label", region.label)
                        addProperty("side", if (index % 2 == 0) "A" else "B")
                        addProperty("acres", region.acreage)
                        addProperty("selected", result.plan.id == selectedPlanId)
                        addProperty("kind", "region")
                    }
                )
            }
        }
    )

    fun lineFeatures(
        resolvedPlans: List<PolystrandPlanResult>,
        selectedPlanId: Long?
    ): FeatureCollection = FeatureCollection.fromFeatures(
        resolvedPlans.flatMap { result ->
            result.dividers.map { divider ->
                Feature.fromGeometry(
                    LineString.fromLngLats(divider.coordinates.map {
                        Point.fromLngLat(it.longitude, it.latitude)
                    }),
                    JsonObject().apply {
                        addProperty("planId", result.plan.id)
                        addProperty("dividerId", divider.divider.id)
                        addProperty("name", divider.divider.name)
                        addProperty("selected", result.plan.id == selectedPlanId)
                        addProperty("kind", "line")
                    }
                )
            }
        }
    )

    private fun List<PastureCoordinate>.toPolygon(): Polygon {
        val ring = map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        ring += ring.first()
        return Polygon.fromLngLats(listOf(ring))
    }
}
