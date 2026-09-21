package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.CountUnit
import com.sagewire.rangewater.data.HerdEntity
import com.sagewire.rangewater.data.HerdLocationKind
import com.sagewire.rangewater.data.PastureWithVertices
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object HerdFeatureConverter {
    fun toHerdBadges(
        herds: List<HerdEntity>,
        pastures: List<PastureWithVertices>,
        selectedHerdId: Long?
    ): FeatureCollection {
        val features = mutableListOf<Feature>()
        val occupied = herds
            .filter { it.archivedAt == null && it.locationKind == HerdLocationKind.PASTURE && it.currentPastureId != null }
            .groupBy { it.currentPastureId!! }

        occupied.forEach { (pastureId, unsortedHerds) ->
            val pasture = pastures.find { it.pasture.id == pastureId } ?: return@forEach
            val vertices = pasture.vertices.sortedBy { it.sequence }
            if (vertices.size < 3) return@forEach
            val ring = vertices.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList().apply { add(first()) }
            val interior = JtsGeometryConverter.toJtsPolygon(Polygon.fromLngLats(listOf(ring))).interiorPoint.coordinate
            val pastureHerds = unsortedHerds.sortedBy { it.name.lowercase() }

            pastureHerds.take(if (pastureHerds.size > 3) 2 else 3).forEachIndexed { index, herd ->
                features += badgeFeature(herd, interior.x, interior.y, index, selectedHerdId)
            }
            if (pastureHerds.size > 3) {
                val properties = JsonObject().apply {
                    addProperty("pastureId", pastureId)
                    addProperty("isOverflow", true)
                    addProperty("overflowCount", pastureHerds.size - 2)
                    addProperty("stackIndex", 2)
                    addProperty("color", "#FFFFFF")
                    addProperty("selected", false)
                }
                features += Feature.fromGeometry(Point.fromLngLat(interior.x, interior.y), properties)
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    private fun badgeFeature(
        herd: HerdEntity,
        longitude: Double,
        latitude: Double,
        stackIndex: Int,
        selectedHerdId: Long?
    ): Feature {
        val unit = if (herd.countUnit == CountUnit.PAIRS) "pairs" else "hd"
        val properties = JsonObject().apply {
            addProperty("id", herd.id)
            addProperty("name", herd.name)
            addProperty("displayLabel", "${herd.name} (${herd.quantity} $unit)")
            addProperty("color", herd.markerColorHex)
            addProperty("shortMarker", herd.shortMarkerLabel.orEmpty())
            addProperty("selected", herd.id == selectedHerdId)
            addProperty("stackIndex", stackIndex)
            addProperty("isOverflow", false)
        }
        return Feature.fromGeometry(Point.fromLngLat(longitude, latitude), properties)
    }
}
