package com.sagewire.rangewater.spatial

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon as JtsPolygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.geom.util.PolygonExtracter
import org.locationtech.jts.operation.overlayng.OverlayNG
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/*
 * 🪨 BLOCK 1 — CONSTRUCTIVE COVERAGE GEOMETRY
 * Purpose: Clips already-geodesic Turf zone polygons against explicit pasture access.
 * 🪨 Protected: JTS operates on WGS84 vertices only for topology; acreage is not derived here.
 */
object JtsGeometryConverter {
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

    fun toJtsPolygon(polygon: Polygon): JtsPolygon {
        val rings = polygon.coordinates()
        require(rings.isNotEmpty()) { "Polygon requires an exterior ring" }
        val shell = geometryFactory.createLinearRing(rings.first().toCoordinates())
        val holes = rings.drop(1).map { ring ->
            geometryFactory.createLinearRing(ring.toCoordinates())
        }.toTypedArray()
        return geometryFactory.createPolygon(shell, holes)
    }

    fun clipZoneToPastures(zone: Polygon, pastures: List<Polygon>): List<Polygon> =
        clipZonesToPastures(listOf(zone), pastures).single()

    fun clipZonesToPastures(
        zones: List<Polygon>,
        pastures: List<Polygon>
    ): List<List<Polygon>> {
        if (pastures.isEmpty()) return zones.map { emptyList() }
        val accessibleLand = OverlayNGRobust.union(pastures.map(::toJtsPolygon))
        return zones.map { zone ->
            val intersection = OverlayNGRobust.overlay(
                toJtsPolygon(zone),
                accessibleLand,
                OverlayNG.INTERSECTION
            )
            toMapLibrePolygons(intersection)
        }
    }

    fun toMapLibrePolygons(geometry: Geometry): List<Polygon> {
        if (geometry.isEmpty) return emptyList()
        @Suppress("UNCHECKED_CAST")
        val polygons = PolygonExtracter.getPolygons(geometry) as List<JtsPolygon>
        return polygons.filter { it.area > 0.0 }.map { polygon ->
            val shell = polygon.exteriorRing.coordinates.toPoints()
            val rings = mutableListOf(shell)
            for (index in 0 until polygon.numInteriorRing) {
                val rawHole = polygon.getInteriorRingN(index).coordinates.toPoints()
                rings += if (signedArea(shell) * signedArea(rawHole) < 0.0) {
                    rawHole
                } else {
                    rawHole.asReversed()
                }
            }
            Polygon.fromLngLats(rings)
        }
    }

    private fun List<Point>.toCoordinates(): Array<Coordinate> {
        require(size >= 4) { "A closed polygon ring requires at least four coordinates" }
        val coordinates = map { point -> Coordinate(point.longitude(), point.latitude()) }
        require(coordinates.first().equals2D(coordinates.last())) { "Polygon ring must be closed" }
        return coordinates.toTypedArray()
    }

    private fun Array<Coordinate>.toPoints() = map { coordinate ->
        Point.fromLngLat(coordinate.x, coordinate.y)
    }

    private fun signedArea(ring: List<Point>): Double = ring.zipWithNext().sumOf { (a, b) ->
        (a.longitude() * b.latitude()) - (b.longitude() * a.latitude())
    } / 2.0
}
