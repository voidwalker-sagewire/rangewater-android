package com.sagewire.rangewater.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sagewire.rangewater.spatial.PaddockSplitEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddockPlanDaoTest {
    private lateinit var db: RangeWaterDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RangeWaterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun createAddArchiveReactivateAndDeletePreserveOneActivePlanRule() = runBlocking {
        val pastureId = squarePasture()
        val pasture = db.pastureDao().getById(pastureId)!!
        val firstStart = PaddockSplitEngine.resolveAnchor(pasture, 1, 2, 0.33)
        val firstEnd = PaddockSplitEngine.resolveAnchor(pasture, 3, 4, 0.67)
        val secondStart = PaddockSplitEngine.resolveAnchor(pasture, 1, 2, 0.67)
        val secondEnd = PaddockSplitEngine.resolveAnchor(pasture, 3, 4, 0.33)
        val dao = db.paddockPlanDao()

        val id = dao.create(pastureId, "Strip Plan", "West", "East", firstStart, firstEnd, now = 100)
        dao.addStraightDivider(id, "Divider 2", secondStart, secondEnd, now = 110)
        assertEquals(2, dao.resolve(id).dividers.size)
        assertEquals(3, dao.resolve(id).regions.size)
        assertEquals(1, dao.observeActivePlans().first().size)

        try {
            dao.create(pastureId, "Second", "A", "B", firstStart, firstEnd)
            fail("Expected one-active-plan rejection")
        } catch (_: IllegalStateException) {
        }

        dao.archive(id, now = 200)
        assertTrue(dao.observeActivePlans().first().isEmpty())
        dao.reactivate(id, now = 300)
        assertEquals(null, dao.getPlan(id)?.archivedAt)

        try {
            db.pastureDao().deleteById(pastureId)
            fail("Expected polystrand-plan pasture delete blocker")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("paddock split"))
        }

        dao.delete(id)
        assertTrue(dao.observePlans().first().isEmpty())
    }

    @Test
    fun sharedJunctionCannotBeDemotedUntilAdditionalDividerIsUnlinked() = runBlocking {
        val pastureId = squarePasture()
        val pasture = db.pastureDao().getById(pastureId)!!
        val dao = db.paddockPlanDao()
        val id = dao.createWithDivider(
            pastureId,
            "Shared-center plan",
            "Horizontal",
            listOf(
                PaddockSplitEngine.resolveAnchor(pasture, 1, 4, 0.5).toDraft(),
                PaddockNodeDraft(
                    nodeKind = PaddockPlanNodeKind.INTERIOR_WAYPOINT,
                    latitude = 40.005,
                    longitude = -79.995
                ),
                PaddockSplitEngine.resolveAnchor(pasture, 2, 3, 0.5).toDraft()
            ),
            now = 100
        )
        val center = dao.nodes(id).single { it.nodeKind == PaddockPlanNodeKind.INTERIOR_WAYPOINT }
        val vertical = dao.connectWaypointWithNewDivider(
            center.id,
            "Vertical",
            listOf(
                PaddockSplitEngine.resolveAnchor(pasture, 1, 2, 0.5).toDraft(),
                PaddockNodeDraft(
                    existingNodeId = center.id,
                    nodeKind = PaddockPlanNodeKind.INTERIOR_JUNCTION
                ),
                PaddockSplitEngine.resolveAnchor(pasture, 3, 4, 0.5).toDraft()
            ),
            now = 120
        )
        assertEquals(PaddockPlanNodeKind.INTERIOR_JUNCTION, dao.observeNodeById(center.id)?.nodeKind)

        try {
            dao.demoteJunction(center.id)
            fail("Expected referenced shared junction demotion to be blocked")
        } catch (_: IllegalStateException) {
        }

        try {
            dao.unlinkNodeFromDivider(center.id, vertical)
            fail("Expected unlink that leaves a visual crossing to be blocked")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(PaddockPlanNodeKind.INTERIOR_JUNCTION, dao.observeNodeById(center.id)?.nodeKind)

        dao.deleteDivider(vertical, now = 125)
        dao.demoteJunction(center.id, now = 130)
        assertEquals(PaddockPlanNodeKind.INTERIOR_WAYPOINT, dao.observeNodeById(center.id)?.nodeKind)
    }

    @Test
    fun rejectedConnectShareRollsBackPromotionAndNewDivider() = runBlocking {
        val pastureId = squarePasture()
        val pasture = db.pastureDao().getById(pastureId)!!
        val dao = db.paddockPlanDao()
        val id = dao.createWithDivider(
            pastureId,
            "Rollback plan",
            "Horizontal",
            listOf(
                PaddockSplitEngine.resolveAnchor(pasture, 1, 4, 0.5).toDraft(),
                PaddockNodeDraft(
                    nodeKind = PaddockPlanNodeKind.INTERIOR_WAYPOINT,
                    latitude = 40.005,
                    longitude = -79.995
                ),
                PaddockSplitEngine.resolveAnchor(pasture, 2, 3, 0.5).toDraft()
            ),
            now = 100
        )
        val center = dao.nodes(id).single { it.nodeKind == PaddockPlanNodeKind.INTERIOR_WAYPOINT }
        val bottom = PaddockSplitEngine.resolveAnchor(pasture, 1, 2, 0.5).toDraft()

        try {
            dao.connectWaypointWithNewDivider(
                center.id,
                "Invalid double-back",
                listOf(
                    bottom,
                    PaddockNodeDraft(
                        existingNodeId = center.id,
                        nodeKind = PaddockPlanNodeKind.INTERIOR_JUNCTION
                    ),
                    bottom
                ),
                now = 120
            )
            fail("Expected invalid connection to roll back")
        } catch (_: Exception) {
        }

        assertEquals(PaddockPlanNodeKind.INTERIOR_WAYPOINT, dao.observeNodeById(center.id)?.nodeKind)
        assertEquals(1, dao.dividers(id).size)
        assertEquals(3, dao.nodeRefs(id).size)
    }

    @Test
    fun savedDividerPointEditsValidateAtomicallyAndRollbackOnFailure() = runBlocking {
        val pastureId = squarePasture()
        val pasture = db.pastureDao().getById(pastureId)!!
        val dao = db.paddockPlanDao()
        val id = dao.createWithDivider(
            pastureId,
            "Editable plan",
            "Bent divider",
            listOf(
                PaddockSplitEngine.resolveAnchor(pasture, 1, 4, 0.5).toDraft(),
                PaddockNodeDraft(
                    nodeKind = PaddockPlanNodeKind.INTERIOR_WAYPOINT,
                    latitude = 40.005,
                    longitude = -79.995
                ),
                PaddockSplitEngine.resolveAnchor(pasture, 2, 3, 0.5).toDraft()
            ),
            now = 100
        )
        val divider = dao.dividers(id).single()
        val initialNodes = dao.nodes(id).associateBy { it.id }
        val ordered = dao.nodeRefs(id).sortedBy { it.sequence }.map { initialNodes.getValue(it.nodeId) }
        val waypoint = ordered[1]

        dao.replaceDividerPath(
            divider.id,
            ordered.map { node ->
                node.toDraft().let { draft ->
                    if (node.id == waypoint.id) draft.copy(latitude = 40.006) else draft
                }
            },
            now = 120
        )
        assertEquals(40.006, dao.observeNodeById(waypoint.id)?.latitude ?: Double.NaN, 0.0)
        assertEquals(2, dao.resolve(id).regions.size)

        val savedNodes = dao.nodes(id).associateBy { it.id }
        val savedPath = dao.nodeRefs(id).sortedBy { it.sequence }.map { savedNodes.getValue(it.nodeId) }
        val first = savedPath.first()
        val last = savedPath.last()
        try {
            dao.replaceDividerPath(
                divider.id,
                savedPath.map { node ->
                    if (node.id == last.id) {
                        node.toDraft().copy(
                            boundaryJunctionAId = first.boundaryJunctionAId,
                            boundaryJunctionBId = first.boundaryJunctionBId,
                            boundarySegmentRatio = first.boundarySegmentRatio
                        )
                    } else {
                        node.toDraft()
                    }
                },
                now = 130
            )
            fail("Expected invalid saved edit to roll back")
        } catch (_: Exception) {
        }

        assertEquals(40.006, dao.observeNodeById(waypoint.id)?.latitude ?: Double.NaN, 0.0)
        assertEquals(savedPath.last(), dao.observeNodeById(last.id))
    }

    private fun com.sagewire.rangewater.spatial.PaddockBoundaryAnchor.toDraft() = PaddockNodeDraft(
        nodeKind = PaddockPlanNodeKind.BOUNDARY_ANCHOR,
        boundaryJunctionAId = junctionAId,
        boundaryJunctionBId = junctionBId,
        boundarySegmentRatio = segmentRatio
    )

    private fun PaddockPlanNodeEntity.toDraft() = PaddockNodeDraft(
        existingNodeId = id,
        nodeKind = nodeKind,
        boundaryJunctionAId = boundaryJunctionAId,
        boundaryJunctionBId = boundaryJunctionBId,
        boundarySegmentRatio = boundarySegmentRatio,
        latitude = latitude,
        longitude = longitude
    )

    private suspend fun squarePasture(): Long {
        val now = 1L
        db.pastureDao().insertPasture(PastureEntity(10, "Square", "", now, now))
        db.pastureDao().insertJunctionEntity(FenceJunctionEntity(1, 40.0, -80.0))
        db.pastureDao().insertJunctionEntity(FenceJunctionEntity(2, 40.0, -79.99))
        db.pastureDao().insertJunctionEntity(FenceJunctionEntity(3, 40.01, -79.99))
        db.pastureDao().insertJunctionEntity(FenceJunctionEntity(4, 40.01, -80.0))
        db.pastureDao().insertVertices(
            listOf(
                PastureVertexEntity(pastureId = 10, sequence = 0, junctionId = 1),
                PastureVertexEntity(pastureId = 10, sequence = 1, junctionId = 2),
                PastureVertexEntity(pastureId = 10, sequence = 2, junctionId = 3),
                PastureVertexEntity(pastureId = 10, sequence = 3, junctionId = 4)
            )
        )
        return 10
    }
}
