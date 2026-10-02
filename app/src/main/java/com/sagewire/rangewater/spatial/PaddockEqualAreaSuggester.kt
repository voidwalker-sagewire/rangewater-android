package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureWithVertices
import kotlin.math.abs

/** Deterministic planning aid that ranks valid midpoint-to-midpoint straight dividers by acreage balance. */
object PaddockEqualAreaSuggester {
    fun suggest(pasture: PastureWithVertices): PaddockSplitResult {
        val ordered = pasture.vertices.sortedBy { it.sequence }
        require(ordered.size >= 3) { "Pasture requires at least three corners" }
        val anchors = ordered.indices.map { index ->
            val first = ordered[index].junctionId
            val second = ordered[(index + 1) % ordered.size].junctionId
            PaddockSplitEngine.resolveAnchor(
                pasture,
                minOf(first, second),
                maxOf(first, second),
                0.5
            )
        }
        val candidates = buildList {
            anchors.indices.forEach { firstIndex ->
                for (secondIndex in firstIndex + 1 until anchors.size) {
                    runCatching {
                        PaddockSplitEngine.split(
                            pasture,
                            anchors[firstIndex],
                            anchors[secondIndex]
                        )
                    }.getOrNull()?.let(::add)
                }
            }
        }
        return candidates.minWithOrNull(
            compareBy<PaddockSplitResult> { abs(it.sideAAcres - it.sideBAcres) }
                .thenBy { it.start.junctionAId }
                .thenBy { it.start.junctionBId }
                .thenBy { it.end.junctionAId }
                .thenBy { it.end.junctionBId }
        ) ?: throw IllegalArgumentException("No valid straight divider suggestion is available for this pasture")
    }
}
