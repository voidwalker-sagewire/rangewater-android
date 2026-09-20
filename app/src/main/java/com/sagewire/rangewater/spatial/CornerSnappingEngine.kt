package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.FenceJunctionEntity
import kotlin.math.cos
import kotlin.math.sqrt
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Projection

object CornerSnappingEngine {
    fun findNearestJunctionScreenSpace(
        tapScreenX: Float,
        tapScreenY: Float,
        junctionScreens: List<Pair<FenceJunctionEntity, Pair<Float, Float>>>,
        tolerancePx: Float
    ): FenceJunctionEntity? {
        var nearest: FenceJunctionEntity? = null
        var nearestSquared = tolerancePx * tolerancePx
        junctionScreens.forEach { (junction, position) ->
            val dx = tapScreenX - position.first
            val dy = tapScreenY - position.second
            val distanceSquared = dx * dx + dy * dy
            if (distanceSquared <= nearestSquared) {
                nearestSquared = distanceSquared
                nearest = junction
            }
        }
        return nearest
    }

    fun findSnapJunction(
        tap: LatLng,
        junctions: List<FenceJunctionEntity>,
        projection: Projection,
        tolerancePx: Float
    ): FenceJunctionEntity? {
        val tapPoint = projection.toScreenLocation(tap)
        return findNearestJunctionScreenSpace(
            tapPoint.x,
            tapPoint.y,
            junctions.map { junction ->
                val point = projection.toScreenLocation(LatLng(junction.latitude, junction.longitude))
                junction to (point.x to point.y)
            },
            tolerancePx
        )
    }

    fun findNearbyJunctions(
        origin: LatLng,
        junctions: List<FenceJunctionEntity>,
        excludedJunctionId: Long,
        maxDistanceMeters: Double = 50.0
    ): List<Pair<FenceJunctionEntity, Double>> {
        val longitudeScale = 111_320.0 * cos(Math.toRadians(origin.latitude))
        val latitudeScale = 110_540.0
        return junctions.asSequence()
            .filter { it.id != excludedJunctionId }
            .map { junction ->
                val dx = (junction.longitude - origin.longitude) * longitudeScale
                val dy = (junction.latitude - origin.latitude) * latitudeScale
                junction to sqrt(dx * dx + dy * dy)
            }
            .filter { it.second <= maxDistanceMeters }
            .sortedBy { it.second }
            .toList()
    }
}
