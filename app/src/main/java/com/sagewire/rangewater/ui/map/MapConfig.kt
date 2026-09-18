package com.sagewire.rangewater.ui.map

import org.maplibre.android.maps.Style

/*
 * 🪨 BLOCK 1 — HYBRID FEDERAL MAP CONFIGURATION
 * Purpose: Declares federal imagery endpoints, zoom thresholds, and one unified
 *          MapLibre style for overview, pasture detail, and labeled navigation.
 * 🎮 Behavior:
 *    1. USGS cached imagery provides fast regional overview through zoom 15.
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

    // 🪨 Verified USDA NAIP dynamic request. MapLibre substitutes each tile's
    // Web Mercator bounds into {bbox-epsg-3857}; the server returns a 512 px image.
    const val USDA_NAIP_EXPORT_URL =
        "https://apps.geo.fpac.usda.gov/geo-imagery/rest/services/naip/conus_naip/ImageServer/exportImage?f=image&bbox={bbox-epsg-3857}&bboxSR=3857&imageSR=3857&size=512%2C512&format=jpgpng"

    const val LAYER_AERIAL_OVERVIEW = "layer-aerial-overview"
    const val LAYER_AERIAL_DETAIL = "layer-aerial-detail"
    const val LAYER_LABELED_TOPO = "layer-labeled-topo"

    // 🪨 The project's ratified 800-foot geodesic water-coverage baseline.
    const val BUFFER_RADIUS_METERS = 243.84
    const val BUFFER_CIRCLE_STEPS = 64

    const val SOURCE_WATER_POINT = "source-water-point"
    const val SOURCE_WATER_RING = "source-water-ring"
    const val LAYER_WATER_RING_FILL = "layer-water-ring-fill"
    const val LAYER_WATER_RING_LINE = "layer-water-ring-line"
    const val LAYER_WATER_POINT = "layer-water-point"

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
              "tileSize": 512,
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
            "$SOURCE_WATER_RING": {
              "type": "geojson",
              "data": { "type": "FeatureCollection", "features": [] }
            },
            "$SOURCE_WATER_POINT": {
              "type": "geojson",
              "data": { "type": "FeatureCollection", "features": [] }
            }
          },
          "layers": [
            {
              "id": "$LAYER_AERIAL_OVERVIEW",
              "type": "raster",
              "source": "source-usgs-overview",
              "maxzoom": 15,
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
              "id": "$LAYER_WATER_RING_FILL",
              "type": "fill",
              "source": "$SOURCE_WATER_RING",
              "paint": {
                "fill-color": "#00E5FF",
                "fill-opacity": 0.15
              }
            },
            {
              "id": "$LAYER_WATER_RING_LINE",
              "type": "line",
              "source": "$SOURCE_WATER_RING",
              "paint": {
                "line-color": "#00B0FF",
                "line-opacity": 0.8,
                "line-width": 1.5
              }
            },
            {
              "id": "$LAYER_WATER_POINT",
              "type": "circle",
              "source": "$SOURCE_WATER_POINT",
              "paint": {
                "circle-radius": 6.0,
                "circle-color": "#00E5FF",
                "circle-stroke-color": "#FFFFFF",
                "circle-stroke-width": 2.0
              }
            }
          ]
        }
        """.trimIndent()

    fun createStyleBuilder(): Style.Builder =
        Style.Builder().fromJson(HYBRID_STYLE_JSON)
}
