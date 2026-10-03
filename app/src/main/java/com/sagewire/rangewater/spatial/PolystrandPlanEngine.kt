package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PaddockDividerEntity
import com.sagewire.rangewater.data.PaddockDividerNodeRefEntity
import com.sagewire.rangewater.data.PaddockPlanEntity
import com.sagewire.rangewater.data.PaddockPlanNodeEntity
import com.sagewire.rangewater.data.PaddockPlanNodeKind
import com.sagewire.rangewater.data.PaddockRegionLabelEntity
import com.sagewire.rangewater.data.PastureCoordinate
import com.sagewire.rangewater.data.PastureWithVertices
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.operation.polygonize.Polygonizer
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.locationtech.jts.precision.GeometryPrecisionReducer
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

data class ResolvedPaddockNode(
    val node: PaddockPlanNodeEntity,
    val coordinate: PastureCoordinate
)

data class ResolvedPaddockDivider(
    val divider: PaddockDividerEntity,
    val nodeIds: List<Long>,
    val coordinates: List<PastureCoordinate>
)

data class PaddockDerivedRegion(
    val key: String,
    val label: String,
    val coordinates: List<PastureCoordinate>,
    val acreage: Double
)

data class PolystrandPlanResult(
    val plan: PaddockPlanEntity,
    val nodes: List<ResolvedPaddockNode>,
    val dividers: List<ResolvedPaddockDivider>,
    val regions: List<PaddockDerivedRegion>,
    val parentAcreage: Double
)

/** Validates all active dividers as one atomic temporary-fence topology. */
object PolystrandPlanEngine {
    private const val RECONCILIATION_TOLERANCE = 0.005
    private const val MIN_REGION_SQUARE_METERS = 1.0
    private const val EPSILON = 1e-9
    private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
    // Field boundaries and boundary-anchor interpolation arrive as floating-point GPS
    // coordinates.  Two values that describe the same boundary point can differ below
    // map precision, which leaves a divider microscopically disconnected from the ring
    // when JTS nodes the linework.  A centimetre-scale fixed grid makes those shared
    // points identical before polygonization without changing any meaningful acreage.
    private val nodingPrecisionModel = PrecisionModel(100_000_000.0)

    fun resolve(
        plan: PaddockPlanEntity,
        nodes: List<PaddockPlanNodeEntity>,
        dividers: List<PaddockDividerEntity>,
        refs: List<PaddockDividerNodeRefEntity>,
        labels: List<PaddockRegionLabelEntity>,
        pasture: PastureWithVertices
    ): PolystrandPlanResult {
        require(plan.pastureId == pasture.pasture.id) { "Paddock plan belongs to another pasture" }
        val activeDividers = dividers.filter { it.archivedAt == null }.sortedBy { it.sequence }
        require(activeDividers.isNotEmpty()) { "An active paddock plan needs at least one divider" }
        require(activeDividers.map { it.sequence } == activeDividers.indices.toList()) {
            "Active divider sequence must be contiguous"
        }
        val nodeMap = nodes.associateBy { it.id }
        require(nodeMap.size == nodes.size && nodes.all { it.planId == plan.id }) {
            "Paddock plan contains invalid nodes"
        }
        val resolvedNodes = nodes.associate { node ->
            node.id to ResolvedPaddockNode(node, resolveNode(node, pasture))
        }
        val parentCoordinates = pasture.vertices.sortedBy { it.sequence }.map {
            PastureCoordinate(it.latitude, it.longitude, junctionId = it.junctionId)
        }
        val parent = polygon(parentCoordinates)
        require(parent.isValid && parent.area > 0.0) { "Parent pasture geometry is invalid" }

        val resolvedDividers = activeDividers.map { divider ->
            val orderedRefs = refs.filter { it.dividerId == divider.id }.sortedBy { it.sequence }
            require(orderedRefs.size >= 2 && orderedRefs.map { it.sequence } == orderedRefs.indices.toList()) {
                "Divider #${divider.id} has a broken node sequence"
            }
            require(orderedRefs.map { it.nodeId }.distinct().size == orderedRefs.size) {
                "Divider #${divider.id} references the same node twice"
            }
            val resolved = orderedRefs.map { ref ->
                resolvedNodes[ref.nodeId]
                    ?: throw IllegalArgumentException("Divider #${divider.id} references a missing node")
            }
            require(resolved.first().node.nodeKind != PaddockPlanNodeKind.INTERIOR_WAYPOINT &&
                resolved.last().node.nodeKind != PaddockPlanNodeKind.INTERIOR_WAYPOINT
            ) { "A private waypoint cannot be a divider endpoint" }
            resolved.drop(1).dropLast(1).forEach { point ->
                require(point.node.nodeKind != PaddockPlanNodeKind.BOUNDARY_ANCHOR) {
                    "A boundary anchor cannot be an interior path point"
                }
            }
            val coordinates = resolved.map { it.coordinate }
            val line = line(coordinates)
            require(line.isSimple && line.length > EPSILON) { "Divider #${divider.id} self-crosses or has zero length" }
            require(parent.covers(line)) { "Divider #${divider.id} leaves the parent pasture" }
            val boundaryIntersection = line.intersection(parent.boundary)
            require(boundaryIntersection.dimension < 1) { "Divider #${divider.id} overlaps the pasture boundary" }
            ResolvedPaddockDivider(divider, resolved.map { it.node.id }, coordinates)
        }

        resolvedDividers.indices.forEach { leftIndex ->
            for (rightIndex in leftIndex + 1 until resolvedDividers.size) {
                validateDividerIntersection(
                    resolvedDividers[leftIndex],
                    resolvedDividers[rightIndex],
                    nodeMap
                )
            }
        }

        val precisionReducer = GeometryPrecisionReducer(nodingPrecisionModel).apply {
            setChangePrecisionModel(true)
            setRemoveCollapsedComponents(true)
        }
        val linework = mutableListOf<Geometry>(precisionReducer.reduce(parent.boundary))
        linework += resolvedDividers.map { precisionReducer.reduce(line(it.coordinates)) }
        val noded = UnaryUnionOp.union(linework)
        // Keep every face created by the noded boundary plus divider linework.
        // `extractOnlyPolygonal=true` intentionally drops adjacent faces to produce
        // an edge-disjoint subset, which is the opposite of a paddock partition.
        val polygonizer = Polygonizer().apply { add(noded) }
        @Suppress("UNCHECKED_CAST")
        val candidatePolygons = polygonizer.polygons as Collection<Polygon>
        val pieces = candidatePolygons
            .filter { it.area > 0.0 && parent.covers(it.interiorPoint) }
            .sortedWith(compareBy<Polygon>({ it.centroid.y }, { it.centroid.x }, { it.area }))
        require(pieces.size >= 2) { "Combined dividers do not create valid planning regions" }

        val parentAcres = AcreageCalculator.calculateAcres(parentCoordinates)
        val minimumAcres = AcreageCalculator.squareMetersToAcres(MIN_REGION_SQUARE_METERS)
        val labelMap = labels.associateBy { it.regionKey }
        val legacyLabels = labels
            .filter { it.regionKey.startsWith("legacy-side-") }
            .sortedBy { it.regionKey }
        val regions = pieces.mapIndexed { index, piece ->
            require(parent.covers(piece) && piece.isValid) { "A derived region is invalid" }
            val coordinates = piece.exteriorRing.coordinates.dropLast(1).map {
                PastureCoordinate(latitude = it.y, longitude = it.x)
            }
            val acreage = AcreageCalculator.calculateAcres(coordinates)
            require(acreage >= minimumAcres) { "A derived region is too small for reliable map geometry" }
            val key = regionKey(coordinates)
            PaddockDerivedRegion(
                key = key,
                label = labelMap[key]?.label ?: legacyLabels.getOrNull(index)?.label ?: "Region ${index + 1}",
                coordinates = coordinates,
                acreage = acreage
            )
        }
        val difference = abs(regions.sumOf { it.acreage } - parentAcres)
        require(parentAcres > 0.0 && difference / parentAcres <= RECONCILIATION_TOLERANCE) {
            "Paddock region acreages do not reconcile with the parent pasture"
        }
        return PolystrandPlanResult(
            plan = plan,
            nodes = resolvedNodes.values.sortedBy { it.node.id },
            dividers = resolvedDividers,
            regions = regions,
            parentAcreage = parentAcres
        )
    }

