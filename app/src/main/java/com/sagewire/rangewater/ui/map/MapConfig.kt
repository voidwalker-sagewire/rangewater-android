package com.sagewire.rangewater.ui.map

/*
 * 🪨 BLOCK 1 — MAP STYLE AND SPATIAL DEFAULTS
 * Purpose: Keeps map-provider configuration separate from rendering code.
 * 🎮 Behavior: Supplies the public development style and initial camera position.
 * 🔧 Unfinished Work: Replace the demo style only after Michael approves a
 *    production provider and its licensing, attribution, and offline-use terms.
 */
object MapConfig {
    // 🌐 Public development style. No API key or account is required.
    const val DEFAULT_STYLE_URI = "https://demotiles.maplibre.org/style.json"

    // 🖍️ Safe Edit Zone: Initial camera shown before GPS or saved pasture data exists.
    const val DEFAULT_LATITUDE = 39.8283
    const val DEFAULT_LONGITUDE = -98.5795
    const val DEFAULT_ZOOM = 4.0
}
