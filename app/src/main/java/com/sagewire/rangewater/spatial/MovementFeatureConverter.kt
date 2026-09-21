package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.CattleMovementEntity
import com.sagewire.rangewater.data.GateWithConnectivity
import com.sagewire.rangewater.data.HerdLocationKind
import com.sagewire.rangewater.data.MovementStatus
import com.sagewire.rangewater.data.PastureWithVertices
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object MovementFeatureConverter {
    fun toMovementRouteLines(
        movement: CattleMovementEntity?,
        pastures: List<PastureWithVertices>,
        resolvedGates: List<GateWithConnectivity>
    ): FeatureCollection {
        if (movement == null || movement.status == MovementStatus.CANCELED || movement.unmappedRoute || movement.gateId == null) {
            return FeatureCollection.fromFeatures(emptyList())
        }
        val gate = resolvedGates.find { it.gate.id == movement.gateId }
            ?: return FeatureCollection.fromFeatures(emptyList())
        val points = mutableListOf<Point>()
        if (movement.originLocationKind == HerdLocationKind.PASTURE) {
            interiorPoint(movement.originPastureId, pastures)?.let(points::add)
        }
        points += Point.fromLngLat(gate.derivedCoordinate.longitude, gate.derivedCoordinate.latitude)
        if (movement.destinationLocationKind == HerdLocationKind.PASTURE) {
            interiorPoint(movement.destinationPastureId, pastures)?.let(points::add)
        }
        if (points.size < 2) return FeatureCollection.fromFeatures(emptyList())
        val properties = JsonObject().apply {
            addProperty("id", movement.id)
            addProperty("isPlanned", movement.status == MovementStatus.PLANNED)
        }
        return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(LineString.fromLngLats(points), properties)))
    }

    fun toMovementHighlights(
        movement: CattleMovementEntity?,
        pastures: List<PastureWithVertices>
    ): FeatureCollection {
        if (movement == null || movement.status == MovementStatus.CANCELED || !movement.unmappedRoute) {
            return FeatureCollection.fromFeatures(emptyList())
        }
        val features = listOfNotNull(movement.originPastureId, movement.destinationPastureId).distinct().mapNotNull { id ->
            val pasture = pastures.find { it.pasture.id == id } ?: return@mapNotNull null
            val vertices = pasture.vertices.sortedBy { it.sequence }
            if (vertices.size < 3) return@mapNotNull null
            val ring = vertices.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList().apply { add(first()) }
            Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)), JsonObject().apply { addProperty("id", id) })
        }
        return FeatureCollection.fromFeatures(features)
    }

    private fun interiorPoint(pastureId: Long?, pastures: List<PastureWithVertices>): Point? {
        val pasture = pastures.find { it.pasture.id == pastureId } ?: return null
        val vertices = pasture.vertices.sortedBy { it.sequence }
        if (vertices.size < 3) return null
        val ring = vertices.map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList().apply { add(first()) }
        val coordinate = JtsGeometryConverter.toJtsPolygon(Polygon.fromLngLats(listOf(ring))).interiorPoint.coordinate
        return Point.fromLngLat(coordinate.x, coordinate.y)
    }
}
