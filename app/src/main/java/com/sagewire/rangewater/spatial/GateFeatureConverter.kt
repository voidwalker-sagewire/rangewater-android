package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.GateEntity
import com.sagewire.rangewater.data.GateWithConnectivity
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object GateFeatureConverter {
    fun toGateFeatures(
        gates: List<GateWithConnectivity>,
        selectedGateId: Long?
    ): FeatureCollection {
        val features = mutableListOf<Feature>()
        gates.forEach { item ->
            val gate = item.gate
            val selected = gate.id == selectedGateId
            val hinge = item.hingeCoordinate
            val latch = item.latchCoordinate
            val center = item.derivedCoordinate
            val latitudeRadians = Math.toRadians(center.latitude)
            val metersPerLongitudeDegree = 111_320.0 * cos(latitudeRadians)
            val metersPerLatitudeDegree = 110_540.0
            val fenceBearing = atan2(
                (latch.latitude - hinge.latitude) * metersPerLatitudeDegree,
                (latch.longitude - hinge.longitude) * metersPerLongitudeDegree
            )
            val commonProperties = JsonObject().apply {
                addProperty("id", gate.id)
                addProperty("name", gate.name)
                addProperty("status", gate.status)
                addProperty("isShared", item.isShared)
                addProperty("selected", selected)
                addProperty("connectivity", item.connectivityDescription)
            }

            features += Feature.fromGeometry(
                Point.fromLngLat(center.longitude, center.latitude),
                commonProperties.deepCopy().apply { addProperty("isOverview", true) }
            )

            val leafEnd = if (gate.status == GateEntity.STATUS_OPEN) {
                val leafAngle = fenceBearing + Math.toRadians(45.0)
                Point.fromLngLat(
                    hinge.longitude + gate.widthMeters * cos(leafAngle) / metersPerLongitudeDegree,
                    hinge.latitude + gate.widthMeters * sin(leafAngle) / metersPerLatitudeDegree
                ).also {
                    val arcPoints = (0..8).map { step ->
                        val angle = fenceBearing + Math.toRadians(45.0) * (step / 8.0)
                        Point.fromLngLat(
                            hinge.longitude + gate.widthMeters * cos(angle) / metersPerLongitudeDegree,
                            hinge.latitude + gate.widthMeters * sin(angle) / metersPerLatitudeDegree
                        )
                    }
                    features += Feature.fromGeometry(
                        LineString.fromLngLats(arcPoints),
                        JsonObject().apply {
                            addProperty("id", gate.id)
                            addProperty("isArc", true)
                            addProperty("selected", selected)
                        }
                    )
                }
            } else {
                Point.fromLngLat(latch.longitude, latch.latitude)
            }

            features += Feature.fromGeometry(
                LineString.fromLngLats(
                    listOf(Point.fromLngLat(hinge.longitude, hinge.latitude), leafEnd)
                ),
                commonProperties
            )
            features += Feature.fromGeometry(
                Point.fromLngLat(center.longitude, center.latitude),
                JsonObject().apply {
                    addProperty("id", gate.id)
                    addProperty("isTouchTarget", true)
                    addProperty("selected", selected)
                }
            )
        }
        return FeatureCollection.fromFeatures(features)
    }
}
