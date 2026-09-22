package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.PastureWithVertices
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import com.sagewire.rangewater.ui.map.MapConfig
import com.sagewire.rangewater.ui.map.SpatialCoverageScope
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
        selectedId: Long?,
        scope: SpatialCoverageScope = SpatialCoverageScope.PHYSICAL_RADIUS,
        assignments: Map<Long, List<Long>> = emptyMap(),
        pastures: List<PastureWithVertices> = emptyList()
    ): FeatureCollection {
        val pastureById = pastures.associateBy { it.pasture.id }
        return FeatureCollection.fromFeatures(
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

            val zones = if (scope == SpatialCoverageScope.PHYSICAL_RADIUS) {
                listOf(listOf(preferred), listOf(transition))
            } else {
                val assignedPolygons = assignments[entity.id].orEmpty().mapNotNull { pastureId ->
                    pastureById[pastureId]?.toPolygon()
                }
                JtsGeometryConverter.clipZonesToPastures(
                    zones = listOf(preferred, transition),
                    pastures = assignedPolygons
                )
            }

            zones[0].map { polygon ->
                Feature.fromGeometry(polygon, zoneProperties(entity, selectedId, MapConfig.ZONE_PREFERRED))
            } + zones[1].map { polygon ->
                Feature.fromGeometry(polygon, zoneProperties(entity, selectedId, MapConfig.ZONE_TRANSITION))
            }
        }
    )
    }

    private fun PastureWithVertices.toPolygon(): Polygon? {
        val ordered = vertices.sortedBy { it.sequence }
        if (ordered.size < 3) return null
        val ring = ordered.map { vertex -> Point.fromLngLat(vertex.longitude, vertex.latitude) }
        return Polygon.fromLngLats(listOf(ring + ring.first()))
    }

    private fun zoneProperties(
        entity: WaterPointEntity,
        selectedId: Long?,
        zone: String
    ) = propertiesFor(entity, selectedId).apply { addProperty("zone", zone) }

    private fun propertiesFor(entity: WaterPointEntity, selectedId: Long?) =
        JsonObject().apply {
            addProperty("id", entity.id)
            addProperty("name", entity.name)
            addProperty("sourceType", entity.sourceType.name)
            addProperty("markerIcon", entity.sourceType.markerIconName())
            addProperty("selected", entity.id == selectedId)
        }

    private fun WaterSourceType.markerIconName(): String = when (this) {
        WaterSourceType.TROUGH -> "marker-water-trough"
        WaterSourceType.TANK -> "marker-water-tank"
        WaterSourceType.SPRING -> "marker-water-spring"
        WaterSourceType.POND -> "marker-water-pond"
        WaterSourceType.HYDRANT -> "marker-water-hydrant"
        WaterSourceType.OTHER -> "marker-water-other"
    }
}