    fun resolveNode(node: PaddockPlanNodeEntity, pasture: PastureWithVertices): PastureCoordinate =
        when (node.nodeKind) {
            PaddockPlanNodeKind.BOUNDARY_ANCHOR -> PaddockSplitEngine.resolveAnchor(
                pasture,
                requireNotNull(node.boundaryJunctionAId),
                requireNotNull(node.boundaryJunctionBId),
                requireNotNull(node.boundarySegmentRatio)
            ).coordinate
            PaddockPlanNodeKind.INTERIOR_JUNCTION,
            PaddockPlanNodeKind.INTERIOR_WAYPOINT -> PastureCoordinate(
                latitude = requireNotNull(node.latitude),
                longitude = requireNotNull(node.longitude)
            )
        }

    private fun validateDividerIntersection(
        left: ResolvedPaddockDivider,
        right: ResolvedPaddockDivider,
        nodes: Map<Long, PaddockPlanNodeEntity>
    ) {
        val intersection = line(left.coordinates).intersection(line(right.coordinates))
        if (intersection.isEmpty) return
        require(intersection.dimension == 0) { "Active dividers cannot overlap" }
        val sharedNodeIds = left.nodeIds.toSet().intersect(right.nodeIds.toSet()).filter { id ->
            nodes.getValue(id).nodeKind != PaddockPlanNodeKind.INTERIOR_WAYPOINT
        }
        val allowedCoordinates = sharedNodeIds.map { id ->
            val index = left.nodeIds.indexOf(id)
            left.coordinates[index]
        }
        intersection.coordinates.forEach { point ->
            require(allowedCoordinates.any { allowed ->
                abs(allowed.longitude - point.x) <= EPSILON && abs(allowed.latitude - point.y) <= EPSILON
            }) { "Divider crossing requires an explicit shared node" }
        }
    }

    private fun line(coordinates: List<PastureCoordinate>): LineString = geometryFactory.createLineString(
        coordinates.map { Coordinate(it.longitude, it.latitude) }.toTypedArray()
    )

    private fun polygon(coordinates: List<PastureCoordinate>): Polygon {
        require(coordinates.size >= 3) { "Pasture requires at least three corners" }
        val ring = coordinates.map { Coordinate(it.longitude, it.latitude) }.toMutableList()
        ring += ring.first().copy()
        return geometryFactory.createPolygon(ring.toTypedArray())
    }

    internal fun regionKey(coordinates: List<PastureCoordinate>): String {
        val tokens = coordinates.map {
            String.format(Locale.US, "%.8f,%.8f", it.latitude, it.longitude)
        }
        val forward = canonicalRotation(tokens)
        val reverse = canonicalRotation(tokens.asReversed())
        val canonical = minOf(forward, reverse)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    }

    private fun canonicalRotation(tokens: List<String>): String = tokens.indices
        .map { offset -> tokens.drop(offset) + tokens.take(offset) }
        .minOf { it.joinToString("|") }
}
