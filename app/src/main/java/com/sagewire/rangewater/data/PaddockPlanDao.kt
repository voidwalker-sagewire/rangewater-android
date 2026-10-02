package com.sagewire.rangewater.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sagewire.rangewater.spatial.PaddockBoundaryAnchor
import com.sagewire.rangewater.spatial.PolystrandPlanEngine
import com.sagewire.rangewater.spatial.PolystrandPlanResult
import kotlinx.coroutines.flow.Flow

data class PaddockPlanGraph(
    val plan: PaddockPlanEntity,
    val nodes: List<PaddockPlanNodeEntity>,
    val dividers: List<PaddockDividerEntity>,
    val nodeRefs: List<PaddockDividerNodeRefEntity>,
    val regionLabels: List<PaddockRegionLabelEntity>
)

data class PaddockNodeDraft(
    val existingNodeId: Long? = null,
    val nodeKind: PaddockPlanNodeKind,
    val boundaryJunctionAId: Long? = null,
    val boundaryJunctionBId: Long? = null,
    val boundarySegmentRatio: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Dao
interface PaddockPlanDao {
    @Query("SELECT * FROM paddock_plans ORDER BY createdAt, id")
    fun observePlans(): Flow<List<PaddockPlanEntity>>

    @Query("SELECT * FROM paddock_plans WHERE archivedAt IS NULL ORDER BY createdAt, id")
    fun observeActivePlans(): Flow<List<PaddockPlanEntity>>

    @Query("SELECT * FROM paddock_plan_nodes ORDER BY id")
    fun observeAllNodes(): Flow<List<PaddockPlanNodeEntity>>

    @Query("SELECT * FROM paddock_dividers ORDER BY planId, sequence, id")
    fun observeAllDividers(): Flow<List<PaddockDividerEntity>>

    @Query("SELECT * FROM paddock_divider_node_refs ORDER BY dividerId, sequence")
    fun observeAllNodeRefs(): Flow<List<PaddockDividerNodeRefEntity>>

    @Query("SELECT * FROM paddock_region_labels ORDER BY planId, regionKey")
    fun observeAllRegionLabels(): Flow<List<PaddockRegionLabelEntity>>

    @Query("SELECT * FROM paddock_plans WHERE id = :id")
    suspend fun getPlan(id: Long): PaddockPlanEntity?

    @Query("SELECT * FROM paddock_plans WHERE pastureId = :pastureId AND archivedAt IS NULL LIMIT 1")
    suspend fun activeForPasture(pastureId: Long): PaddockPlanEntity?

    @Query("SELECT * FROM paddock_plan_nodes WHERE planId = :planId ORDER BY id")
    suspend fun nodes(planId: Long): List<PaddockPlanNodeEntity>

    @Query("SELECT * FROM paddock_dividers WHERE planId = :planId ORDER BY sequence, id")
    suspend fun dividers(planId: Long): List<PaddockDividerEntity>

    @Query("SELECT r.* FROM paddock_divider_node_refs r INNER JOIN paddock_dividers d ON d.id = r.dividerId WHERE d.planId = :planId ORDER BY d.sequence, r.sequence")
    suspend fun nodeRefs(planId: Long): List<PaddockDividerNodeRefEntity>

    @Query("SELECT * FROM paddock_region_labels WHERE planId = :planId ORDER BY regionKey")
    suspend fun regionLabels(planId: Long): List<PaddockRegionLabelEntity>

    @Query("SELECT COUNT(*) FROM paddock_divider_node_refs WHERE nodeId = :nodeId")
    suspend fun referenceCount(nodeId: Long): Int

    @Transaction
    @Query("SELECT * FROM pastures WHERE id = :pastureId LIMIT 1")
    suspend fun pastureWithVertices(pastureId: Long): PastureWithVertices?

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPlan(row: PaddockPlanEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNode(row: PaddockPlanNodeEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertDivider(row: PaddockDividerEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRefs(rows: List<PaddockDividerNodeRefEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLabels(rows: List<PaddockRegionLabelEntity>)
    @Update suspend fun updatePlan(row: PaddockPlanEntity)
    @Update suspend fun updateNode(row: PaddockPlanNodeEntity)
    @Update suspend fun updateDivider(row: PaddockDividerEntity)

    @Query("DELETE FROM paddock_region_labels WHERE planId = :planId") suspend fun deleteLabels(planId: Long)
    @Query("UPDATE paddock_region_labels SET label = :label WHERE planId = :planId AND regionKey = :regionKey")
    suspend fun renameRegionLabelRaw(planId: Long, regionKey: String, label: String): Int
    @Query("DELETE FROM paddock_divider_node_refs WHERE dividerId = :dividerId") suspend fun deleteRefs(dividerId: Long)
    @Query("DELETE FROM paddock_dividers WHERE id = :dividerId") suspend fun deleteDividerRaw(dividerId: Long)
    @Query("DELETE FROM paddock_plan_nodes WHERE id = :nodeId") suspend fun deleteNodeRaw(nodeId: Long)
    @Query("DELETE FROM paddock_plans WHERE id = :planId") suspend fun deletePlanRaw(planId: Long)
    @Query("UPDATE paddock_plans SET archivedAt = :now, updatedAt = :now WHERE id = :planId") suspend fun archivePlanRaw(planId: Long, now: Long)
    @Query("UPDATE paddock_plans SET archivedAt = NULL, updatedAt = :now WHERE id = :planId") suspend fun reactivatePlanRaw(planId: Long, now: Long)
    @Query("UPDATE paddock_dividers SET archivedAt = :now, updatedAt = :now WHERE planId = :planId AND archivedAt IS NULL") suspend fun archivePlanDividersRaw(planId: Long, now: Long)
    @Query("UPDATE paddock_dividers SET archivedAt = NULL, updatedAt = :now WHERE planId = :planId AND archivedAt = :planArchivedAt")
    suspend fun reactivatePlanDividersRaw(planId: Long, planArchivedAt: Long, now: Long)
    @Query("UPDATE paddock_dividers SET archivedAt = :archivedAt, updatedAt = :now WHERE id = :dividerId") suspend fun setDividerArchiveRaw(dividerId: Long, archivedAt: Long?, now: Long)
    @Query("UPDATE paddock_dividers SET sequence = :sequence, updatedAt = :now WHERE id = :dividerId") suspend fun setDividerSequenceRaw(dividerId: Long, sequence: Int, now: Long)

    @Transaction
    suspend fun graph(planId: Long): PaddockPlanGraph {
        val plan = getPlan(planId) ?: throw IllegalArgumentException("Paddock plan #$planId not found")
        return PaddockPlanGraph(plan, nodes(planId), dividers(planId), nodeRefs(planId), regionLabels(planId))
    }

    @Transaction
    suspend fun deleteOrphanNode(nodeId: Long) {
        check(referenceCount(nodeId) == 0) { "A referenced polystrand node cannot be deleted" }
        deleteNodeRaw(nodeId)
    }

    @Transaction
    suspend fun resolve(planId: Long): PolystrandPlanResult {
        val graph = graph(planId)
        val pasture = pastureWithVertices(graph.plan.pastureId)
            ?: throw IllegalArgumentException("Pasture #${graph.plan.pastureId} not found")
        return PolystrandPlanEngine.resolve(
            graph.plan,
            graph.nodes,
            graph.dividers,
            graph.nodeRefs,
            graph.regionLabels,
            pasture
        )
    }

    /** Compatibility entry for the accepted 1.2 endpoint workflow, now stored in schema 9. */
    @Transaction
    suspend fun create(
        pastureId: Long,
        name: String,
        sideALabel: String,
        sideBLabel: String,
        start: PaddockBoundaryAnchor,
        end: PaddockBoundaryAnchor,
        now: Long = System.currentTimeMillis()
    ): Long {
        check(activeForPasture(pastureId) == null) { "This pasture already has an active polystrand plan" }
        val planId = insertPlan(
            PaddockPlanEntity(
                pastureId = pastureId,
                name = normalizedLabel(name, "Polystrand Plan"),
                createdAt = now,
                updatedAt = now
            )
        )
        insertStraightDivider(planId, normalizedLabel(name, "Divider 1"), 0, start, end, now)
        reconcileLabels(planId, listOf(sideALabel, sideBLabel))
        return planId
    }

    @Transaction
    suspend fun addStraightDivider(
        planId: Long,
        name: String,
        start: PaddockBoundaryAnchor,
        end: PaddockBoundaryAnchor,
        now: Long = System.currentTimeMillis()
    ): Long {
        val plan = getPlan(planId) ?: throw IllegalArgumentException("Paddock plan #$planId not found")
        check(plan.archivedAt == null) { "Archived plans cannot be edited" }
        val existingDividers = dividers(planId)
        val sequence = existingDividers.maxOfOrNull { it.sequence }?.plus(1) ?: 0
        val activeOrdinal = existingDividers.count { it.archivedAt == null } + 1
        val dividerId = insertStraightDivider(planId, normalizedLabel(name, "Divider $activeOrdinal"), sequence, start, end, now)
        normalizeDividerSequences(planId, now)
        reconcileLabels(planId, emptyList())
        updatePlan(plan.copy(updatedAt = now))
        return dividerId
    }

    @Transaction
    suspend fun addDivider(
        planId: Long,
        name: String,
        points: List<PaddockNodeDraft>,
        now: Long = System.currentTimeMillis()
    ): Long {
        val plan = getPlan(planId) ?: throw IllegalArgumentException("Paddock plan #$planId not found")
        check(plan.archivedAt == null) { "Archived plans cannot be edited" }
        require(points.size >= 2) { "A divider requires at least two points" }
        val sequence = dividers(planId).maxOfOrNull { it.sequence }?.plus(1) ?: 0
        val nodeIds = points.map { draft ->
            draft.existingNodeId?.let { existingId ->
                val existing = nodes(planId).firstOrNull { it.id == existingId }
                    ?: throw IllegalArgumentException("Shared node #$existingId is not part of this plan")
                check(existing.nodeKind != PaddockPlanNodeKind.INTERIOR_WAYPOINT) {
                    "A private waypoint must be explicitly promoted before another divider can connect"
                }
                existingId
            } ?: run {
                require(draft.nodeKind != PaddockPlanNodeKind.INTERIOR_JUNCTION) {
                    "A shared junction must be created through the explicit Connect/Share operation"
                }
                insertNode(draft.toEntity(planId, now))
            }
        }
        val dividerId = insertDivider(
            PaddockDividerEntity(
                planId = planId,
                name = normalizedLabel(name, "Divider ${sequence + 1}"),
                sequence = sequence,
                createdAt = now,
                updatedAt = now
            )
        )
        insertRefs(nodeIds.mapIndexed { index, nodeId ->
            PaddockDividerNodeRefEntity(dividerId, index, nodeId)
        })
        normalizeDividerSequences(planId, now)
        reconcileLabels(planId, emptyList())
        updatePlan(plan.copy(updatedAt = now))
        return dividerId
    }

    /**
     * Explicitly promotes one private waypoint and connects a new divider to it.
     * The promotion, divider insert, references, and whole-plan validation share one
     * transaction so a rejected connection leaves the saved plan unchanged.
     */
    @Transaction
    suspend fun connectWaypointWithNewDivider(
        waypointNodeId: Long,
        dividerName: String,
        points: List<PaddockNodeDraft>,
        now: Long = System.currentTimeMillis()
    ): Long {
        val waypoint = nodesForNode(waypointNodeId)
        check(waypoint.nodeKind == PaddockPlanNodeKind.INTERIOR_WAYPOINT) {
            "Only a private waypoint can be promoted"
        }
        require(points.count { it.existingNodeId == waypointNodeId } == 1) {
            "The new divider must reference the selected waypoint exactly once"
        }
        updateNode(waypoint.copy(nodeKind = PaddockPlanNodeKind.INTERIOR_JUNCTION, updatedAt = now))
        return addDivider(waypoint.planId, dividerName, points, now)
    }

    @Transaction
    suspend fun createWithDivider(
        pastureId: Long,
        planName: String,
        dividerName: String,
        points: List<PaddockNodeDraft>,
        now: Long = System.currentTimeMillis()
    ): Long {
        check(activeForPasture(pastureId) == null) { "This pasture already has an active polystrand plan" }
        val planId = insertPlan(
            PaddockPlanEntity(
                pastureId = pastureId,
                name = normalizedLabel(planName, "Polystrand Plan"),
                createdAt = now,
                updatedAt = now
            )
        )
        addDivider(planId, dividerName, points, now)
        return planId
    }

    @Transaction
    suspend fun moveNode(node: PaddockPlanNodeEntity, now: Long = System.currentTimeMillis()) {
        val current = nodes(node.planId).firstOrNull { it.id == node.id }
            ?: throw IllegalArgumentException("Paddock node #${node.id} not found")
        check(current.nodeKind == node.nodeKind) { "Use the explicit promotion or demotion operation to change node kind" }
        updateNode(node.copy(createdAt = current.createdAt, updatedAt = now))
        reconcileLabels(node.planId, emptyList())
    }

    @Transaction
    suspend fun replaceDividerPath(
        dividerId: Long,
        points: List<PaddockNodeDraft>,
        now: Long = System.currentTimeMillis()
    ) {
        val divider = dividersForNodeMutation(dividerId)
        check(divider.archivedAt == null) { "Archived dividers cannot be edited" }
        val plan = getPlan(divider.planId) ?: throw IllegalArgumentException("Paddock plan #${divider.planId} not found")
        check(plan.archivedAt == null) { "Archived plans cannot be edited" }
        require(points.size >= 2) { "A divider requires at least two points" }
        val existingIds = points.mapNotNull { it.existingNodeId }
        require(existingIds.distinct().size == existingIds.size) { "A divider cannot reference one node more than once" }

        val planNodes = nodes(divider.planId).associateBy { it.id }
        val replacementIds = points.map { draft ->
            draft.existingNodeId?.let { nodeId ->
                val current = planNodes[nodeId]
                    ?: throw IllegalArgumentException("Paddock node #$nodeId is not part of this plan")
                check(current.nodeKind == draft.nodeKind) {
                    "Use the explicit promotion or demotion operation to change node kind"
                }
                updateNode(
                    draft.toEntity(divider.planId, now).copy(
                        id = current.id,
                        createdAt = current.createdAt
                    )
                )
                nodeId
            } ?: run {
                require(draft.nodeKind == PaddockPlanNodeKind.INTERIOR_WAYPOINT) {
                    "Only a private interior waypoint can be added while editing a saved divider"
                }
                insertNode(draft.toEntity(divider.planId, now))
            }
        }

        val oldNodeIds = nodeRefs(divider.planId)
            .filter { it.dividerId == dividerId }
            .map { it.nodeId }
        replaceDividerRefs(dividerId, replacementIds)
        oldNodeIds.forEach { nodeId -> if (referenceCount(nodeId) == 0) deleteNodeRaw(nodeId) }
        updateDivider(divider.copy(updatedAt = now))
        updatePlan(plan.copy(updatedAt = now))
        reconcileLabels(divider.planId, emptyList())
    }

    @Transaction
    suspend fun promoteWaypointAndConnect(
        nodeId: Long,
        connectingDividerId: Long,
        insertionSequence: Int,
        now: Long = System.currentTimeMillis()
    ) {
        val divider = dividersForNodeMutation(connectingDividerId)
        val node = nodes(divider.planId).firstOrNull { it.id == nodeId }
            ?: throw IllegalArgumentException("Paddock node #$nodeId not found")
        check(node.nodeKind == PaddockPlanNodeKind.INTERIOR_WAYPOINT) { "Only a private waypoint can be promoted" }
        val existingRefs = nodeRefs(divider.planId).filter { it.dividerId == connectingDividerId }.sortedBy { it.sequence }
        require(insertionSequence in 1 until existingRefs.size) { "Shared junction must be inserted inside the connecting path" }
        check(existingRefs.none { it.nodeId == nodeId }) { "Divider already references this node" }
        updateNode(node.copy(nodeKind = PaddockPlanNodeKind.INTERIOR_JUNCTION, updatedAt = now))
        replaceDividerRefs(
            connectingDividerId,
            existingRefs.map { it.nodeId }.toMutableList().apply { add(insertionSequence, nodeId) }
        )
        reconcileLabels(divider.planId, emptyList())
    }

    @Transaction
    suspend fun unlinkNodeFromDivider(nodeId: Long, dividerId: Long) {
        val divider = dividersForNodeMutation(dividerId)
        val remaining = nodeRefs(divider.planId).filter { it.dividerId == dividerId }
            .sortedBy { it.sequence }.map { it.nodeId }.toMutableList()
        check(remaining.remove(nodeId)) { "Divider does not reference this node" }
        require(remaining.size >= 2) { "Unlinking would leave fewer than two path points" }
        replaceDividerRefs(dividerId, remaining)
        reconcileLabels(divider.planId, emptyList())
    }

    @Transaction
    suspend fun demoteJunction(nodeId: Long, now: Long = System.currentTimeMillis()) {
        val node = nodesForNode(nodeId)
        check(node.nodeKind == PaddockPlanNodeKind.INTERIOR_JUNCTION) { "Only an interior junction can be demoted" }
        val dividerCount = nodeRefs(node.planId).filter { it.nodeId == nodeId }.map { it.dividerId }.distinct().size
        check(dividerCount <= 1) { "A junction shared by multiple dividers cannot be demoted" }
        updateNode(node.copy(nodeKind = PaddockPlanNodeKind.INTERIOR_WAYPOINT, updatedAt = now))
        reconcileLabels(node.planId, emptyList())
    }

    @Transaction
    suspend fun archiveDivider(dividerId: Long, now: Long = System.currentTimeMillis()) {
        val divider = dividersForNodeMutation(dividerId)
        check(divider.archivedAt == null) { "Divider is already archived" }
        check(dividers(divider.planId).count { it.archivedAt == null } > 1) {
            "The final active divider can only be archived with the complete plan"
        }
        setDividerArchiveRaw(dividerId, now, now)
        normalizeDividerSequences(divider.planId, now)
        reconcileLabels(divider.planId, emptyList())
    }

    @Transaction
    suspend fun reactivateDivider(dividerId: Long, now: Long = System.currentTimeMillis()) {
        val divider = dividersForNodeMutation(dividerId)
        check(divider.archivedAt != null) { "Divider is already active" }
        setDividerArchiveRaw(dividerId, null, now)
        normalizeDividerSequences(divider.planId, now)
        reconcileLabels(divider.planId, emptyList())
    }

    @Transaction
    suspend fun renameDivider(dividerId: Long, name: String, now: Long = System.currentTimeMillis()) {
        val divider = dividersForNodeMutation(dividerId)
        updateDivider(divider.copy(name = normalizedLabel(name, divider.name), updatedAt = now))
    }

    @Transaction
    suspend fun renameRegionLabel(
        planId: Long,
        regionKey: String,
        label: String,
        now: Long = System.currentTimeMillis()
    ) {
        val plan = getPlan(planId) ?: throw IllegalArgumentException("Paddock plan #$planId not found")
        check(plan.archivedAt == null) { "Archived plans cannot be edited" }
        val normalized = label.trim()
        require(normalized.isNotEmpty()) { "Region label cannot be blank" }
        check(renameRegionLabelRaw(planId, regionKey, normalized) == 1) { "Planning region no longer exists" }
        updatePlan(plan.copy(updatedAt = now))
    }

    @Transaction
    suspend fun deleteDivider(dividerId: Long, now: Long = System.currentTimeMillis()) {
        val divider = dividersForNodeMutation(dividerId)
        check(dividers(divider.planId).count { it.archivedAt == null } > 1 || divider.archivedAt != null) {
            "Deleting the final active divider would invalidate the active plan"
        }
        val oldNodeIds = nodeRefs(divider.planId).filter { it.dividerId == dividerId }.map { it.nodeId }
        deleteDividerRaw(dividerId)
        oldNodeIds.forEach { nodeId -> if (referenceCount(nodeId) == 0) deleteNodeRaw(nodeId) }
        normalizeDividerSequences(divider.planId, now)
        reconcileLabels(divider.planId, emptyList())
    }

    @Transaction
    suspend fun update(
        id: Long,
        name: String,
        sideALabel: String,
        sideBLabel: String,
        start: PaddockBoundaryAnchor,
        end: PaddockBoundaryAnchor,
        now: Long = System.currentTimeMillis()
    ) {
        val plan = getPlan(id) ?: throw IllegalArgumentException("Paddock plan #$id not found")
        check(plan.archivedAt == null) { "Archived plans cannot be edited" }
        val target = dividers(id).filter { it.archivedAt == null }.minByOrNull { it.sequence }
            ?: throw IllegalStateException("Paddock plan has no active divider")
        val oldNodeIds = nodeRefs(id).filter { it.dividerId == target.id }.map { it.nodeId }
        deleteRefs(target.id)
        oldNodeIds.forEach { nodeId -> if (referenceCount(nodeId) == 0) deleteNodeRaw(nodeId) }
        val startId = insertNode(start.toEntity(id, now))
        val endId = insertNode(end.toEntity(id, now))
        updateDivider(target.copy(name = normalizedLabel(name, target.name), updatedAt = now))
        insertRefs(
            listOf(
                PaddockDividerNodeRefEntity(target.id, 0, startId),
                PaddockDividerNodeRefEntity(target.id, 1, endId)
            )
        )
        updatePlan(plan.copy(name = normalizedLabel(name, "Polystrand Plan"), updatedAt = now))
        reconcileLabels(id, listOf(sideALabel, sideBLabel))
    }

    @Transaction
    suspend fun archive(id: Long, now: Long = System.currentTimeMillis()) {
        val plan = getPlan(id) ?: throw IllegalArgumentException("Paddock plan #$id not found")
        check(plan.archivedAt == null) { "Paddock plan is already archived" }
        archivePlanDividersRaw(id, now)
        archivePlanRaw(id, now)
    }

    @Transaction
    suspend fun reactivate(id: Long, now: Long = System.currentTimeMillis()) {
        val plan = getPlan(id) ?: throw IllegalArgumentException("Paddock plan #$id not found")
        val archivedAt = plan.archivedAt ?: throw IllegalStateException("Paddock plan is already active")
        check(activeForPasture(plan.pastureId) == null) {
            "Archive the currently active polystrand plan before reactivating this one"
        }
        reactivatePlanRaw(id, now)
        reactivatePlanDividersRaw(id, archivedAt, now)
        normalizeDividerSequences(id, now)
        resolve(id)
    }

    @Transaction
    suspend fun delete(id: Long) {
        getPlan(id) ?: throw IllegalArgumentException("Paddock plan #$id not found")
        deletePlanRaw(id)
    }

    private suspend fun insertStraightDivider(
        planId: Long,
        name: String,
        sequence: Int,
        start: PaddockBoundaryAnchor,
        end: PaddockBoundaryAnchor,
        now: Long
    ): Long {
        val startId = insertNode(start.toEntity(planId, now))
        val endId = insertNode(end.toEntity(planId, now))
        val dividerId = insertDivider(
            PaddockDividerEntity(
                planId = planId,
                name = name,
                sequence = sequence,
                createdAt = now,
                updatedAt = now
            )
        )
        insertRefs(
            listOf(
                PaddockDividerNodeRefEntity(dividerId, 0, startId),
                PaddockDividerNodeRefEntity(dividerId, 1, endId)
            )
        )
        return dividerId
    }

    private suspend fun reconcileLabels(planId: Long, requested: List<String>) {
        deleteLabels(planId)
        val result = resolve(planId)
        insertLabels(result.regions.mapIndexed { index, region ->
            PaddockRegionLabelEntity(
                planId = planId,
                regionKey = region.key,
                label = normalizedLabel(requested.getOrNull(index).orEmpty(), "Region ${index + 1}")
            )
        })
    }

    private suspend fun replaceDividerRefs(dividerId: Long, nodeIds: List<Long>) {
        deleteRefs(dividerId)
        insertRefs(nodeIds.mapIndexed { index, nodeId -> PaddockDividerNodeRefEntity(dividerId, index, nodeId) })
    }

    private suspend fun normalizeDividerSequences(planId: Long, now: Long) {
        val ordered = dividers(planId).sortedWith(compareBy<PaddockDividerEntity> { it.archivedAt != null }.thenBy { it.sequence }.thenBy { it.id })
        ordered.forEachIndexed { index, divider -> setDividerSequenceRaw(divider.id, -index - 1, now) }
        ordered.forEachIndexed { index, divider -> setDividerSequenceRaw(divider.id, index, now) }
    }

    private suspend fun dividersForNodeMutation(dividerId: Long): PaddockDividerEntity =
        observeDividerById(dividerId) ?: throw IllegalArgumentException("Paddock divider #$dividerId not found")

    @Query("SELECT * FROM paddock_dividers WHERE id = :dividerId")
    suspend fun observeDividerById(dividerId: Long): PaddockDividerEntity?

    private suspend fun nodesForNode(nodeId: Long): PaddockPlanNodeEntity =
        observeNodeById(nodeId) ?: throw IllegalArgumentException("Paddock node #$nodeId not found")

    @Query("SELECT * FROM paddock_plan_nodes WHERE id = :nodeId")
    suspend fun observeNodeById(nodeId: Long): PaddockPlanNodeEntity?

    private fun PaddockBoundaryAnchor.toEntity(planId: Long, now: Long) = PaddockPlanNodeEntity(
        planId = planId,
        nodeKind = PaddockPlanNodeKind.BOUNDARY_ANCHOR,
        boundaryJunctionAId = junctionAId,
        boundaryJunctionBId = junctionBId,
        boundarySegmentRatio = segmentRatio,
        createdAt = now,
        updatedAt = now
    )

    private fun PaddockNodeDraft.toEntity(planId: Long, now: Long) = PaddockPlanNodeEntity(
        planId = planId,
        nodeKind = nodeKind,
        boundaryJunctionAId = boundaryJunctionAId,
        boundaryJunctionBId = boundaryJunctionBId,
        boundarySegmentRatio = boundarySegmentRatio,
        latitude = latitude,
        longitude = longitude,
        createdAt = now,
        updatedAt = now
    )

    private fun normalizedLabel(value: String, fallback: String): String = value.trim().ifBlank { fallback }
}
