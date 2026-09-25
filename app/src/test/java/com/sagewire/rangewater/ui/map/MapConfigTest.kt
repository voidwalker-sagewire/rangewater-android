package com.sagewire.rangewater.ui.map

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapConfigTest {
    @Test
    fun hybridStyleRemainsValidJsonWithUniqueLayerIds() {
        val root = JsonParser.parseString(MapConfig.HYBRID_STYLE_JSON).asJsonObject
        val layerIds = root.getAsJsonArray("layers").map {
            it.asJsonObject.get("id").asString
        }

        assertEquals(layerIds.size, layerIds.toSet().size)
        assertTrue(MapConfig.LAYER_PASTURE_LINE in layerIds)
        assertTrue(MapConfig.LAYER_HERD_BADGE_CASING_0 in layerIds)
        assertTrue(MapConfig.LAYER_HERD_BADGE_CASING_1 in layerIds)
        assertTrue(MapConfig.LAYER_HERD_BADGE_CASING_2 in layerIds)
    }
}
