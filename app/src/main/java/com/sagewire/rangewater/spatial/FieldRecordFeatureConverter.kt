package com.sagewire.rangewater.spatial

import com.google.gson.JsonObject
import com.sagewire.rangewater.data.FieldRecordEntity
import com.sagewire.rangewater.data.FieldRecordType
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

object FieldRecordFeatureConverter {
    fun toFeatures(records: List<FieldRecordEntity>, selectedId: Long?): FeatureCollection =
        FeatureCollection.fromFeatures(records.filter { it.archivedAt == null }.map { record ->
            Feature.fromGeometry(
                Point.fromLngLat(record.longitude, record.latitude),
                JsonObject().apply {
                    addProperty("id", record.id)
                    addProperty("recordType", record.recordType.name)
                    addProperty("markerLabel", record.recordType.markerLabel())
                    addProperty("selected", record.id == selectedId)
                }
            )
        })

    private fun FieldRecordType.markerLabel(): String = when (this) {
        FieldRecordType.TASK -> "T"
        FieldRecordType.OBSERVATION -> "O"
        FieldRecordType.INPUT -> "I"
        FieldRecordType.REPAIR -> "R"
        FieldRecordType.ANIMAL -> "A"
        FieldRecordType.WEATHER -> "W"
        FieldRecordType.NOTE -> "N"
    }
}
