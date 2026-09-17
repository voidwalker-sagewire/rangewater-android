package com.sagewire.rangewater.ui.map

import org.maplibre.android.maps.Style

/*
 * 🪨 BLOCK 1 — MAP STYLE AND SPATIAL DEFAULTS
 * Purpose: Keeps the approved imagery source separate from rendering code.
 * 🎮 Behavior: Builds a MapLibre v8 raster style backed by the official USGS
 *    Imagery Only tile cache.
 * 🪨 Locked Authority: Michael approved the federal USDA NAIP / USGS imagery path.
 */
object MapConfig {
    // 🌐 Required source attribution shown by MapLibre and the RangeWater status badge.
    const val ATTRIBUTION_LABEL = "USDA, USGS – The National Map: Orthoimagery"

    // 🖍️ Safe Edit Zone: Initial camera shown before GPS or saved pasture data exists.
    const val DEFAULT_LATITUDE = 39.8283
    const val DEFAULT_LONGITUDE = -98.5795
    const val DEFAULT_ZOOM = 4.0

    // 🪨 Official USGS The National Map cached-tile endpoint.
    const val USGS_IMAGERY_TILE_URL =
        "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}"

    // 🎮 MapLibre Style Specification v8. The service publishes 256-pixel Web Mercator
    // tiles through zoom 16; MapLibre may overzoom those tiles at closer pasture scales.
    val USGS_IMAGERY_STYLE_JSON =
        """
        {
          "version": 8,
          "sources": {
            "usgs-imagery": {
              "type": "raster",
              "tiles": ["$USGS_IMAGERY_TILE_URL"],
              "tileSize": 256,
              "maxzoom": 16,
              "attribution": "$ATTRIBUTION_LABEL"
            }
          },
          "layers": [
            {
              "id": "usgs-imagery-layer",
              "type": "raster",
              "source": "usgs-imagery"
            }
          ]
        }
        """.trimIndent()

    // 🎮 BLOCK 2 — STYLE BUILDER FACTORY
    fun createStyleBuilder(): Style.Builder =
        Style.Builder().fromJson(USGS_IMAGERY_STYLE_JSON)
}
