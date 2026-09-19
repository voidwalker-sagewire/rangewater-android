package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.ui.map.MapConfig
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

/*
 * 🪨 BLOCK 1 — PERSISTED-ASSET FEATURE CONVERSION
 * Purpose: Converts Room entities into synchronized point and tiered coverage GeoJSON.
 */
object WaterFeatureConverter {
    fun toPointFeatures(
        points: List<WaterPointEntity>,
        selectedId: Long?
    ): FeatureCollection = FeatureCollection.fromFeatures(
        points.map { entity ->
            Feature.fromGeometry(
                Point.fromLngLat(entity.longitude, entity.latitude),
                propertiesFor(entity, selectedId)
            )
        }
    )

    fun toRingFeatures(
        points: List<WaterPointEntity>,
        selectedId: Long?
    ): FeatureCollection = FeatureCollection.fromFeatures(
        points.flatMap { entity ->
            val center = Point.fromLngLat(entity.longitude, entity.latitude)
            val preferred = TurfTransformation.circle(
                center,
                MapConfig.PREFERRED_RADIUS_METERS,
                MapConfig.BUFFER_CIRCLE_STEPS,
                TurfConstants.UNIT_METERS
            )
            val transitionBoundary = TurfTransformation.circle(
                center,
                MapConfig.TRANSITION_RADIUS_METERS,
                MapConfig.BUFFER_CIRCLE_STEPS,
                TurfConstants.UNIT_METERS
            )

            // MapLibre classifies polygon holes by winding. Turf generates both circles
            // with the same winding, so reverse the inner ring before constructing the
            // true 800–1,000-foot annulus.
            val transition = Polygon.fromLngLats(
                listOf(
                    transitionBoundary.coordinates().single(),
                    preferred.coordinates().single().asReversed()
                )
            )

            listOf(
                Feature.fromGeometry(
                    preferred,
                    propertiesFor(entity, selectedId).apply {
                        addProperty("zone", MapConfig.ZONE_PREFERRED)
                    }
                ),
                Feature.fromGeometry(
                    transition,
                    propertiesFor(entity, selectedId).apply {
                        addProperty("zone", MapConfig.ZONE_TRANSITION)
                    }
                )
            )
        }
    )

    private fun propertiesFor(entity: WaterPointEntity, selectedId: Long?) =
        JsonObject().apply {
            addProperty("id", entity.id)
            addProperty("name", entity.name)
            addProperty("sourceType", entity.sourceType.name)
            addProperty("selected", entity.id == selectedId)
        }
}
