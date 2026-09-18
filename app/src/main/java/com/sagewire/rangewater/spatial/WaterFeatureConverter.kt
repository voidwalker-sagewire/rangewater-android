package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.ui.map.MapConfig
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

/*
 * 🪨 BLOCK 1 — PERSISTED-ASSET FEATURE CONVERSION
 * Purpose: Converts Room entities into synchronized point and 800-foot ring GeoJSON.
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
        points.map { entity ->
            val center = Point.fromLngLat(entity.longitude, entity.latitude)
            val ring = TurfTransformation.circle(
                center,
                MapConfig.BUFFER_RADIUS_METERS,
                MapConfig.BUFFER_CIRCLE_STEPS,
                TurfConstants.UNIT_METERS
            )
            Feature.fromGeometry(ring, propertiesFor(entity, selectedId))
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
