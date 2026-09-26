package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.PastureCoordinate
import com.sagewire.rangewater.data.GateWithConnectivity
import com.sagewire.rangewater.data.PastureWithVertices
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

object PastureFeatureConverter {
    fun toPastureFeatures(
        pastures: List<PastureWithVertices>,
        selectedId: Long?,
        focusedPastureId: Long? = null,
        focusColorHex: String? = null,
        focusedPastureIds: Set<Long> = focusedPastureId?.let(::setOf).orEmpty()
    ): FeatureCollection = FeatureCollection.fromFeatures(
        pastures.mapNotNull { pasture ->
            val coordinates = pasture.orderedCoordinates()
            if (coordinates.size < 3) return@mapNotNull null
            val properties = JsonObject().apply {
                addProperty("id", pasture.pasture.id)
                addProperty("name", pasture.pasture.name)
                addProperty("selected", pasture.pasture.id == selectedId)
                addFocusProperties(pasture.pasture.id, focusedPastureId, focusedPastureIds, focusColorHex)
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
        selectedVertexIndex: Int?,
        sharedJunctionIds: Set<Long> = emptySet()
    ): FeatureCollection {
        val features = vertices.mapIndexed { index, coordinate ->
            Feature.fromGeometry(
                Point.fromLngLat(coordinate.longitude, coordinate.latitude),
                JsonObject().apply {
                    addProperty("index", index)
                    addProperty("handleType", "vertex")
                    addProperty("selected", index == selectedVertexIndex)
                    addProperty(
                        "isShared",
                        coordinate.junctionId != null && coordinate.junctionId in sharedJunctionIds
                    )
                    coordinate.junctionId?.let { addProperty("junctionId", it) }
                }
            )
        }
        return FeatureCollection.fromFeatures(features)
    }

    fun toPastureBoundaryLines(
        pastures: List<PastureWithVertices>,
        gates: List<GateWithConnectivity>,
        selectedId: Long?,
        focusedPastureId: Long? = null,
        focusColorHex: String? = null,
        focusedPastureIds: Set<Long> = focusedPastureId?.let(::setOf).orEmpty()
    ): FeatureCollection {
        val features = mutableListOf<Feature>()
        pastures.forEach pastureLoop@ { pasture ->
            val vertices = pasture.vertices.sortedBy { it.sequence }
            if (vertices.size < 3) return@pastureLoop
            vertices.indices.forEach { index ->
                val start = vertices[index]
                val end = vertices[(index + 1) % vertices.size]
                val junctionAId = minOf(start.junctionId, end.junctionId)
                val junctionBId = maxOf(start.junctionId, end.junctionId)
                val gatesOnSegment = gates
                    .filter { it.gate.junctionAId == junctionAId && it.gate.junctionBId == junctionBId }
                    .sortedBy { it.gate.segmentRatio }
                val properties = JsonObject().apply {
                    addProperty("id", pasture.pasture.id)
                    addProperty("selected", pasture.pasture.id == selectedId)
                    addFocusProperties(pasture.pasture.id, focusedPastureId, focusedPastureIds, focusColorHex)
                }
                if (gatesOnSegment.isEmpty()) {
                    features += lineFeature(start.longitude, start.latitude, end.longitude, end.latitude, properties)
                } else {
                    var current = Point.fromLngLat(start.longitude, start.latitude)
                    val forward = start.junctionId < end.junctionId
                    val orderedGates = if (forward) gatesOnSegment else gatesOnSegment.reversed()
                    orderedGates.forEach { gate ->
                        val gapStart = if (forward) gate.hingeCoordinate else gate.latchCoordinate
                        val gapEnd = if (forward) gate.latchCoordinate else gate.hingeCoordinate
                        val beforeGap = Point.fromLngLat(gapStart.longitude, gapStart.latitude)
                        if (current != beforeGap) {
                            features += Feature.fromGeometry(
                                LineString.fromLngLats(listOf(current, beforeGap)),
                                properties.deepCopy()
                            )
                        }
                        current = Point.fromLngLat(gapEnd.longitude, gapEnd.latitude)
                    }
                    val segmentEnd = Point.fromLngLat(end.longitude, end.latitude)
                    if (current != segmentEnd) {
                        features += Feature.fromGeometry(
                            LineString.fromLngLats(listOf(current, segmentEnd)),
                            properties.deepCopy()
                        )
                    }
                }
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    private fun lineFeature(
        startLongitude: Double,
        startLatitude: Double,
        endLongitude: Double,
        endLatitude: Double,
        properties: JsonObject
    ): Feature = Feature.fromGeometry(
        LineString.fromLngLats(
            listOf(
                Point.fromLngLat(startLongitude, startLatitude),
                Point.fromLngLat(endLongitude, endLatitude)
            )
        ),
        properties.deepCopy()
    )

    private fun JsonObject.addFocusProperties(
        pastureId: Long,
        focusedPastureId: Long?,
        focusedPastureIds: Set<Long>,
        focusColorHex: String?
    ) {
        val focusActive = focusedPastureIds.isNotEmpty()
        addProperty("focused", pastureId in focusedPastureIds)
        addProperty("focusCurrent", pastureId == focusedPastureId)
        addProperty("dimmed", focusActive && pastureId !in focusedPastureIds)
        addProperty("focusColor", focusColorHex ?: "#FF2D95")
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
                elevationCapturedAt = it.elevationCapturedAt,
                junctionId = it.junctionId
            )
        }

    private fun List<PastureCoordinate>.toPolygon(): Polygon {
        val ring = map { Point.fromLngLat(it.longitude, it.latitude) }.toMutableList()
        ring.add(ring.first())
        return Polygon.fromLngLats(listOf(ring))
    }
}
