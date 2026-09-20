package com.sagewire.rangewater.ui.map

import org.maplibre.android.maps.Style

/*
 * 🪨 BLOCK 1 — HYBRID FEDERAL MAP CONFIGURATION
 * Purpose: Declares federal imagery endpoints, zoom thresholds, and one unified
 *          MapLibre style for overview, pasture detail, and labeled navigation.
 * 🎮 Behavior:
 *    1. USGS cached imagery provides fast regional overview and a resilient overzoom
 *       fallback when a device cannot render the USDA detail response.
 *    2. USDA NAIP exportImage requests provide approved close detail from zoom 15.
 *    3. USGS Imagery Topo provides a separately selectable labeled mode.
 * 🪨 Locked Authority: Michael ratified direct USDA NAIP close-detail quality.
 */
object MapConfig {
    // 🌐 Federal source attributions.
    const val USGS_ATTRIBUTION = "USGS The National Map: Orthoimagery"
    const val USDA_ATTRIBUTION = "USDA Farm Production and Conservation: NAIP"
    const val TOPO_ATTRIBUTION = "USGS The National Map: Imagery Topo"

    // 🖍️ Safe Edit Zone: Initial camera shown before GPS or saved pasture data exists.
    const val DEFAULT_LATITUDE = 39.8283
    const val DEFAULT_LONGITUDE = -98.5795
    const val DEFAULT_ZOOM = 4.0

    // 🎮 Working zoom range and automatic imagery transition.
    const val DETAIL_TRANSITION_ZOOM = 15.0
    const val MIN_ALLOWED_ZOOM = 3.0
    const val MAX_AERIAL_ZOOM = 20.0
    const val MAX_LABELED_ZOOM = 16.0

    // 🪨 Cached federal overview and labeled-navigation endpoints.
    const val USGS_IMAGERY_TILE_URL =
        "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}"
    const val USGS_IMAGERY_TOPO_TILE_URL =
        "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryTopo/MapServer/tile/{z}/{y}/{x}"

    // 🪨 RangeWater's cache gateway converts XYZ coordinates to USDA NAIP
    // exportImage requests. The gateway avoids device-specific TLS/routing failures,
    // while the USGS overview remains visible underneath if detail is unavailable.
    const val USDA_NAIP_EXPORT_URL =
        "https://imagery.sagewire.dev/naip/{z}/{x}/{y}.jpg"

    const val LAYER_AERIAL_OVERVIEW = "layer-aerial-overview"
    const val LAYER_AERIAL_DETAIL = "layer-aerial-detail"
    const val LAYER_LABELED_TOPO = "layer-labeled-topo"

    // 🪨 Ratified geodesic water-coverage thresholds.
    const val PREFERRED_RADIUS_METERS = 243.84
    const val TRANSITION_RADIUS_METERS = 304.80
    const val ANNULUS_WIDTH_METERS = 60.96
    const val BUFFER_RADIUS_METERS = PREFERRED_RADIUS_METERS
    const val BUFFER_CIRCLE_STEPS = 64
    const val ZONE_PREFERRED = "preferred"
    const val ZONE_TRANSITION = "transition"

    const val SOURCE_WATER_POINTS = "source-water-points"
    const val SOURCE_WATER_RINGS = "source-water-rings"
    const val SOURCE_PASTURES = "source-pastures"
    const val SOURCE_PASTURE_DRAFT = "source-pasture-draft"
    const val SOURCE_PASTURE_HANDLES = "source-pasture-handles"
    const val SOURCE_SNAPPED_JUNCTION = "source-snapped-junction"
    const val LAYER_PASTURE_FILL = "layer-pasture-fill"
    const val LAYER_PASTURE_CASING = "layer-pasture-casing"
    const val LAYER_PASTURE_LINE = "layer-pasture-line"
    const val LAYER_PASTURE_DRAFT_FILL = "layer-pasture-draft-fill"
    const val LAYER_PASTURE_DRAFT_CASING = "layer-pasture-draft-casing"
    const val LAYER_PASTURE_DRAFT_LINE = "layer-pasture-draft-line"
    const val LAYER_PASTURE_HANDLES = "layer-pasture-handles"
    const val LAYER_SNAPPED_JUNCTION = "layer-snapped-junction"
    const val LAYER_WATER_TRANSITION_FILL = "layer-water-transition-fill"
    const val LAYER_WATER_PREFERRED_FILL = "layer-water-preferred-fill"
    const val LAYER_WATER_RINGS_LINE = "layer-water-rings-line"
    const val LAYER_WATER_POINTS_HIGHLIGHT = "layer-water-points-highlight"
    const val LAYER_WATER_POINTS = "layer-water-points"

