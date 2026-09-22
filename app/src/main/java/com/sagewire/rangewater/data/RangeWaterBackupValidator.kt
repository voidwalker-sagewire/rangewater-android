package com.sagewire.rangewater.data

/** Rejects malformed archives before the live database transaction begins. */
object RangeWaterBackupValidator {
    fun validate(data: RangeWaterBackupData) {
        val waterIds = uniquePositiveIds("water point", data.waterPoints.map { it.id })
        val pastureIds = uniquePositiveIds("pasture", data.pastures.map { it.id })
        val junctionIds = uniquePositiveIds("fence junction", data.junctions.map { it.id })
        val vertexIds = uniquePositiveIds("pasture vertex", data.vertices.map { it.id })
        val gateIds = uniquePositiveIds("gate", data.gates.map { it.id })
        val herdIds = uniquePositiveIds("herd", data.herds.map { it.id })
        uniquePositiveIds("movement", data.movements.map { it.id })

        data.waterPoints.forEach { point ->
            requireCoordinate(point.latitude, point.longitude, "Water point #${point.id}")
            require(point.name.isNotBlank()) { "Water point #${point.id} has no name" }
        }
        data.junctions.forEach { junction ->
            requireCoordinate(junction.latitude, junction.longitude, "Fence junction #${junction.id}")
            require(junction.elevationMeters?.isFinite() != false) { "Fence junction #${junction.id} has invalid elevation" }
            require(junction.verticalAccuracyMeters?.let { it.isFinite() && it >= 0.0 } != false) {
                "Fence junction #${junction.id} has invalid vertical accuracy"
            }
        }

        val vertexPairs = mutableSetOf<Pair<Long, Int>>()
        val pastureJunctionPairs = mutableSetOf<Pair<Long, Long>>()
        data.vertices.forEach { vertex ->
            require(vertex.pastureId in pastureIds) { "Pasture vertex #${vertex.id} references a missing pasture" }
            require(vertex.junctionId in junctionIds) { "Pasture vertex #${vertex.id} references a missing fence junction" }
            require(vertex.sequence >= 0) { "Pasture vertex #${vertex.id} has a negative sequence" }
            require(vertexPairs.add(vertex.pastureId to vertex.sequence)) { "A pasture contains duplicate vertex sequences" }
            require(pastureJunctionPairs.add(vertex.pastureId to vertex.junctionId)) {
                "A pasture references the same fence junction more than once"
            }
        }
        data.vertices.groupBy { it.pastureId }.forEach { (pastureId, vertices) ->
            val sequences = vertices.map { it.sequence }.sorted()
            require(sequences == sequences.indices.toList()) { "Pasture #$pastureId has a broken vertex sequence" }
        }

        val assignmentKeys = mutableSetOf<Pair<Long, Long>>()
        data.assignments.forEach { assignment ->
            require(assignment.waterPointId in waterIds) { "Water assignment references a missing water point" }
            require(assignment.pastureId in pastureIds) { "Water assignment references a missing pasture" }
            require(assignmentKeys.add(assignment.waterPointId to assignment.pastureId)) { "Backup contains a duplicate water assignment" }
        }

        val gateKeys = mutableSetOf<Triple<Long, Long, Double>>()
        data.gates.forEach { gate ->
            require(gate.junctionAId in junctionIds && gate.junctionBId in junctionIds) {
                "Gate #${gate.id} references a missing fence junction"
            }
            require(gate.junctionAId < gate.junctionBId) { "Gate #${gate.id} has non-canonical anchors" }
            require(gate.segmentRatio.isFinite() && gate.segmentRatio in 0.0..1.0) { "Gate #${gate.id} has an invalid position" }
            require(gate.widthMeters.isFinite() && gate.widthMeters in GateEntity.MIN_WIDTH_METERS..GateEntity.MAX_WIDTH_METERS) {
                "Gate #${gate.id} has an invalid width"
            }
            require(gate.gateType in GateEntity.VALID_TYPES) { "Gate #${gate.id} has an unsupported type" }
            require(gate.status in GateEntity.VALID_STATUSES) { "Gate #${gate.id} has an unsupported status" }
            require(gateKeys.add(Triple(gate.junctionAId, gate.junctionBId, gate.segmentRatio))) {
                "Backup contains duplicate gate positions"
            }
        }

        val herdNames = mutableSetOf<String>()
        data.herds.forEach { herd ->
            require(herd.name.isNotBlank()) { "Herd #${herd.id} has no name" }
            require(herdNames.add(herd.name.lowercase())) { "Backup contains duplicate herd names" }
            require(herd.quantity > 0) { "Herd #${herd.id} has an invalid quantity" }
            require(herd.averageWeightLbs?.let { it.isFinite() && it > 0.0 } != false) { "Herd #${herd.id} has an invalid average weight" }
            require(herd.markerColorHex.matches(Regex("^#[0-9A-Fa-f]{6}$"))) { "Herd #${herd.id} has an invalid marker color" }
            require(herd.shortMarkerLabel == null || herd.shortMarkerLabel.length <= 4) { "Herd #${herd.id} has an invalid short label" }
            if (herd.locationKind == HerdLocationKind.PASTURE) {
                require(herd.currentPastureId in pastureIds) { "Herd #${herd.id} references a missing current pasture" }
            } else {
                require(herd.currentPastureId == null) { "Herd #${herd.id} has an inconsistent location" }
            }
        }

        data.movements.forEach { movement ->
            require(movement.herdId in herdIds) { "Movement #${movement.id} references a missing herd" }
            require(movement.originPastureId == null || movement.originPastureId in pastureIds) {
                "Movement #${movement.id} references a missing origin pasture"
            }
            require(movement.destinationPastureId == null || movement.destinationPastureId in pastureIds) {
                "Movement #${movement.id} references a missing destination pasture"
            }
            require(movement.gateId == null || movement.gateId in gateIds) { "Movement #${movement.id} references a missing gate" }
            require(movement.quantity > 0) { "Movement #${movement.id} has an invalid quantity" }
            require(movement.originNameSnapshot.isNotBlank() && movement.destinationNameSnapshot.isNotBlank()) {
                "Movement #${movement.id} is missing an audit snapshot"
            }
        }
    }

    private fun uniquePositiveIds(label: String, ids: List<Long>): Set<Long> {
        require(ids.all { it > 0 }) { "Backup contains an invalid $label ID" }
        require(ids.toSet().size == ids.size) { "Backup contains duplicate $label IDs" }
        return ids.toSet()
    }

    private fun requireCoordinate(latitude: Double, longitude: Double, label: String) {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "$label has an invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "$label has an invalid longitude" }
    }
}