    /*
     * 🎮 BLOCK 2 — UNIFIED STYLE SPECIFICATION
     * A single style lets the UI switch Aerial/Labeled layers without rebuilding
     * the style or discarding MapLibre's active cache.
     */
    val HYBRID_STYLE_JSON =
        """
        {
          "version": 8,
          "sources": {
            "source-usgs-overview": {
              "type": "raster",
              "tiles": ["$USGS_IMAGERY_TILE_URL"],
              "tileSize": 256,
              "maxzoom": 15,
              "attribution": "$USGS_ATTRIBUTION"
            },
            "source-usda-detail": {
              "type": "raster",
              "tiles": ["$USDA_NAIP_EXPORT_URL"],
              "tileSize": 256,
              "minzoom": 15,
              "maxzoom": 20,
              "attribution": "$USDA_ATTRIBUTION"
            },
            "source-usgs-topo": {
              "type": "raster",
              "tiles": ["$USGS_IMAGERY_TOPO_TILE_URL"],
              "tileSize": 256,
              "maxzoom": 16,
              "attribution": "$TOPO_ATTRIBUTION"
            },
            "$SOURCE_WATER_RINGS": {
              "type": "geojson",
              "data": { "type": "FeatureCollection", "features": [] }
            },
            "$SOURCE_WATER_POINTS": {
              "type": "geojson",
              "data": { "type": "FeatureCollection", "features": [] }
            },
            "$SOURCE_PASTURES": {
              "type": "geojson",
              "data": { "type": "FeatureCollection", "features": [] }
            },
            "$SOURCE_PASTURE_DRAFT": {
              "type": "geojson",
              "data": { "type": "FeatureCollection", "features": [] }
            },
            "$SOURCE_PASTURE_HANDLES": {
              "type": "geojson",
              "data": { "type": "FeatureCollection", "features": [] }
            },
            "$SOURCE_SNAPPED_JUNCTION": {
              "type": "geojson",
              "data": { "type": "FeatureCollection", "features": [] }
            }
          },
          "layers": [
            {
              "id": "$LAYER_AERIAL_OVERVIEW",
              "type": "raster",
              "source": "source-usgs-overview",
              "layout": { "visibility": "visible" }
            },
            {
              "id": "$LAYER_AERIAL_DETAIL",
              "type": "raster",
              "source": "source-usda-detail",
              "minzoom": 15,
              "layout": { "visibility": "visible" }
            },
            {
              "id": "$LAYER_LABELED_TOPO",
              "type": "raster",
              "source": "source-usgs-topo",
              "layout": { "visibility": "none" }
            },
            {
              "id": "$LAYER_PASTURE_FILL",
              "type": "fill",
              "source": "$SOURCE_PASTURES",
              "paint": {
                "fill-color": "#FF2D95",
                "fill-opacity": 0.08
              }
            },
            {
              "id": "$LAYER_PASTURE_DRAFT_FILL",
              "type": "fill",
              "source": "$SOURCE_PASTURE_DRAFT",
              "paint": {
                "fill-color": "#FF2D95",
                "fill-opacity": 0.08
              }
            },
            {
              "id": "$LAYER_WATER_TRANSITION_FILL",
              "type": "fill",
              "source": "$SOURCE_WATER_RINGS",
              "filter": ["==", ["get", "zone"], "$ZONE_TRANSITION"],
              "paint": {
                "fill-color": "#FFD600",
                "fill-opacity": 0.18
              }
            },
            {
              "id": "$LAYER_WATER_PREFERRED_FILL",
              "type": "fill",
              "source": "$SOURCE_WATER_RINGS",
              "filter": ["==", ["get", "zone"], "$ZONE_PREFERRED"],
              "paint": {
                "fill-color": "#2E7D32",
                "fill-opacity": 0.22
              }
            },
            {
              "id": "$LAYER_WATER_RINGS_LINE",
              "type": "line",
              "source": "$SOURCE_WATER_RINGS",
              "paint": {
                "line-color": [
                  "case",
                  ["get", "selected"], "#FFFFFF",
                  ["==", ["get", "zone"], "$ZONE_PREFERRED"], "#2E7D32",
                  "#FBC02D"
                ],
                "line-opacity": 0.60,
                "line-width": [
                  "case", ["get", "selected"], 2.0, 1.0
                ]
              }
            },
            {
              "id": "$LAYER_PASTURE_CASING",
              "type": "line",
              "source": "$SOURCE_PASTURES",
              "paint": {
                "line-color": "#151515",
                "line-width": ["case", ["get", "selected"], 6.0, 4.0]
              }
            },
            {
              "id": "$LAYER_PASTURE_LINE",
              "type": "line",
              "source": "$SOURCE_PASTURES",
              "paint": {
                "line-color": "#FF2D95",
                "line-width": ["case", ["get", "selected"], 4.0, 2.0]
              }
            },
            {
              "id": "$LAYER_PASTURE_DRAFT_CASING",
              "type": "line",
              "source": "$SOURCE_PASTURE_DRAFT",
              "paint": { "line-color": "#151515", "line-width": 4.0 }
            },
            {
              "id": "$LAYER_PASTURE_DRAFT_LINE",
              "type": "line",
              "source": "$SOURCE_PASTURE_DRAFT",
              "paint": { "line-color": "#FF2D95", "line-width": 2.0 }
            },
            {
              "id": "$LAYER_SNAPPED_JUNCTION",
              "type": "circle",
              "source": "$SOURCE_SNAPPED_JUNCTION",
              "paint": {
                "circle-radius": 14.0,
                "circle-color": "#FFD600",
                "circle-opacity": 0.45,
                "circle-stroke-color": "#FFD600",
                "circle-stroke-width": 3.0
              }
            },
            {
              "id": "$LAYER_PASTURE_HANDLES",
              "type": "circle",
              "source": "$SOURCE_PASTURE_HANDLES",
              "paint": {
                "circle-radius": [
                  "case", ["get", "selected"], 10.0, 8.0
                ],
                "circle-color": [
                  "case",
                  ["get", "selected"], "#FFD600",
                  ["get", "isShared"], "#00E5FF",
                  "#FFFFFF"
                ],
                "circle-stroke-color": "#FF2D95",
                "circle-stroke-width": 3.0
              }
            },
            {
              "id": "$LAYER_WATER_POINTS_HIGHLIGHT",
              "type": "circle",
              "source": "$SOURCE_WATER_POINTS",
              "filter": ["==", ["get", "selected"], true],
              "paint": {
                "circle-radius": 12.0,
                "circle-color": "#FFFFFF",
                "circle-opacity": 0.95
              }
            },
            {
              "id": "$LAYER_WATER_POINTS",
              "type": "circle",
              "source": "$SOURCE_WATER_POINTS",
              "paint": {
                "circle-radius": [
                  "case", ["get", "selected"], 8.0, 6.0
                ],
                "circle-color": [
                  "case", ["get", "selected"], "#FFD600", "#00E5FF"
                ],
                "circle-stroke-color": "#FFFFFF",
                "circle-stroke-width": [
                  "case", ["get", "selected"], 3.0, 2.0
                ]
              }
            }
          ]
        }
        """.trimIndent()

    fun createStyleBuilder(): Style.Builder =
        Style.Builder().fromJson(HYBRID_STYLE_JSON)
}
