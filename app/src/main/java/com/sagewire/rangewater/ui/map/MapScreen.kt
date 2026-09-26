package com.sagewire.rangewater.ui.map

import android.content.ComponentCallbacks2
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sagewire.rangewater.data.PastureCoordinate
import com.sagewire.rangewater.data.BackupDestinationRepository
import com.sagewire.rangewater.data.OpenRangeWaterBackupContract
import com.sagewire.rangewater.data.PreparedRangeWaterRestore
import com.sagewire.rangewater.data.RangeWaterArchiveCodec
import com.sagewire.rangewater.data.RangeWaterBackupManager
import com.sagewire.rangewater.data.CattleMovementEntity
import com.sagewire.rangewater.data.CountUnit
import com.sagewire.rangewater.data.HerdEntity
import com.sagewire.rangewater.data.HerdLocationKind
import com.sagewire.rangewater.data.MovementStatus
import com.sagewire.rangewater.data.StockClass
import com.sagewire.rangewater.data.PastureWithVertices
import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.ForageCalibrationSource
import com.sagewire.rangewater.data.ForageCalculator
import com.sagewire.rangewater.data.ForageStandCondition
import com.sagewire.rangewater.data.ForageStandType
import com.sagewire.rangewater.data.GateEntity
import com.sagewire.rangewater.data.GateWithConnectivity
import com.sagewire.rangewater.data.GrazingCircuitEntity
import com.sagewire.rangewater.data.GrazingCircuitPastureEntity
import com.sagewire.rangewater.data.GrazingCircuitPastureDraft
import com.sagewire.rangewater.data.GrazingCircuitPastureRoleEntity
import com.sagewire.rangewater.data.PastureForageObservationEntity
import com.sagewire.rangewater.data.PastureRestState
import com.sagewire.rangewater.data.PastureRestStatus
import com.sagewire.rangewater.data.RangeWaterDatabase
import com.sagewire.rangewater.data.SeasonalPastureRole
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import com.sagewire.rangewater.spatial.AcreageCalculator
import com.sagewire.rangewater.spatial.GeometryValidator
import com.sagewire.rangewater.spatial.GateFeatureConverter
import com.sagewire.rangewater.spatial.GateSnapResult
import com.sagewire.rangewater.spatial.GateSnappingEngine
import com.sagewire.rangewater.spatial.HerdFeatureConverter
import com.sagewire.rangewater.spatial.MovementFeatureConverter
import com.sagewire.rangewater.spatial.SnappedGateCandidate
import com.sagewire.rangewater.spatial.CornerSnappingEngine
import com.sagewire.rangewater.spatial.PastureAnalyticsCalculator
import com.sagewire.rangewater.spatial.PastureCoverageMetrics
import com.sagewire.rangewater.spatial.PastureFeatureConverter
import com.sagewire.rangewater.spatial.PastureFeatureConverter.orderedCoordinates
import com.sagewire.rangewater.spatial.WaterFeatureConverter
import java.util.Locale
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

enum class ActiveMapMode { AERIAL, LABELED }

private enum class InteractionState {
    ORDINARY,
    WATER_PLACEMENT,
    WATER_MOVING,
    PASTURE_DRAWING,
    PASTURE_EDITING,
    GATE_PLACEMENT,
    GATE_MOVING
}

private enum class PastureEditMode {
    SELECT_OR_DRAG,
    ADD_CORNER
}

private data class PastureEditSnapshot(
    val vertices: List<PastureCoordinate>,
    val junctionMoves: Map<Long, PastureCoordinate>
)

/*
 * 🪨 BLOCK 1 — PERSISTENT WATER AND PASTURE MAP HOST
 * Purpose: Adds durable pasture drawing/editing without regressing the verified map,
 * lifecycle, memory, imagery, zoom, attribution, or water-asset behavior.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    initialLat: Double = MapConfig.DEFAULT_LATITUDE,
    initialLng: Double = MapConfig.DEFAULT_LONGITUDE,
    initialZoom: Double = MapConfig.DEFAULT_ZOOM
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val database = remember(appContext) { RangeWaterDatabase.getDatabase(appContext) }
    val waterDao = remember(database) { database.waterPointDao() }
    val pastureDao = remember(database) { database.pastureDao() }
    val junctionDao = remember(database) { database.fenceJunctionDao() }
    val assignmentDao = remember(database) { database.waterPastureAssignmentDao() }
    val gateDao = remember(database) { database.gateDao() }
    val waterPoints by waterDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val pastures by pastureDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val allJunctions by junctionDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val assignments by assignmentDao.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val gates by gateDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val herds by database.herdDao().observeActiveHerds().collectAsStateWithLifecycle(initialValue = emptyList())
    val movements by database.movementDao().observeAllMovements().collectAsStateWithLifecycle(initialValue = emptyList())
    val grazingCircuits by database.grazingCircuitDao().observeActiveCircuits()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val circuitPastures by database.grazingCircuitDao().observeAllMemberships()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val circuitRoles by database.grazingCircuitDao().observeAllRoles()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val herdCircuitAssignments by database.grazingCircuitDao().observeAllHerdAssignments()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val forageObservations by database.forageObservationDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val assignmentMap = remember(assignments) {
        assignments.groupBy({ it.waterPointId }, { it.pastureId })
    }
    val junctionUsageMap = remember(pastures) {
        buildMap<Long, Set<Long>> {
            pastures.forEach { pasture ->
                pasture.vertices.forEach { vertex ->
                    put(
                        vertex.junctionId,
                        get(vertex.junctionId).orEmpty() + pasture.pasture.id
                    )
                }
            }
        }
    }
    val displayPreferencesRepository = remember(appContext) {
        DisplayPreferencesRepository(appContext)
    }
    val backupManager = remember(appContext, database) {
        RangeWaterBackupManager(appContext, database, displayPreferencesRepository)
    }
    val backupDestinationRepository = remember(appContext) {
        BackupDestinationRepository(appContext)
    }
    val cameraStateRepository = remember(appContext) {
        MapCameraStateRepository(appContext)
    }
    var displayPreferences by remember {
        mutableStateOf(displayPreferencesRepository.getPreferences())
    }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showDataSafetyDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<PreparedRangeWaterRestore?>(null) }
    var restoringEmergencyBackup by remember { mutableStateOf(false) }
    var dataOperationInProgress by remember { mutableStateOf(false) }
    var emergencyBackupAvailable by remember { mutableStateOf(backupManager.hasEmergencyBackup()) }
    var backupFolderUri by remember { mutableStateOf(backupDestinationRepository.backupFolder()) }
    var lastBackupUri by remember { mutableStateOf(backupDestinationRepository.lastBackup()) }
    var showAssignPasturesDialog by remember { mutableStateOf(false) }
    val assignmentDraftIds = remember { mutableStateListOf<Long>() }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var activeMode by remember { mutableStateOf(ActiveMapMode.AERIAL) }
    var interactionState by remember { mutableStateOf(InteractionState.ORDINARY) }
    var currentZoom by remember { mutableDoubleStateOf(initialZoom) }
    var isMapRendering by remember { mutableStateOf(true) }
    var hasLoadError by remember { mutableStateOf(false) }
    var isMapStyleReady by remember { mutableStateOf(false) }
    var initialViewportApplied by remember { mutableStateOf(false) }

    var selectedWaterId by remember { mutableStateOf<Long?>(null) }
    var selectedPastureId by remember { mutableStateOf<Long?>(null) }
    var selectedGateId by remember { mutableStateOf<Long?>(null) }
    var selectedHerdId by remember { mutableStateOf<Long?>(null) }
    var focusedHerdId by remember { mutableStateOf<Long?>(null) }
    var selectedMovementId by remember { mutableStateOf<Long?>(null) }
    var herdManagerPastureFilterId by remember { mutableStateOf<Long?>(null) }
    var showHerdManagerDialog by remember { mutableStateOf(false) }
    var showCircuitManagerDialog by remember { mutableStateOf(false) }
    var showCircuitEditorDialog by remember { mutableStateOf(false) }
    var editingCircuitId by remember { mutableStateOf<Long?>(null) }
    var showAssignCircuitDialog by remember { mutableStateOf(false) }
    var showForageObservationDialog by remember { mutableStateOf(false) }
    var showHerdCreateEditDialog by remember { mutableStateOf(false) }
    var herdBeingEdited by remember { mutableStateOf<HerdEntity?>(null) }
    var showPlanMoveDialog by remember { mutableStateOf(false) }
    var showMoveHistoryDialog by remember { mutableStateOf(false) }
    var showArchiveHerdConfirmDialog by remember { mutableStateOf(false) }
    var candidateGate by remember { mutableStateOf<SnappedGateCandidate?>(null) }
    var movingGate by remember { mutableStateOf<GateEntity?>(null) }
    var draftGateMoveCandidate by remember { mutableStateOf<SnappedGateCandidate?>(null) }
    var showGateEditDialog by remember { mutableStateOf(false) }
    var showGateDeleteDialog by remember { mutableStateOf(false) }
    var selectedVertexIndex by remember { mutableStateOf<Int?>(null) }
    var pastureEditMode by remember { mutableStateOf(PastureEditMode.SELECT_OR_DRAG) }
    val draftVertices = remember { mutableStateListOf<PastureCoordinate>() }
    val ephemeralJunctionMoves = remember { mutableStateMapOf<Long, PastureCoordinate>() }
    val undoSnapshots = remember { mutableStateListOf<PastureEditSnapshot>() }
    var draftRevision by remember { mutableIntStateOf(0) }
    var activeSnappedJunction by remember { mutableStateOf<FenceJunctionEntity?>(null) }
    var approvedSharedMoveJunctionId by remember { mutableStateOf<Long?>(null) }
    var pendingSharedMoveJunctionId by remember { mutableStateOf<Long?>(null) }
    var pendingJoinJunction by remember { mutableStateOf<FenceJunctionEntity?>(null) }
    var pendingJoinIndex by remember { mutableStateOf<Int?>(null) }
    var pendingJoinIndependentCoordinate by remember { mutableStateOf<PastureCoordinate?>(null) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var mergeCandidates by remember {
        mutableStateOf<List<Pair<FenceJunctionEntity, Double>>>(emptyList())
    }
    var selectedMergeTargetId by remember { mutableStateOf<Long?>(null) }

    var showPastureNameDialog by remember { mutableStateOf(false) }
    var pastureNameInput by remember { mutableStateOf("") }
    var showPastureDetailsDialog by remember { mutableStateOf(false) }
    var pastureEditName by remember { mutableStateOf("") }
    var pastureEditNotes by remember { mutableStateOf("") }
    var showPastureDeleteDialog by remember { mutableStateOf(false) }

    var showWaterEditDialog by remember { mutableStateOf(false) }
    var showWaterDeleteDialog by remember { mutableStateOf(false) }
    var waterEditName by remember { mutableStateOf("") }
    var waterEditNotes by remember { mutableStateOf("") }
    var waterEditType by remember { mutableStateOf(WaterSourceType.TROUGH) }
    var movingWaterPoint by remember { mutableStateOf<WaterPointEntity?>(null) }
    var draftWaterLocation by remember { mutableStateOf<LatLng?>(null) }

    val selectedWater = waterPoints.firstOrNull { it.id == selectedWaterId }
    val selectedPasture = pastures.firstOrNull { it.pasture.id == selectedPastureId }
    val selectedHerd = herds.firstOrNull { it.id == selectedHerdId }
    val focusedHerd = herds.firstOrNull { it.id == focusedHerdId }
    val focusedCircuitPastureIds = remember(focusedHerd, herdCircuitAssignments, circuitPastures) {
        val circuitId = herdCircuitAssignments.firstOrNull { it.herdId == focusedHerd?.id }?.circuitId
        circuitPastures.filter { it.circuitId == circuitId }.map { it.pastureId }.toSet()
    }
    val herdFocus = remember(focusedHerd, assignmentMap, focusedCircuitPastureIds) {
        HerdFocusController.derive(focusedHerd, assignmentMap, focusedCircuitPastureIds)
    }
    val activeMovement = movements.firstOrNull { it.id == selectedMovementId }
    var selectedPastureRestStatus by remember { mutableStateOf<PastureRestStatus?>(null) }
    LaunchedEffect(selectedPastureId, herds, movements) {
        selectedPastureRestStatus = selectedPastureId?.let { pastureId ->
            withContext(Dispatchers.IO) { database.pastureRestDao().status(pastureId) }
        }
    }
    val selectedPastureLatestForage = remember(selectedPastureId, forageObservations) {
        forageObservations
            .filter { it.pastureId == selectedPastureId }
            .maxWithOrNull(compareBy<PastureForageObservationEntity> { it.observedAt }.thenBy { it.id })
    }
    val selectedPastureIsStockpiledWinter = remember(selectedPastureId, circuitRoles) {
        circuitRoles.any {
            it.pastureId == selectedPastureId && it.role == SeasonalPastureRole.STOCKPILED_WINTER
        }
    }
    val selectedPastureMetrics = remember(selectedPasture, waterPoints, assignmentMap) {
        selectedPasture?.let { pasture ->
            val assignedWater = waterPoints.filter { point ->
                pasture.pasture.id in assignmentMap[point.id].orEmpty()
            }
            PastureAnalyticsCalculator.computeMetrics(pasture, assignedWater)
        }
    }
    val effectiveWaterPoints = applyWaterMovePreview(
        waterPoints = waterPoints,
        movingWaterPoint = movingWaterPoint,
        draftLocation = draftWaterLocation
    )
    val effectivePastures = remember(pastures, ephemeralJunctionMoves.toMap()) {
        applyJunctionMovePreview(pastures, ephemeralJunctionMoves)
    }
    val effectiveJunctionCoordinates = remember(allJunctions, ephemeralJunctionMoves.toMap()) {
        allJunctions.associate { junction ->
            val moved = ephemeralJunctionMoves[junction.id]
            junction.id to if (moved == null) {
                LatLng(junction.latitude, junction.longitude)
            } else {
                LatLng(moved.latitude, moved.longitude)
            }
        }
    }
    val resolvedGates = remember(gates, effectivePastures, effectiveJunctionCoordinates) {
        gates.mapNotNull { gate ->
            GateSnappingEngine.resolveConnectivity(gate, effectivePastures, effectiveJunctionCoordinates)
        }
    }
    val displayedGates = remember(
        resolvedGates,
        candidateGate,
        movingGate,
        draftGateMoveCandidate,
        interactionState
    ) {
        when {
            interactionState == InteractionState.GATE_MOVING &&
                movingGate != null && draftGateMoveCandidate != null -> {
                val moving = movingGate!!
                val candidate = draftGateMoveCandidate!!
                resolvedGates.map { resolved ->
                    if (resolved.gate.id != moving.id) resolved else candidate.toConnectivity(
                        moving.copy(
                            junctionAId = candidate.junctionA.id,
                            junctionBId = candidate.junctionB.id,
                            segmentRatio = candidate.segmentRatio
                        )
                    )
                }
            }
            interactionState == InteractionState.GATE_PLACEMENT && candidateGate != null -> {
                val candidate = candidateGate!!
                resolvedGates + candidate.toConnectivity(
                    GateEntity(
                        id = 0L,
                        name = "Gate preview",
                        junctionAId = candidate.junctionA.id,
                        junctionBId = candidate.junctionB.id,
                        segmentRatio = candidate.segmentRatio,
                        widthMeters = GateEntity.WIDTH_14_FT
                    )
                )
            }
            else -> resolvedGates
        }
    }
    val focusedWaterPoints = remember(effectiveWaterPoints, herdFocus) {
        HerdFocusController.filterWaterPoints(effectiveWaterPoints, herdFocus)
    }
    val focusedGates = remember(displayedGates, herdFocus) {
        HerdFocusController.filterGates(displayedGates, herdFocus)
    }
    val focusedHerds = remember(herds, herdFocus) {
        HerdFocusController.filterHerds(herds, herdFocus)
    }
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }
    val currentMapInstance by rememberUpdatedState(mapInstance)

    fun replaceDraft(vertices: List<PastureCoordinate>) {
        draftVertices.clear()
        draftVertices.addAll(vertices)
        draftRevision++
    }

    fun resetAfterRestore() {
        interactionState = InteractionState.ORDINARY
        selectedWaterId = null
        selectedPastureId = null
        selectedGateId = null
        selectedHerdId = null
        focusedHerdId = null
        selectedMovementId = null
        candidateGate = null
        movingGate = null
        draftGateMoveCandidate = null
        movingWaterPoint = null
        draftWaterLocation = null
        replaceDraft(emptyList())
        ephemeralJunctionMoves.clear()
        undoSnapshots.clear()
        displayPreferences = displayPreferencesRepository.getPreferences()
        cameraStateRepository.clear()
        initialViewportApplied = false
    }

    fun backupFileName(): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
        return "RangeWater-$stamp${RangeWaterArchiveCodec.FILE_EXTENSION}"
    }

    fun retainUriPermission(uri: Uri, flags: Int) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some document providers keep the grant themselves but reject this optional call.
        }
    }

    suspend fun writeBackup(uri: Uri) {
        withContext(Dispatchers.IO) {
            val output = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
                "The selected backup file could not be opened"
            }
            output.use { backupManager.writeManualBackup(it) }
        }
        backupDestinationRepository.saveLastBackup(uri)
        lastBackupUri = uri
    }

    suspend fun createBackupInFolder(folder: Uri): Uri {
        val createdUri = withContext(Dispatchers.IO) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                folder,
                DocumentsContract.getTreeDocumentId(folder)
            )
            requireNotNull(
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parent,
                    RangeWaterArchiveCodec.MIME_TYPE,
                    backupFileName()
                )
            ) { "The selected folder could not create a backup file" }
        }
        writeBackup(createdUri)
        return createdUri
    }

    fun shareBackup(uri: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = RangeWaterArchiveCodec.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "RangeWater backup", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Share RangeWater backup"))
    }

    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            retainUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            backupDestinationRepository.saveBackupFolder(uri)
            backupFolderUri = uri
            scope.launch {
                dataOperationInProgress = true
                try {
                    createBackupInFolder(uri)
                    Toast.makeText(context, "Backup folder saved and backup created", Toast.LENGTH_LONG).show()
                    showDataSafetyDialog = false
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Backup failed", Toast.LENGTH_LONG).show()
                } finally {
                    dataOperationInProgress = false
                }
            }
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RangeWaterArchiveCodec.MIME_TYPE)
    ) { uri ->
        if (uri != null) {
            retainUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch {
                dataOperationInProgress = true
                try {
                    writeBackup(uri)
                    Toast.makeText(context, "RangeWater backup created", Toast.LENGTH_LONG).show()
                    showDataSafetyDialog = false
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Backup failed", Toast.LENGTH_LONG).show()
                } finally {
                    dataOperationInProgress = false
                }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        OpenRangeWaterBackupContract()
    ) { uri ->
        if (uri != null) {
            retainUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch {
                dataOperationInProgress = true
                try {
                    pendingRestore = withContext(Dispatchers.IO) {
                        val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                            "The selected backup file could not be opened"
                        }
                        input.use(backupManager::prepareRestore)
                    }
                    restoringEmergencyBackup = false
                    showRestoreConfirmDialog = true
                    showDataSafetyDialog = false
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Backup cannot be restored", Toast.LENGTH_LONG).show()
                } finally {
                    dataOperationInProgress = false
                }
            }
        }
    }

    fun rememberUndoPoint() {
        undoSnapshots.add(
            PastureEditSnapshot(draftVertices.toList(), ephemeralJunctionMoves.toMap())
        )
    }

    fun leaveGeometryMode() {
        interactionState = InteractionState.ORDINARY
        draftVertices.clear()
        undoSnapshots.clear()
        ephemeralJunctionMoves.clear()
        activeSnappedJunction = null
        approvedSharedMoveJunctionId = null
        pendingSharedMoveJunctionId = null
        pendingJoinJunction = null
        pendingJoinIndex = null
        pendingJoinIndependentCoordinate = null
        selectedVertexIndex = null
        pastureEditMode = PastureEditMode.SELECT_OR_DRAG
        draftRevision++
    }

    fun geometryError(): String? = GeometryValidator.validationError(draftVertices)

    // 🪨 BLOCK 2 — VERIFIED LIFECYCLE, MEMORY, AND RENDER BRIDGE
    DisposableEffect(lifecycleOwner, mapView, appContext) {
        var destroyed = false

        fun destroyMapViewOnce() {
            if (!destroyed) {
                destroyed = true
                mapView.onDestroy()
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> destroyMapViewOnce()
                else -> Unit
            }
        }
        val memoryCallbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = mapView.onLowMemory()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) mapView.onLowMemory()
            }
        }
        val willRenderListener = MapView.OnWillStartRenderingMapListener { isMapRendering = true }
        val didRenderListener = MapView.OnDidFinishRenderingMapListener { fullyRendered ->
            if (fullyRendered) {
                isMapRendering = false
                hasLoadError = false
            }
        }
        val failedLoadListener = MapView.OnDidFailLoadingMapListener {
            isMapRendering = false
            hasLoadError = true
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        appContext.registerComponentCallbacks(memoryCallbacks)
        mapView.addOnWillStartRenderingMapListener(willRenderListener)
        mapView.addOnDidFinishRenderingMapListener(didRenderListener)
        mapView.addOnDidFailLoadingMapListener(failedLoadListener)
        onDispose {
            currentMapInstance?.cameraPosition?.let { camera ->
                cameraStateRepository.save(camera.toSavedMapCamera())
            }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            appContext.unregisterComponentCallbacks(memoryCallbacks)
            mapView.removeOnWillStartRenderingMapListener(willRenderListener)
            mapView.removeOnDidFinishRenderingMapListener(didRenderListener)
            mapView.removeOnDidFailLoadingMapListener(failedLoadListener)
            destroyMapViewOnce()
        }
    }

    // 🎮 BLOCK 3 — ROOM-TO-MAP SYNCHRONIZATION
    LaunchedEffect(
        focusedWaterPoints,
        effectivePastures,
        selectedWaterId,
        selectedPastureId,
        selectedGateId,
        focusedHerds,
        selectedHerdId,
        herdFocus,
        activeMovement,
        focusedGates,
        selectedVertexIndex,
        draftRevision,
        interactionState,
        assignmentMap,
        displayPreferences.coverageScope,
        activeSnappedJunction,
        mapInstance
    ) {
        pushAllOverlays(
            map = mapInstance,
            waterPoints = focusedWaterPoints,
            selectedWaterId = selectedWaterId,
            pastures = effectivePastures,
            selectedPastureId = selectedPastureId,
            gates = focusedGates,
            boundaryGates = displayedGates,
            selectedGateId = selectedGateId,
            herds = focusedHerds,
            selectedHerdId = selectedHerdId,
            focusedPastureId = herdFocus?.pastureId,
            focusedPastureIds = herdFocus?.pastureIds.orEmpty(),
            focusColorHex = herdFocus?.colorHex,
            activeMovement = activeMovement,
            draftVertices = draftVertices.toList(),
            selectedVertexIndex = selectedVertexIndex,
            coverageScope = displayPreferences.coverageScope,
            assignments = assignmentMap,
            sharedJunctionIds = junctionUsageMap.filterValues { it.size > 1 }.keys,
            activeSnappedJunction = activeSnappedJunction
        )
    }
    LaunchedEffect(waterPoints, pastures, gates, herds, movements, selectedWaterId, selectedPastureId, selectedGateId, selectedHerdId, focusedHerdId, selectedMovementId) {
        if (selectedWaterId != null && selectedWater == null) selectedWaterId = null
        if (selectedPastureId != null && selectedPasture == null) selectedPastureId = null
        if (selectedGateId != null && gates.none { it.id == selectedGateId }) selectedGateId = null
        if (selectedHerdId != null && selectedHerd == null) selectedHerdId = null
        if (focusedHerdId != null && focusedHerd == null) focusedHerdId = null
        if (selectedMovementId != null && movements.none { it.id == selectedMovementId }) selectedMovementId = null
        if (selectedWater == null) showAssignPasturesDialog = false
    }
    LaunchedEffect(interactionState) {
        if (interactionState != InteractionState.ORDINARY) focusedHerdId = null
    }
    LaunchedEffect(displayPreferences, interactionState, mapInstance) {
        mapInstance?.let { map ->
            applyLayerVisibility(
                map = map,
                preferences = displayPreferences,
                isMovingWater = interactionState == InteractionState.WATER_MOVING,
                isEditingWater = interactionState == InteractionState.WATER_PLACEMENT ||
                    interactionState == InteractionState.WATER_MOVING,
                isEditingPasture = interactionState == InteractionState.PASTURE_DRAWING ||
                    interactionState == InteractionState.PASTURE_EDITING,
                isEditingGate = interactionState == InteractionState.GATE_PLACEMENT ||
                    interactionState == InteractionState.GATE_MOVING
            )
        }
    }
    LaunchedEffect(
        mapInstance,
        isMapStyleReady,
        initialViewportApplied,
        pastures,
        waterPoints
    ) {
        val map = mapInstance ?: return@LaunchedEffect
        if (!isMapStyleReady || initialViewportApplied) return@LaunchedEffect
        val ranchCoordinates = buildList {
            pastures.forEach { pasture ->
                pasture.vertices.forEach { vertex ->
                    add(LatLng(vertex.junction.latitude, vertex.junction.longitude))
                }
            }
            waterPoints.forEach { point -> add(LatLng(point.latitude, point.longitude)) }
        }
        if (ranchCoordinates.isEmpty()) return@LaunchedEffect

        if (ranchCoordinates.size == 1) {
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(ranchCoordinates.first())
                        .zoom(17.0)
                        .build()
                )
            )
        } else {
            val bounds = LatLngBounds.Builder().includes(ranchCoordinates).build()
            val padding = (72f * context.resources.displayMetrics.density).toInt()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
        initialViewportApplied = true
        cameraStateRepository.save(map.cameraPosition.toSavedMapCamera())
    }

    // 🎮 BLOCK 4 — MODE-AWARE MAP TAP ROUTING
    DisposableEffect(
        mapInstance,
        interactionState,
        selectedVertexIndex,
        pastureEditMode,
        allJunctions,
        pastures,
        movingGate
    ) {
        val map = mapInstance
        if (map == null) {
            onDispose { }
        } else {
            val listener = MapLibreMap.OnMapClickListener { coordinate ->
                when (interactionState) {
                    InteractionState.GATE_PLACEMENT -> {
                        when (
                            val result = GateSnappingEngine.findCandidateSegmentScreenSpace(
                                tapPoint = coordinate,
                                pastures = pastures,
                                projection = map.projection,
                                tolerancePx = 36f * context.resources.displayMetrics.density
                            )
                        ) {
                            is GateSnapResult.Snapped -> candidateGate = result.candidate
                            GateSnapResult.FenceTooShort -> Toast.makeText(
                                context,
                                "Fence segment too short for gate width",
                                Toast.LENGTH_LONG
                            ).show()
                            GateSnapResult.NoFenceInRange -> Toast.makeText(
                                context,
                                "Tap directly on a fence line",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    InteractionState.GATE_MOVING -> {
                        val gate = movingGate
                        if (gate != null) {
                            when (
                                val result = GateSnappingEngine.findCandidateSegmentScreenSpace(
                                    tapPoint = coordinate,
                                    pastures = pastures,
                                    projection = map.projection,
                                    tolerancePx = 36f * context.resources.displayMetrics.density,
                                    gateWidthMeters = gate.widthMeters
                                )
                            ) {
                                is GateSnapResult.Snapped -> draftGateMoveCandidate = result.candidate
                                GateSnapResult.FenceTooShort -> Toast.makeText(
                                    context,
                                    "Fence segment too short for gate width",
                                    Toast.LENGTH_LONG
                                ).show()
                                GateSnapResult.NoFenceInRange -> Toast.makeText(
                                    context,
                                    "Tap directly on a fence line",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    InteractionState.WATER_MOVING -> {
                        draftWaterLocation = coordinate
                    }
                    InteractionState.WATER_PLACEMENT -> {
                        interactionState = InteractionState.ORDINARY
                        scope.launch {
                            selectedWaterId = waterDao.insertWithDefaultName(
                                coordinate.latitude,
                                coordinate.longitude
                            )
                            selectedPastureId = null
                        }
                    }
                    InteractionState.PASTURE_DRAWING -> {
                        val snapped = CornerSnappingEngine.findSnapJunction(
                            coordinate,
                            allJunctions,
                            map.projection,
                            32f * context.resources.displayMetrics.density
                        )
                        if (snapped != null && draftVertices.none { it.junctionId == snapped.id }) {
                            rememberUndoPoint()
                            draftVertices.add(
                                PastureCoordinate(
                                    latitude = snapped.latitude,
                                    longitude = snapped.longitude,
                                    elevationMeters = snapped.elevationMeters,
                                    elevationSource = snapped.elevationSource,
                                    verticalDatum = snapped.verticalDatum,
                                    verticalAccuracyMeters = snapped.verticalAccuracyMeters,
                                    elevationCapturedAt = snapped.elevationCapturedAt,
                                    junctionId = snapped.id
                                )
                            )
                            activeSnappedJunction = snapped
                            Toast.makeText(context, "Snapped to shared corner", Toast.LENGTH_SHORT).show()
                            selectedVertexIndex = draftVertices.lastIndex
                            draftRevision++
                        } else if (snapped != null) {
                            Toast.makeText(context, "That corner is already in this pasture", Toast.LENGTH_SHORT).show()
                        } else {
                            rememberUndoPoint()
                            draftVertices.add(coordinate.toPastureCoordinate())
                            activeSnappedJunction = null
                            selectedVertexIndex = draftVertices.lastIndex
                            draftRevision++
                        }
                    }
                    InteractionState.PASTURE_EDITING -> {
                        val screenPoint = map.projection.toScreenLocation(coordinate)
                        if (pastureEditMode == PastureEditMode.ADD_CORNER) {
                            val lineHit = queryFeaturesNear(
                                map,
                                screenPoint,
                                28f * context.resources.displayMetrics.density,
                                MapConfig.LAYER_PASTURE_DRAFT_LINE
                            ).isNotEmpty()
                            val projection = GeometryValidator.projectOntoClosestSegment(
                                coordinate.toPastureCoordinate(),
                                draftVertices
                            )
                            if (!lineHit || projection == null) {
                                Toast.makeText(
                                    context,
                                    "Tap closer to the fence line",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val snapped = CornerSnappingEngine.findSnapJunction(
                                    coordinate,
                                    allJunctions,
                                    map.projection,
                                    32f * context.resources.displayMetrics.density
                                )
                                when {
                                    snapped != null && draftVertices.any { it.junctionId == snapped.id } ->
                                        Toast.makeText(
                                            context,
                                            "Cannot join: that corner is already in this pasture",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    snapped != null -> {
                                        pendingJoinJunction = snapped
                                        pendingJoinIndex = projection.insertionIndex
                                        pendingJoinIndependentCoordinate = projection.coordinate
                                        activeSnappedJunction = snapped
                                    }
                                    else -> {
                                        val candidate = draftVertices.toMutableList().apply {
                                            add(projection.insertionIndex, projection.coordinate)
                                        }
                                        val error = GeometryValidator.validationError(candidate)
                                        if (error != null) {
                                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        } else {
                                            rememberUndoPoint()
                                            draftVertices.add(projection.insertionIndex, projection.coordinate)
                                            selectedVertexIndex = projection.insertionIndex
                                            pastureEditMode = PastureEditMode.SELECT_OR_DRAG
                                            draftRevision++
                                            Toast.makeText(context, "Corner added", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        } else {
                            val hit = queryFeaturesNear(
                                map,
                                screenPoint,
                                24f,
                                MapConfig.LAYER_PASTURE_HANDLES
                            ).firstOrNull()
                            selectedVertexIndex = hit?.getNumberProperty("index")?.toInt()
                        }
                    }
                    InteractionState.ORDINARY -> {
                        val screenPoint = map.projection.toScreenLocation(coordinate)
                        val gateHit = if (
                            MapTapSelectionPolicy.useExpandedGateTarget(map.cameraPosition.zoom)
                        ) {
                            // The layer already supplies a 24-pixel target. Querying another
                            // density-scaled box around it made gates consume nearby pastures.
                            map.queryRenderedFeatures(
                                screenPoint,
                                MapConfig.LAYER_GATE_TOUCH_TARGET
                            ).firstOrNull()
                        } else {
                            // At ranch overview zoom, only a deliberate tap on the visible
                            // orange gate marker outranks the pasture polygon beneath it.
                            map.queryRenderedFeatures(
                                screenPoint,
                                MapConfig.LAYER_GATE_OVERVIEW
                            ).firstOrNull()
                        }
                        if (gateHit != null) {
                            selectedGateId = gateHit.getNumberProperty("id").toLong()
                            selectedWaterId = null
                            selectedPastureId = null
                            selectedHerdId = null
                            selectedMovementId = null
                        } else {
                            val waterHit = queryFeaturesNear(
                                map,
                                screenPoint,
                                24f,
                                MapConfig.LAYER_WATER_POINTS
                            ).firstOrNull()
                            if (waterHit != null) {
                                selectedWaterId = waterHit.getNumberProperty("id").toLong()
                                selectedPastureId = null
                                selectedGateId = null
                                selectedHerdId = null
                                selectedMovementId = null
                            } else {
                                val herdHit = queryHerdFeatureAt(map, screenPoint)
                                if (herdHit != null) {
                                    if (herdHit.getBooleanProperty("isOverflow")) {
                                        herdManagerPastureFilterId = herdHit.getNumberProperty("pastureId").toLong()
                                        showHerdManagerDialog = true
                                        selectedHerdId = null
                                    } else {
                                        selectedHerdId = herdHit.getNumberProperty("id").toLong()
                                    }
                                    selectedGateId = null
                                    selectedWaterId = null
                                    selectedPastureId = null
                                    selectedMovementId = null
                                } else {
                                    val pastureHit = map.queryRenderedFeatures(
                                        screenPoint,
                                        MapConfig.LAYER_PASTURE_FILL
                                    ).firstOrNull()
                                    selectedPastureId = pastureHit?.getNumberProperty("id")?.toLong()
                                    selectedWaterId = null
                                    selectedGateId = null
                                    selectedHerdId = null
                                    selectedMovementId = null
                                }
                            }
                        }
                    }
                }
                true
            }
            map.addOnMapClickListener(listener)
            onDispose { map.removeOnMapClickListener(listener) }
        }
    }

    // 🎮 BLOCK 5 — EXPLICIT VERTEX DRAGGING
    DisposableEffect(
        mapView,
        mapInstance,
        interactionState,
        pastureEditMode,
        allJunctions,
        junctionUsageMap,
        approvedSharedMoveJunctionId
    ) {
        val map = mapInstance
        if (map == null || interactionState != InteractionState.PASTURE_EDITING ||
            pastureEditMode != PastureEditMode.SELECT_OR_DRAG
        ) {
            onDispose { }
        } else {
            var dragIndex: Int? = null
            val touchListener = android.view.View.OnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val hit = queryFeaturesNear(
                            map,
                            PointF(event.x, event.y),
                            28f,
                            MapConfig.LAYER_PASTURE_HANDLES
                        ).firstOrNull { it.getStringProperty("handleType") == "vertex" }
                        if (hit == null) {
                            false
                        } else {
                            val index = hit.getNumberProperty("index").toInt()
                            val junctionId = draftVertices.getOrNull(index)?.junctionId
                            val sharedCount = junctionId?.let { junctionUsageMap[it]?.size } ?: 0
                            selectedVertexIndex = index
                            if (sharedCount > 1 && approvedSharedMoveJunctionId != junctionId) {
                                pendingSharedMoveJunctionId = junctionId
                                true
                            } else {
                                dragIndex = index
                                rememberUndoPoint()
                                map.uiSettings.isScrollGesturesEnabled = false
                                true
                            }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val index = dragIndex
                        if (index == null || index !in draftVertices.indices) {
                            false
                        } else {
                            val moved = map.projection.fromScreenLocation(PointF(event.x, event.y))
                            val existing = draftVertices[index]
                            val movedCoordinate = existing.copy(
                                latitude = moved.latitude,
                                longitude = moved.longitude
                            )
                            draftVertices[index] = movedCoordinate
                            existing.junctionId?.let { ephemeralJunctionMoves[it] = movedCoordinate }
                            draftRevision++
                            true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (dragIndex == null) {
                            false
                        } else {
                            val completedIndex = dragIndex
                            val completed = completedIndex?.let { draftVertices.getOrNull(it) }
                            if (completedIndex != null && completed != null && completed.junctionId == null) {
                                val snap = CornerSnappingEngine.findSnapJunction(
                                    completed.toLatLng(),
                                    allJunctions,
                                    map.projection,
                                    32f * context.resources.displayMetrics.density
                                )
                                if (snap != null && draftVertices.none { it.junctionId == snap.id }) {
                                    pendingJoinJunction = snap
                                    pendingJoinIndex = completedIndex
                                    pendingJoinIndependentCoordinate = completed
                                    activeSnappedJunction = snap
                                }
                            }
                            dragIndex = null
                            approvedSharedMoveJunctionId = null
                            map.uiSettings.isScrollGesturesEnabled = true
                            true
                        }
                    }
                    else -> dragIndex != null
                }
            }
            mapView.setOnTouchListener(touchListener)
            onDispose {
                mapView.setOnTouchListener(null)
                map.uiSettings.isScrollGesturesEnabled = true
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        mapInstance = map
                        map.setMinZoomPreference(MapConfig.MIN_ALLOWED_ZOOM)
                        map.setMaxZoomPreference(MapConfig.MAX_AERIAL_ZOOM)
                        map.setStyle(MapConfig.createStyleBuilder()) {
                            MapMarkerIcons.register(it)
                            val savedCamera = cameraStateRepository.load()
                            map.cameraPosition = if (savedCamera == null) {
                                CameraPosition.Builder()
                                    .target(LatLng(initialLat, initialLng))
                                    .zoom(initialZoom)
                                    .build()
                            } else {
                                initialViewportApplied = true
                                CameraPosition.Builder()
                                    .target(LatLng(savedCamera.latitude, savedCamera.longitude))
                                    .zoom(savedCamera.zoom)
                                    .bearing(savedCamera.bearing)
                                    .tilt(savedCamera.tilt)
                                    .build()
                            }
                            applyMapMode(map, activeMode)
                            applyLayerVisibility(
                                map = map,
                                preferences = displayPreferences,
                                isMovingWater = interactionState == InteractionState.WATER_MOVING,
                                isEditingWater = interactionState == InteractionState.WATER_PLACEMENT ||
                                    interactionState == InteractionState.WATER_MOVING,
                                isEditingPasture = interactionState == InteractionState.PASTURE_DRAWING ||
                                    interactionState == InteractionState.PASTURE_EDITING,
                                isEditingGate = interactionState == InteractionState.GATE_PLACEMENT ||
                                    interactionState == InteractionState.GATE_MOVING
                            )
                            pushAllOverlays(
                                map = map,
                                waterPoints = focusedWaterPoints,
                                selectedWaterId = selectedWaterId,
                                pastures = effectivePastures,
                                selectedPastureId = selectedPastureId,
                                gates = focusedGates,
                                boundaryGates = displayedGates,
                                selectedGateId = selectedGateId,
                                herds = focusedHerds,
                                selectedHerdId = selectedHerdId,
                                focusedPastureId = herdFocus?.pastureId,
                                focusedPastureIds = herdFocus?.pastureIds.orEmpty(),
                                focusColorHex = herdFocus?.colorHex,
                                activeMovement = activeMovement,
                                draftVertices = draftVertices.toList(),
                                selectedVertexIndex = selectedVertexIndex,
                                coverageScope = displayPreferences.coverageScope,
                                assignments = assignmentMap,
                                sharedJunctionIds = junctionUsageMap.filterValues { it.size > 1 }.keys,
                                activeSnappedJunction = activeSnappedJunction
                            )
                            isMapStyleReady = true
                        }
                        map.addOnCameraMoveListener { currentZoom = map.cameraPosition.zoom }
                        map.addOnCameraIdleListener {
                            currentZoom = map.cameraPosition.zoom
                            cameraStateRepository.save(map.cameraPosition.toSavedMapCamera())
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        MapStatusAndModeControls(
            activeMode = activeMode,
            currentZoom = currentZoom,
            isMapRendering = isMapRendering,
            hasLoadError = hasLoadError,
            interactionState = interactionState,
            coverageMode = displayPreferences.coverageMode,
            pastureFillEnabled = displayPreferences.pastureFillEnabled,
            focusedHerdName = focusedHerd?.takeIf { herdFocus != null }?.let { herd ->
                val circuitId = herdCircuitAssignments.firstOrNull { it.herdId == herd.id }?.circuitId
                val circuitName = grazingCircuits.firstOrNull { it.id == circuitId }?.name
                if (circuitName == null) herd.name else "${herd.name} • $circuitName"
            },
            focusedHerdColorHex = focusedHerd?.takeIf { herdFocus != null }?.markerColorHex,
            onModeSelected = { mode ->
                activeMode = mode
                mapInstance?.let { applyMapMode(it, mode) }
            },
            onLayersClick = { showLayersDialog = true },
            onDataClick = { showDataSafetyDialog = true },
            onHerdsClick = {
                herdManagerPastureFilterId = null
                showHerdManagerDialog = true
            }
        )

        if (
            interactionState == InteractionState.ORDINARY &&
            selectedWater == null && selectedPasture == null && selectedGateId == null && selectedHerd == null
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 56.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.82f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            interactionState = InteractionState.WATER_PLACEMENT
                            selectedWaterId = null
                            selectedPastureId = null
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                    ) { Text("Add Water", fontSize = 12.sp, maxLines = 1) }
                    Button(
                        onClick = {
                            interactionState = InteractionState.PASTURE_DRAWING
                            selectedWaterId = null
                            selectedPastureId = null
                            replaceDraft(emptyList())
                            undoSnapshots.clear()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D95))
                    ) { Text("Draw Pasture", fontSize = 12.sp, maxLines = 1) }
                }
            }
        }

        if (interactionState == InteractionState.GATE_PLACEMENT) {
            GatePlacementCard(
                candidate = candidateGate,
                onCancel = {
                    candidateGate = null
                    interactionState = InteractionState.ORDINARY
                },
                onSave = {
                    candidateGate?.let { candidate ->
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    gateDao.insertValidatedGate(
                                        GateEntity(
                                            name = "",
                                            junctionAId = candidate.junctionA.id,
                                            junctionBId = candidate.junctionB.id,
                                            segmentRatio = candidate.segmentRatio
                                        )
                                    )
                                }
                                candidateGate = null
                                interactionState = InteractionState.ORDINARY
                            } catch (error: IllegalArgumentException) {
                                Toast.makeText(
                                    context,
                                    error.message ?: "Failed to place gate",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )
        }

        if (interactionState == InteractionState.GATE_MOVING) {
            GateMoveControls(
                gate = movingGate,
                candidate = draftGateMoveCandidate,
                onCancel = {
                    movingGate = null
                    draftGateMoveCandidate = null
                    interactionState = InteractionState.ORDINARY
                },
                onSave = {
                    val gate = movingGate
                    val candidate = draftGateMoveCandidate
                    if (gate != null && candidate != null) {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    gateDao.updateValidatedPosition(
                                        id = gate.id,
                                        junctionAId = candidate.junctionA.id,
                                        junctionBId = candidate.junctionB.id,
                                        segmentRatio = candidate.segmentRatio
                                    )
                                }
                                movingGate = null
                                draftGateMoveCandidate = null
                                interactionState = InteractionState.ORDINARY
                                Toast.makeText(context, "Gate position saved", Toast.LENGTH_SHORT).show()
                            } catch (error: Exception) {
                                Toast.makeText(
                                    context,
                                    error.message ?: "Failed to move gate",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )
        }

        if (interactionState == InteractionState.WATER_PLACEMENT) {
            BottomInstruction(
                text = "Tap pasture to place water",
                action = "Cancel",
                onAction = { interactionState = InteractionState.ORDINARY }
            )
        }

        if (interactionState == InteractionState.WATER_MOVING) {
            WaterMoveControls(
                draftLocation = draftWaterLocation,
                onCancel = {
                    draftWaterLocation = null
                    movingWaterPoint = null
                    interactionState = InteractionState.ORDINARY
                },
                onSave = {
                    val original = movingWaterPoint
                    val location = draftWaterLocation
                    if (original != null && location != null) {
                        scope.launch {
                            waterDao.update(
                                original.copy(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            draftWaterLocation = null
                            movingWaterPoint = null
                            interactionState = InteractionState.ORDINARY
                        }
                    }
                }
            )
        }

        if (interactionState == InteractionState.PASTURE_DRAWING ||
            interactionState == InteractionState.PASTURE_EDITING
        ) {
            PastureGeometryControls(
                acres = AcreageCalculator.calculateAcres(draftVertices),
                canUndo = undoSnapshots.isNotEmpty(),
                canFinish = draftVertices.distinctBy { it.latitude to it.longitude }.size >= 3,
                canRemove = selectedVertexIndex != null && draftVertices.size > 3,
                editing = interactionState == InteractionState.PASTURE_EDITING,
                addCornerArmed = pastureEditMode == PastureEditMode.ADD_CORNER,
                selectedCorner = selectedVertexIndex?.plus(1),
                onUndo = {
                    if (undoSnapshots.isNotEmpty()) {
                        val previous = undoSnapshots.removeAt(undoSnapshots.lastIndex)
                        replaceDraft(previous.vertices)
                        ephemeralJunctionMoves.clear()
                        ephemeralJunctionMoves.putAll(previous.junctionMoves)
                        selectedVertexIndex = null
                        pastureEditMode = PastureEditMode.SELECT_OR_DRAG
                    }
                },
                onAddCorner = {
                    pastureEditMode = if (pastureEditMode == PastureEditMode.ADD_CORNER) {
                        PastureEditMode.SELECT_OR_DRAG
                    } else {
                        selectedVertexIndex = null
                        PastureEditMode.ADD_CORNER
                    }
                },
                onMove = {
                    val junctionId = selectedVertexIndex?.let { draftVertices.getOrNull(it)?.junctionId }
                    val sharedCount = junctionId?.let { junctionUsageMap[it]?.size } ?: 0
                    if (junctionId != null && sharedCount > 1) {
                        pendingSharedMoveJunctionId = junctionId
                    } else {
                        approvedSharedMoveJunctionId = junctionId
                        Toast.makeText(context, "Drag the selected corner to move it", Toast.LENGTH_SHORT).show()
                    }
                },
                onMerge = {
                    val coordinate = selectedVertexIndex?.let { draftVertices.getOrNull(it) }
                    when {
                        coordinate?.junctionId == null -> Toast.makeText(
                            context,
                            "Save this corner before merging",
                            Toast.LENGTH_SHORT
                        ).show()
                        undoSnapshots.isNotEmpty() || ephemeralJunctionMoves.isNotEmpty() -> Toast.makeText(
                            context,
                            "Save or cancel the current boundary changes before merging",
                            Toast.LENGTH_LONG
                        ).show()
                        else -> {
                            mergeCandidates = CornerSnappingEngine.findNearbyJunctions(
                                coordinate.toLatLng(),
                                allJunctions,
                                coordinate.junctionId,
                                50.0
                            )
                            selectedMergeTargetId = null
                            showMergeDialog = true
                        }
                    }
                },
                onRemove = {
                    val index = selectedVertexIndex
                    if (index != null && index in draftVertices.indices && draftVertices.size > 3) {
                        rememberUndoPoint()
                        draftVertices.removeAt(index)
                        selectedVertexIndex = null
                        pastureEditMode = PastureEditMode.SELECT_OR_DRAG
                        draftRevision++
                    }
                },
                onCancel = { leaveGeometryMode() },
                onFinish = {
                    val error = geometryError()
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    } else if (interactionState == InteractionState.PASTURE_DRAWING) {
                        pastureNameInput = ""
                        showPastureNameDialog = true
                    } else {
                        val pastureId = selectedPastureId
                        if (pastureId != null) {
                            val invalidConnected = effectivePastures.firstOrNull { pasture ->
                                pasture.pasture.id != pastureId &&
                                    pasture.vertices.any { it.junctionId in ephemeralJunctionMoves.keys } &&
                                    GeometryValidator.validationError(pasture.orderedCoordinates()) != null
                            }
                            if (invalidConnected != null) {
                                Toast.makeText(
                                    context,
                                    "Invalid: moving this corner breaks ${invalidConnected.pasture.name}",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@PastureGeometryControls
                            }
                            scope.launch {
                                try {
                                    pastureDao.savePastureBoundaryWithJunctions(
                                        pastureId = pastureId,
                                        vertices = draftVertices.toList(),
                                        junctionMoves = ephemeralJunctionMoves.toMap()
                                    )
                                    leaveGeometryMode()
                                } catch (error: IllegalStateException) {
                                    Toast.makeText(
                                        context,
                                        error.message ?: "Failed to save pasture boundary",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                }
            )
        }

        if (interactionState == InteractionState.ORDINARY) {
            selectedWater?.let { point ->
                WaterInspectionCard(
                    point = point,
                    assignedPastureCount = assignmentMap[point.id].orEmpty().size,
                    onClose = { selectedWaterId = null },
                    onMove = {
                        movingWaterPoint = point
                        draftWaterLocation = null
                        selectedPastureId = null
                        interactionState = InteractionState.WATER_MOVING
                    },
                    onEdit = {
                        waterEditName = point.name
                        waterEditNotes = point.notes
                        waterEditType = point.sourceType
                        showWaterEditDialog = true
                    },
                    onAssignPastures = {
                        assignmentDraftIds.clear()
                        assignmentDraftIds.addAll(assignmentMap[point.id].orEmpty())
                        showAssignPasturesDialog = true
                    },
                    onDelete = { showWaterDeleteDialog = true }
                )
            }
        }

        selectedPasture?.let { pasture ->
            if (interactionState == InteractionState.ORDINARY) {
                PastureInspectionCard(
                    pasture = pasture,
                    metrics = selectedPastureMetrics ?: return@let,
                    restStatus = selectedPastureRestStatus,
                    latestForageObservation = selectedPastureLatestForage,
                    isStockpiledWinter = selectedPastureIsStockpiledWinter,
                    onClose = { selectedPastureId = null },
                    onAddGate = {
                        candidateGate = null
                        selectedGateId = null
                        interactionState = InteractionState.GATE_PLACEMENT
                    },
                    onEditDetails = {
                        pastureEditName = pasture.pasture.name
                        pastureEditNotes = pasture.pasture.notes
                        showPastureDetailsDialog = true
                    },
                    onRecordForage = { showForageObservationDialog = true },
                    onEditBoundary = {
                        interactionState = InteractionState.PASTURE_EDITING
                        replaceDraft(pasture.orderedCoordinates())
                        undoSnapshots.clear()
                        ephemeralJunctionMoves.clear()
                        selectedVertexIndex = null
                        pastureEditMode = PastureEditMode.SELECT_OR_DRAG
                    },
                    onDelete = { showPastureDeleteDialog = true }
                )
            }
        }

        if (interactionState == InteractionState.ORDINARY) {
            resolvedGates.firstOrNull { it.gate.id == selectedGateId }?.let { item ->
                GateInspectionCard(
                    item = item,
                    onClose = { selectedGateId = null },
                    onToggleStatus = {
                        scope.launch {
                            val next = if (item.gate.status == GateEntity.STATUS_CLOSED) {
                                GateEntity.STATUS_OPEN
                            } else {
                                GateEntity.STATUS_CLOSED
                            }
                            gateDao.updateStatus(item.gate.id, next)
                        }
                    },
                    onMove = {
                        movingGate = item.gate
                        draftGateMoveCandidate = null
                        interactionState = InteractionState.GATE_MOVING
                    },
                    onEdit = { showGateEditDialog = true },
                    onDelete = { showGateDeleteDialog = true }
                )
            }
        }

        if (interactionState == InteractionState.ORDINARY && selectedHerd != null) {
            val herd = selectedHerd
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E1E)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(herd.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${herd.quantity} ${if (herd.countUnit == CountUnit.PAIRS) "Pairs" else "Head"}",
                            color = Color(android.graphics.Color.parseColor(herd.markerColorHex)),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Text(herd.stockClass.displayName, color = Color.LightGray, fontSize = 12.sp)
                    val detailParts = buildList {
                        herd.averageWeightLbs?.let { weight ->
                            val formatted = if (weight % 1.0 == 0.0) weight.toInt().toString() else weight.toString()
                            add("Avg weight: $formatted lb")
                        }
                        herd.shortMarkerLabel?.takeIf { it.isNotBlank() }?.let { add("Marker: $it") }
                    }
                    if (detailParts.isNotEmpty()) {
                        Text(detailParts.joinToString(" • "), color = Color.LightGray, fontSize = 11.sp)
                    }
                    val location = pastures.find { it.pasture.id == herd.currentPastureId }?.pasture?.name
                        ?: herd.locationKind.name
                    Text("Location: $location", color = Color(0xFF00E5FF), fontSize = 12.sp)
                    val assignedCircuitId = herdCircuitAssignments.firstOrNull { it.herdId == herd.id }?.circuitId
                    val assignedCircuit = grazingCircuits.firstOrNull { it.id == assignedCircuitId }
                    val assignedCircuitMembers = circuitPastures
                        .filter { it.circuitId == assignedCircuitId }
                        .sortedBy { it.sequence }
                    val circuitPosition = assignedCircuitMembers.indexOfFirst { it.pastureId == herd.currentPastureId }
                    Text(
                        "Grazing Circuit: ${assignedCircuit?.name ?: "None"}",
                        color = Color(0xFFFFD54F),
                        fontSize = 12.sp
                    )
                    if (assignedCircuit != null) {
                        Text(
                            if (circuitPosition >= 0) {
                                "${assignedCircuitMembers.size} pastures • Current position ${circuitPosition + 1}"
                            } else {
                                "${assignedCircuitMembers.size} pastures"
                            },
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        if (herd.locationKind == HerdLocationKind.PASTURE && circuitPosition < 0) {
                            Text(
                                "This herd is currently outside its assigned Grazing Circuit.",
                                color = Color(0xFFFF9100),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (herd.notes.isNotBlank()) Text(herd.notes, color = Color.Gray, fontSize = 11.sp)
                    if (herd.locationKind == HerdLocationKind.PASTURE && herd.currentPastureId != null) {
                        val focusActive = focusedHerdId == herd.id
                        Button(
                            onClick = { focusedHerdId = if (focusActive) null else herd.id },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (focusActive) {
                                    Color(0xFF424242)
                                } else {
                                    Color(android.graphics.Color.parseColor(herd.markerColorHex))
                                },
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp)
                        ) {
                            Text(if (focusActive) "Clear Map Focus" else "Focus Map on This Herd", fontSize = 11.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Button(
                            onClick = { showPlanMoveDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 6.dp)
                        ) { Text("Move", fontSize = 11.sp) }
                        OutlinedButton(
                            onClick = { herdBeingEdited = herd; showHerdCreateEditDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 6.dp)
                        ) { Text("Edit", fontSize = 11.sp) }
                        OutlinedButton(
                            onClick = { showMoveHistoryDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 6.dp)
                        ) { Text("History", fontSize = 11.sp) }
                        OutlinedButton(
                            onClick = { showAssignCircuitDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 6.dp)
                        ) { Text("Circuit", fontSize = 10.sp) }
                        OutlinedButton(
                            onClick = { showArchiveHerdConfirmDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 3.dp, vertical = 6.dp)
                        ) { Text("Archive", color = Color.Red, fontSize = 10.sp) }
                    }
                }
            }
        }

        ActiveAttribution(activeMode, currentZoom)
    }

    if (showHerdManagerDialog) {
        val displayedHerds = herdManagerPastureFilterId?.let { pastureId ->
            herds.filter { it.currentPastureId == pastureId }
        } ?: herds
        AlertDialog(
            onDismissRequest = { showHerdManagerDialog = false; herdManagerPastureFilterId = null },
            title = {
                val pastureName = pastures.find { it.pasture.id == herdManagerPastureFilterId }?.pasture?.name
                Text(pastureName?.let { "Herds in $it" } ?: "Herds & Grazing Groups")
            },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { herdBeingEdited = null; showHerdCreateEditDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("+ Create New Herd") }
                    OutlinedButton(
                        onClick = {
                            showHerdManagerDialog = false
                            showCircuitManagerDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Manage Grazing Circuits") }
                    Spacer(Modifier.width(8.dp))
                    if (displayedHerds.isEmpty()) {
                        Text("No active herds in this view.", color = Color.Gray)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(displayedHerds) { herd ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                        selectedHerdId = herd.id
                                        selectedMovementId = null
                                        showHerdManagerDialog = false
                                        herdManagerPastureFilterId = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF252525)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(herd.name, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("${herd.quantity} ${herd.countUnit.name} • ${herd.stockClass.displayName}", color = Color.LightGray, fontSize = 11.sp)
                                        }
                                        Text(herd.shortMarkerLabel ?: "●", color = Color(android.graphics.Color.parseColor(herd.markerColorHex)))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHerdManagerDialog = false; herdManagerPastureFilterId = null }) { Text("Close") }
            }
        )
    }

    if (showCircuitManagerDialog) {
        GrazingCircuitManagerDialog(
            circuits = grazingCircuits,
            memberships = circuitPastures,
            pastures = pastures,
            onCreate = {
                editingCircuitId = null
                showCircuitEditorDialog = true
            },
            onEdit = { circuitId ->
                editingCircuitId = circuitId
                showCircuitEditorDialog = true
            },
            onArchive = { circuitId ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { database.grazingCircuitDao().archiveCircuit(circuitId) }
                    } catch (error: Exception) {
                        Toast.makeText(context, error.message ?: "Could not archive circuit", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onClose = { showCircuitManagerDialog = false }
        )
    }

    if (showCircuitEditorDialog) {
        val editingCircuit = grazingCircuits.firstOrNull { it.id == editingCircuitId }
        GrazingCircuitEditorDialog(
            circuit = editingCircuit,
            memberships = circuitPastures.filter { it.circuitId == editingCircuitId },
            roles = circuitRoles.filter { it.circuitId == editingCircuitId },
            pastures = pastures,
            onDismiss = { showCircuitEditorDialog = false },
            onSave = { name, notes, drafts ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            if (editingCircuit == null) {
                                database.grazingCircuitDao().createCircuit(name, notes, drafts)
                            } else {
                                database.grazingCircuitDao().updateCircuit(editingCircuit.id, name, notes, drafts)
                            }
                        }
                        showCircuitEditorDialog = false
                    } catch (error: Exception) {
                        Toast.makeText(context, error.message ?: "Could not save circuit", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showAssignCircuitDialog && selectedHerd != null) {
        HerdCircuitAssignmentDialog(
            herd = selectedHerd,
            circuits = grazingCircuits,
            assignedCircuitId = herdCircuitAssignments.firstOrNull { it.herdId == selectedHerd.id }?.circuitId,
            onDismiss = { showAssignCircuitDialog = false },
            onAssign = { circuitId ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            database.grazingCircuitDao().assignHerd(selectedHerd.id, circuitId)
                        }
                        showAssignCircuitDialog = false
                    } catch (error: Exception) {
                        Toast.makeText(context, error.message ?: "Could not assign circuit", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showForageObservationDialog && selectedPasture != null && selectedPastureMetrics != null) {
        ForageObservationDialog(
            pastureName = selectedPasture.pasture.name,
            acreage = selectedPastureMetrics.totalAcreage,
            onDismiss = { showForageObservationDialog = false },
            onSave = { observation ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { database.forageObservationDao().record(observation) }
                        showForageObservationDialog = false
                    } catch (error: Exception) {
                        Toast.makeText(context, error.message ?: "Could not record forage", Toast.LENGTH_LONG).show()
                    }
                }
            },
            pastureId = selectedPasture.pasture.id
        )
    }

    if (showHerdCreateEditDialog) {
        val editing = herdBeingEdited
        var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
        var quantityText by remember(editing?.id) { mutableStateOf(editing?.quantity?.toString().orEmpty()) }
        var countUnit by remember(editing?.id) { mutableStateOf(editing?.countUnit ?: CountUnit.HEAD) }
        var stockClass by remember(editing?.id) { mutableStateOf(editing?.stockClass ?: StockClass.COW_CALF_PAIRS) }
        var weightText by remember(editing?.id) { mutableStateOf(editing?.averageWeightLbs?.toString().orEmpty()) }
        var color by remember(editing?.id) { mutableStateOf(editing?.markerColorHex ?: "#FF9100") }
        var shortLabel by remember(editing?.id) { mutableStateOf(editing?.shortMarkerLabel.orEmpty()) }
        var notes by remember(editing?.id) { mutableStateOf(editing?.notes.orEmpty()) }
        var locationKind by remember(editing?.id) { mutableStateOf(editing?.locationKind ?: HerdLocationKind.PASTURE) }
        var pastureId by remember(editing?.id) { mutableStateOf(editing?.currentPastureId ?: pastures.firstOrNull()?.pasture?.id) }
        val colors = listOf("#FF9100", "#00E5FF", "#76FF03", "#FF2D95", "#D500F9", "#FFD600")
        AlertDialog(
            onDismissRequest = { showHerdCreateEditDialog = false },
            title = { Text(if (editing == null) "Create Herd" else "Edit Herd Details") },
            text = {
                LazyColumn(Modifier.fillMaxWidth()) {
                    item {
                        OutlinedTextField(name, { name = it }, label = { Text("Herd name") }, singleLine = true)
                        OutlinedTextField(quantityText, { quantityText = it }, label = { Text("Quantity") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CountUnit.entries.forEach { unit ->
                                FilterChip(countUnit == unit, { countUnit = unit }, label = { Text(unit.name) })
                            }
                        }
                        Text("Stock class", color = Color.Gray, fontSize = 12.sp)
                        StockClass.entries.forEach { value ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(stockClass == value, { stockClass = value })
                                Text(value.displayName, fontSize = 12.sp)
                            }
                        }
                        OutlinedTextField(weightText, { weightText = it }, label = { Text("Average weight (lbs, optional)") }, singleLine = true)
                        Text("Marker color", color = Color.Gray, fontSize = 12.sp)
                        colors.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { value ->
                                    val selected = color == value
                                    Surface(
                                        modifier = Modifier.size(42.dp).clickable { color = value },
                                        shape = CircleShape,
                                        color = Color(android.graphics.Color.parseColor(value)),
                                        border = BorderStroke(
                                            if (selected) 4.dp else 2.dp,
                                            if (selected) Color.White else Color(0xFF333333)
                                        )
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (selected) {
                                                Text("✓", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(shortLabel, { if (it.length <= 4) shortLabel = it }, label = { Text("Short label (max 4)") }, singleLine = true)
                        if (editing != null) {
                            val currentLocation = pastures.find { it.pasture.id == editing.currentPastureId }?.pasture?.name ?: editing.locationKind.name
                            Text("Current location: $currentLocation", color = Color(0xFF00E5FF), fontSize = 12.sp)
                            Text("Use Move to change location and preserve history.", color = Color.Gray, fontSize = 10.sp)
                        } else {
                            Text("Initial location", color = Color.Gray, fontSize = 12.sp)
                            HerdLocationKind.entries.forEach { value ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(locationKind == value, { locationKind = value })
                                    Text(value.name, fontSize = 12.sp)
                                }
                            }
                            if (locationKind == HerdLocationKind.PASTURE) {
                                Text("Pasture", color = Color.Gray, fontSize = 12.sp)
                                pastures.forEach { pasture ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(pastureId == pasture.pasture.id, { pastureId = pasture.pasture.id })
                                        Text(pasture.pasture.name, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val quantity = quantityText.toIntOrNull()
                    val weight = weightText.takeIf { it.isNotBlank() }?.toDoubleOrNull()
                    if (name.isBlank() || quantity == null || quantity <= 0 || (weightText.isNotBlank() && (weight == null || weight <= 0.0))) {
                        Toast.makeText(context, "Enter a name, positive quantity, and valid optional weight", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (editing == null && locationKind == HerdLocationKind.PASTURE && pastureId == null) {
                        Toast.makeText(context, "Select an initial pasture", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                if (editing != null) {
                                    database.herdDao().updateHerdDetails(editing.id, name, quantity, countUnit, stockClass, weight, color, shortLabel, notes)
                                } else {
                                    database.herdDao().createHerd(
                                        HerdEntity(
                                            name = name.trim(), quantity = quantity, countUnit = countUnit,
                                            stockClass = stockClass, averageWeightLbs = weight,
                                            markerColorHex = color, shortMarkerLabel = shortLabel.trim().ifBlank { null },
                                            notes = notes.trim(), locationKind = locationKind,
                                            currentPastureId = pastureId.takeIf { locationKind == HerdLocationKind.PASTURE }
                                        )
                                    )
                                }
                            }
                            showHerdCreateEditDialog = false
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: "Failed to save herd", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showHerdCreateEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPlanMoveDialog && selectedHerd != null) {
        val herd = selectedHerd
        var destinationKind by remember(herd.id) { mutableStateOf(HerdLocationKind.PASTURE) }
        var destinationPastureId by remember(herd.id) { mutableStateOf(pastures.firstOrNull { it.pasture.id != herd.currentPastureId }?.pasture?.id) }
        var unmappedRoute by remember(herd.id) { mutableStateOf(false) }
        var routeGateId by remember(herd.id) { mutableStateOf<Long?>(null) }
        var completeNow by remember(herd.id) { mutableStateOf(true) }
        var plannedDayOffset by remember(herd.id) { mutableIntStateOf(0) }
        var movementNotes by remember(herd.id) { mutableStateOf("") }
        val validGates = displayedGates.filter { gate ->
            val originId = herd.currentPastureId
            val destinationId = destinationPastureId
            when {
                herd.locationKind == HerdLocationKind.PASTURE && destinationKind == HerdLocationKind.PASTURE ->
                    (gate.pastureAId == originId && gate.pastureBId == destinationId) || (gate.pastureAId == destinationId && gate.pastureBId == originId)
                herd.locationKind == HerdLocationKind.PASTURE -> !gate.isShared && gate.pastureAId == originId
                destinationKind == HerdLocationKind.PASTURE -> !gate.isShared && gate.pastureAId == destinationId
                else -> false
            }
        }
        AlertDialog(
            onDismissRequest = { showPlanMoveDialog = false },
            title = { Text("Move ${herd.name}") },
            text = {
                LazyColumn(Modifier.fillMaxWidth()) {
                    item {
                        Text("Destination", color = Color.Gray, fontSize = 12.sp)
                        listOf(HerdLocationKind.PASTURE, HerdLocationKind.PEN, HerdLocationKind.OFF_RANCH).forEach { value ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(destinationKind == value, { destinationKind = value; routeGateId = null })
                                Text(value.name, fontSize = 12.sp)
                            }
                        }
                        if (destinationKind == HerdLocationKind.PASTURE) {
                            pastures.filter { it.pasture.id != herd.currentPastureId }.forEach { pasture ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(destinationPastureId == pasture.pasture.id, { destinationPastureId = pasture.pasture.id; routeGateId = null })
                                    Text(pasture.pasture.name, fontSize = 12.sp)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(!unmappedRoute, { unmappedRoute = false }, label = { Text("Mapped gate") })
                            FilterChip(unmappedRoute, { unmappedRoute = true; routeGateId = null }, label = { Text("Unmapped") })
                        }
                        if (!unmappedRoute) {
                            if (validGates.isEmpty()) Text("No valid mapped gate for this route.", color = Color(0xFFFFD600), fontSize = 11.sp)
                            validGates.forEach { gate ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(routeGateId == gate.gate.id, { routeGateId = gate.gate.id })
                                    Text(gate.gate.name, fontSize = 12.sp)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(completeNow, { completeNow = true }, label = { Text("Log now") })
                            FilterChip(!completeNow, { completeNow = false }, label = { Text("Schedule") })
                        }
                        if (!completeNow) {
                            listOf(0 to "Today", 1 to "+1 day", 3 to "+3 days", 7 to "+7 days").forEach { (days, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(plannedDayOffset == days, { plannedDayOffset = days })
                                    Text(label, fontSize = 12.sp)
                                }
                            }
                        }
                        OutlinedTextField(movementNotes, { movementNotes = it }, label = { Text(if (unmappedRoute) "Route notes (required)" else "Notes") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (unmappedRoute && movementNotes.isBlank()) {
                        Toast.makeText(context, "Unmapped routes require explanatory notes", Toast.LENGTH_LONG).show(); return@Button
                    }
                    if (!unmappedRoute && routeGateId == null) {
                        Toast.makeText(context, "Select a valid gate or choose Unmapped", Toast.LENGTH_LONG).show(); return@Button
                    }
                    val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, plannedDayOffset) }
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                database.movementDao().scheduleOrLogMovement(
                                    herd.id, destinationKind,
                                    destinationPastureId.takeIf { destinationKind == HerdLocationKind.PASTURE },
                                    routeGateId.takeUnless { unmappedRoute }, unmappedRoute,
                                    calendar.timeInMillis, completeNow, movementNotes
                                )
                            }
                            showPlanMoveDialog = false
                        } catch (error: Exception) {
                            Toast.makeText(context, error.message ?: "Failed to record movement", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showPlanMoveDialog = false }) { Text("Cancel") } }
        )
    }

    if (showMoveHistoryDialog && selectedHerd != null) {
        val herdMovements = movements.filter { it.herdId == selectedHerd.id }
        AlertDialog(
            onDismissRequest = { showMoveHistoryDialog = false },
            title = { Text("Movement History & Plans") },
            text = {
                if (herdMovements.isEmpty()) Text("No recorded movements.", color = Color.Gray) else LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(herdMovements) { movement ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                selectedMovementId = movement.id
                                showMoveHistoryDialog = false
                            },
                            shape = RoundedCornerShape(8.dp), color = Color(0xFF252525)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("${movement.originNameSnapshot} → ${movement.destinationNameSnapshot}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${movement.status} • ${movement.gateSnapshot ?: "Unmapped route"}", color = Color.LightGray, fontSize = 11.sp)
                                if (movement.status == MovementStatus.PLANNED) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(onClick = {
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { database.movementDao().completePlannedMovement(movement.id, System.currentTimeMillis(), null) }
                                                } catch (error: Exception) { Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() }
                                            }
                                        }) { Text("Complete", fontSize = 10.sp) }
                                        OutlinedButton(onClick = {
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { database.movementDao().cancelPlannedMovement(movement.id) }
                                                } catch (error: Exception) { Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() }
                                            }
                                        }) { Text("Cancel", fontSize = 10.sp, color = Color.Red) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMoveHistoryDialog = false }) { Text("Close") } }
        )
    }

    if (showArchiveHerdConfirmDialog && selectedHerd != null) {
        AlertDialog(
            onDismissRequest = { showArchiveHerdConfirmDialog = false },
            title = { Text("Archive Herd?") },
            text = { Text("Archive '${selectedHerd.name}'? Its location will be cleared while movement history remains available.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { database.herdDao().archiveHerdWithChecks(selectedHerd.id) }
                            selectedHerdId = null
                            showArchiveHerdConfirmDialog = false
                        } catch (error: Exception) { Toast.makeText(context, error.message, Toast.LENGTH_LONG).show() }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { showArchiveHerdConfirmDialog = false }) { Text("Cancel") } }
        )
    }

    pendingSharedMoveJunctionId?.let { junctionId ->
        val count = junctionUsageMap[junctionId]?.size ?: 1
        AlertDialog(
            onDismissRequest = { pendingSharedMoveJunctionId = null },
            title = { Text("Move Shared Corner?") },
            text = {
                Text(
                    "This corner is shared by $count pastures. Moving it changes every connected boundary. Proceed?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    approvedSharedMoveJunctionId = junctionId
                    pendingSharedMoveJunctionId = null
                    Toast.makeText(context, "Drag the corner to its new location", Toast.LENGTH_SHORT).show()
                }) { Text("Proceed") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSharedMoveJunctionId = null }) { Text("Cancel") }
            }
        )
    }

    pendingJoinJunction?.let { target ->
        AlertDialog(
            onDismissRequest = {
                pendingJoinJunction = null
                pendingJoinIndex = null
                pendingJoinIndependentCoordinate = null
                activeSnappedJunction = null
            },
            title = { Text("Join Shared Corner?") },
            text = {
                Text(
                    "Join this fence corner to junction #${target.id}? The connected pastures will use the same permanent corner."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val index = pendingJoinIndex
                    if (index != null) {
                        val targetCoordinate = PastureCoordinate(
                            latitude = target.latitude,
                            longitude = target.longitude,
                            elevationMeters = target.elevationMeters,
                            elevationSource = target.elevationSource,
                            verticalDatum = target.verticalDatum,
                            verticalAccuracyMeters = target.verticalAccuracyMeters,
                            elevationCapturedAt = target.elevationCapturedAt,
                            junctionId = target.id
                        )
                        val candidate = draftVertices.toMutableList()
                        if (pastureEditMode == PastureEditMode.ADD_CORNER) {
                            candidate.add(index, targetCoordinate)
                        } else if (index in candidate.indices) {
                            candidate[index] = targetCoordinate
                        }
                        val error = GeometryValidator.validationError(candidate)
                        if (error != null) {
                            Toast.makeText(context, "Cannot join: $error", Toast.LENGTH_LONG).show()
                        } else {
                            rememberUndoPoint()
                            replaceDraft(candidate)
                            selectedVertexIndex = index
                            pastureEditMode = PastureEditMode.SELECT_OR_DRAG
                            Toast.makeText(context, "Joined to shared corner", Toast.LENGTH_SHORT).show()
                        }
                    }
                    pendingJoinJunction = null
                    pendingJoinIndex = null
                    pendingJoinIndependentCoordinate = null
                    activeSnappedJunction = null
                }) { Text("Confirm Join") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val index = pendingJoinIndex
                    val independent = pendingJoinIndependentCoordinate
                    if (
                        pastureEditMode == PastureEditMode.ADD_CORNER &&
                        index != null && independent != null
                    ) {
                        val candidate = draftVertices.toMutableList().apply { add(index, independent) }
                        val error = GeometryValidator.validationError(candidate)
                        if (error == null) {
                            rememberUndoPoint()
                            replaceDraft(candidate)
                            selectedVertexIndex = index
                        } else {
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    }
                    pendingJoinJunction = null
                    pendingJoinIndex = null
                    pendingJoinIndependentCoordinate = null
                    activeSnappedJunction = null
                    pastureEditMode = PastureEditMode.SELECT_OR_DRAG
                }) { Text("Keep Independent") }
            }
        )
    }

    if (showMergeDialog) {
        val source = selectedVertexIndex?.let { draftVertices.getOrNull(it) }
        val sourceId = source?.junctionId
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text("Merge Nearby Corners") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose the surviving corner within 50 meters.", fontSize = 12.sp)
                    if (mergeCandidates.isEmpty()) {
                        Text("No nearby corners found.", color = Color.Gray)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(mergeCandidates) { (candidate, distance) ->
                                val pastureNames = junctionUsageMap[candidate.id].orEmpty().mapNotNull { id ->
                                    pastures.firstOrNull { it.pasture.id == id }?.pasture?.name
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { selectedMergeTargetId = candidate.id },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedMergeTargetId == candidate.id) {
                                        Color(0xFF1E3A5F)
                                    } else {
                                        Color(0xFF2C2C2C)
                                    }
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            "Corner #${candidate.id} • ${String.format(Locale.US, "%.1f m", distance)}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Pastures: ${pastureNames.ifEmpty { listOf("None") }.joinToString()}",
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = sourceId != null && selectedMergeTargetId != null,
                    onClick = {
                        val targetId = selectedMergeTargetId
                        val target = allJunctions.firstOrNull { it.id == targetId }
                        val sourcePastureIds = sourceId?.let { junctionUsageMap[it].orEmpty() }.orEmpty()
                        val targetPastureIds = targetId?.let { junctionUsageMap[it].orEmpty() }.orEmpty()
                        when {
                            sourceId == null || targetId == null || target == null -> Unit
                            sourcePastureIds.intersect(targetPastureIds).isNotEmpty() -> Toast.makeText(
                                context,
                                "Cannot merge: a pasture already uses both corners",
                                Toast.LENGTH_LONG
                            ).show()
                            else -> {
                                val invalid = pastures.firstOrNull { pasture ->
                                    pasture.pasture.id in sourcePastureIds &&
                                        GeometryValidator.validationError(
                                            pasture.orderedCoordinates().map { coordinate ->
                                                if (coordinate.junctionId == sourceId) {
                                                    coordinate.copy(
                                                        latitude = target.latitude,
                                                        longitude = target.longitude,
                                                        junctionId = target.id
                                                    )
                                                } else coordinate
                                            }
                                        ) != null
                                }
                                if (invalid != null) {
                                    Toast.makeText(
                                        context,
                                        "Cannot merge: ${invalid.pasture.name} would become invalid",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    scope.launch {
                                        try {
                                            pastureDao.mergeJunctions(sourceId, targetId)
                                            showMergeDialog = false
                                            selectedMergeTargetId = null
                                            leaveGeometryMode()
                                            Toast.makeText(context, "Corners merged", Toast.LENGTH_SHORT).show()
                                        } catch (error: IllegalStateException) {
                                            Toast.makeText(
                                                context,
                                                error.message ?: "Failed to merge corners",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) { Text("Confirm Merge") }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPastureNameDialog) {
        AlertDialog(
            onDismissRequest = { showPastureNameDialog = false },
            title = { Text("Name Pasture") },
            text = {
                OutlinedTextField(
                    value = pastureNameInput,
                    onValueChange = { pastureNameInput = it },
                    label = { Text("Optional name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val error = geometryError()
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    } else {
                        val vertices = draftVertices.toList()
                        val name = pastureNameInput
                        showPastureNameDialog = false
                        scope.launch {
                            selectedPastureId = pastureDao.insertWithVertices(name, "", vertices)
                            leaveGeometryMode()
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPastureNameDialog = false }) { Text("Back") }
            }
        )
    }

    if (showPastureDetailsDialog && selectedPasture != null) {
        AlertDialog(
            onDismissRequest = { showPastureDetailsDialog = false },
            title = { Text("Edit Pasture") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pastureEditName,
                        onValueChange = { pastureEditName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pastureEditNotes,
                        onValueChange = { pastureEditNotes = it },
                        label = { Text("Notes") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pasture = selectedPasture.pasture
                    scope.launch {
                        pastureDao.updateDetails(
                            pasture.id,
                            pastureEditName.trim().ifBlank { pasture.name },
                            pastureEditNotes.trim(),
                            System.currentTimeMillis()
                        )
                        showPastureDetailsDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPastureDetailsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPastureDeleteDialog && selectedPasture != null) {
        AlertDialog(
            onDismissRequest = { showPastureDeleteDialog = false },
            title = { Text("Delete ${selectedPasture.pasture.name}?") },
            text = { Text("This removes the saved pasture boundary from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = selectedPasture.pasture.id
                        showPastureDeleteDialog = false
                        scope.launch {
                            try {
                                pastureDao.deleteById(id)
                                selectedPastureId = null
                            } catch (error: IllegalStateException) {
                                Toast.makeText(
                                    context,
                                    error.message ?: "Failed to delete pasture",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showPastureDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showGateEditDialog && selectedGateId != null) {
        gates.firstOrNull { it.id == selectedGateId }?.let { gate ->
            key(gate.id) {
                var editName by remember(gate.id) { mutableStateOf(gate.name) }
                var editWidth by remember(gate.id) { mutableStateOf(gate.widthMeters.toString()) }
                var editType by remember(gate.id) { mutableStateOf(gate.gateType) }
                var editNotes by remember(gate.id) { mutableStateOf(gate.notes) }
                AlertDialog(
                    onDismissRequest = { showGateEditDialog = false },
                    title = { Text("Edit Gate Details") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Name") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editWidth,
                                onValueChange = { editWidth = it },
                                label = { Text("Width in meters (2.0 to 10.0)") },
                                singleLine = true
                            )
                            Text("Gate Type", fontSize = 12.sp, color = Color.Gray)
                            GateEntity.VALID_TYPES.chunked(3).forEach { rowTypes ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowTypes.forEach { type ->
                                        FilterChip(
                                            selected = editType == type,
                                            onClick = { editType = type },
                                            label = { Text(type, fontSize = 9.sp) }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text("Notes") }
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val parsedWidth = editWidth.toDoubleOrNull()
                            if (parsedWidth == null) {
                                Toast.makeText(context, "Enter a valid gate width", Toast.LENGTH_LONG).show()
                            } else {
                                scope.launch {
                                    try {
                                        gateDao.updateValidatedDetails(
                                            id = gate.id,
                                            name = editName,
                                            widthMeters = parsedWidth,
                                            gateType = editType,
                                            notes = editNotes
                                        )
                                        showGateEditDialog = false
                                    } catch (error: IllegalArgumentException) {
                                        Toast.makeText(
                                            context,
                                            error.message ?: "Failed to update gate",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGateEditDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }

    if (showGateDeleteDialog && selectedGateId != null) {
        val gate = gates.firstOrNull { it.id == selectedGateId }
        AlertDialog(
            onDismissRequest = { showGateDeleteDialog = false },
            title = { Text("Delete Gate?") },
            text = { Text("This removes the saved gate opening from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (gate != null) {
                            scope.launch {
                                try {
                                    gateDao.deleteGateWithChecks(gate)
                                    selectedGateId = null
                                    showGateDeleteDialog = false
                                } catch (error: IllegalStateException) {
                                    Toast.makeText(context, error.message ?: "Cannot delete gate", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showGateDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showWaterEditDialog && selectedWater != null) {
        AlertDialog(
            onDismissRequest = { showWaterEditDialog = false },
            title = { Text("Edit Water Asset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = waterEditName,
                        onValueChange = { waterEditName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                    Text("Water type", fontSize = 12.sp)
                    WaterSourceType.entries.chunked(3).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            rowTypes.forEach { type ->
                                FilterChip(
                                    selected = waterEditType == type,
                                    onClick = { waterEditType = type },
                                    label = { Text(type.displayName(), fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = waterEditNotes,
                        onValueChange = { waterEditNotes = it },
                        label = { Text("Notes") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val current = selectedWater
                    scope.launch {
                        waterDao.update(
                            current.copy(
                                name = waterEditName.trim().ifBlank { current.name },
                                sourceType = waterEditType,
                                notes = waterEditNotes.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        showWaterEditDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showWaterEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showWaterDeleteDialog && selectedWater != null) {
        AlertDialog(
            onDismissRequest = { showWaterDeleteDialog = false },
            title = { Text("Delete ${selectedWater.name}?") },
            text = { Text("This removes the saved water point and its tiered coverage zones from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = selectedWater.id
                        showWaterDeleteDialog = false
                        scope.launch {
                            waterDao.deleteById(id)
                            selectedWaterId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showWaterDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAssignPasturesDialog && selectedWater != null) {
        AlertDialog(
            onDismissRequest = { showAssignPasturesDialog = false },
            title = {
                Text(
                    "Assign Pastures: ${selectedWater.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Select every pasture livestock can access from this water source.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    if (pastures.isEmpty()) {
                        Text("No saved pastures are available.", color = Color.Gray)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(pastures, key = { it.pasture.id }) { pasture ->
                                val pastureId = pasture.pasture.id
                                val checked = pastureId in assignmentDraftIds
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (checked) {
                                        Color(0xFF2E7D32).copy(alpha = 0.28f)
                                    } else {
                                        Color(0xFF2C2C2C)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { enabled ->
                                                if (enabled && pastureId !in assignmentDraftIds) {
                                                    assignmentDraftIds.add(pastureId)
                                                } else if (!enabled) {
                                                    assignmentDraftIds.remove(pastureId)
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                pasture.pasture.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                String.format(
                                                    Locale.US,
                                                    "%.1f acres",
                                                    AcreageCalculator.calculateAcres(
                                                        pasture.orderedCoordinates()
                                                    )
                                                ),
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        if (assignmentDraftIds.isEmpty()) {
                            "Unassigned: accessible coverage will be hidden."
                        } else {
                            "${assignmentDraftIds.size} pasture${if (assignmentDraftIds.size == 1) "" else "s"} selected"
                        },
                        color = if (assignmentDraftIds.isEmpty()) Color(0xFFFFD600) else Color(0xFF00E5FF),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val waterPointId = selectedWater.id
                        val replacements = assignmentDraftIds.toList()
                        scope.launch {
                            assignmentDao.replaceForWaterPoint(waterPointId, replacements)
                            showAssignPasturesDialog = false
                        }
                    },
                    enabled = pastures.isNotEmpty() || assignmentDraftIds.isEmpty()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAssignPasturesDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDataSafetyDialog) {
        AlertDialog(
            onDismissRequest = { if (!dataOperationInProgress) showDataSafetyDialog = false },
            title = { Text("Data Safety", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Backups preserve every mapped asset, herd, movement record, audit timestamp, and display setting.",
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = {
                            val folder = backupFolderUri
                            if (folder == null) {
                                backupFolderLauncher.launch(null)
                            } else {
                                scope.launch {
                                    dataOperationInProgress = true
                                    try {
                                        createBackupInFolder(folder)
                                        Toast.makeText(context, "Quick backup created", Toast.LENGTH_LONG).show()
                                        showDataSafetyDialog = false
                                    } catch (error: Exception) {
                                        Toast.makeText(context, error.message ?: "Backup failed", Toast.LENGTH_LONG).show()
                                    } finally {
                                        dataOperationInProgress = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !dataOperationInProgress
                    ) { Text(if (backupFolderUri == null) "Choose Backup Folder" else "Quick Backup") }
                    Text(
                        if (backupFolderUri == null) {
                            "Choose Google Drive, local storage, or USB once. Later backups can be created there with one tap."
                        } else {
                            "Quick Backup creates a new timestamped archive in your saved folder."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    OutlinedButton(
                        onClick = { createBackupLauncher.launch(backupFileName()) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !dataOperationInProgress
                    ) { Text("Save Backup As…") }
                    OutlinedButton(
                        onClick = {
                            openBackupLauncher.launch(backupFolderUri)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !dataOperationInProgress
                    ) { Text("Restore Backup") }
                    if (backupFolderUri != null || lastBackupUri != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (backupFolderUri != null) {
                                TextButton(
                                    onClick = { backupFolderLauncher.launch(backupFolderUri) },
                                    enabled = !dataOperationInProgress
                                ) { Text("Change Folder") }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                            lastBackupUri?.let { backupUri ->
                                TextButton(
                                    onClick = { shareBackup(backupUri) },
                                    enabled = !dataOperationInProgress
                                ) { Text("Share Latest") }
                            }
                        }
                    }
                    if (emergencyBackupAvailable) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    dataOperationInProgress = true
                                    try {
                                        pendingRestore = withContext(Dispatchers.IO) {
                                            backupManager.prepareLatestEmergencyRestore()
                                        }
                                        restoringEmergencyBackup = true
                                        showDataSafetyDialog = false
                                        showRestoreConfirmDialog = true
                                    } catch (error: Exception) {
                                        Toast.makeText(context, error.message ?: "Rollback cannot be opened", Toast.LENGTH_LONG).show()
                                    } finally {
                                        dataOperationInProgress = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !dataOperationInProgress
                        ) { Text("Undo Last Restore") }
                    }
                    if (dataOperationInProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Working…", fontSize = 12.sp)
                        }
                    }
                    Text(
                        "Restore replaces the current RangeWater records only after the selected archive passes every safety check.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showDataSafetyDialog = false },
                    enabled = !dataOperationInProgress
                ) { Text("Close") }
            }
        )
    }

    if (showRestoreConfirmDialog && pendingRestore != null) {
        val prepared = pendingRestore!!
        val counts = prepared.manifest.recordCounts
        val created = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
            .format(Date(prepared.manifest.createdAt))
        AlertDialog(
            onDismissRequest = {
                if (!dataOperationInProgress) {
                    showRestoreConfirmDialog = false
                    pendingRestore = null
                }
            },
            title = { Text(if (restoringEmergencyBackup) "Undo Last Restore?" else "Restore RangeWater Backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Created: $created", fontWeight = FontWeight.Bold)
                    Text("RangeWater ${prepared.manifest.appVersionName} • Archive format ${prepared.manifest.formatVersion}", fontSize = 12.sp)
                    Text(
                        "${counts.waterPoints} water • ${counts.pastures} pastures • ${counts.gates} gates\n" +
                            "${counts.herds} herds • ${counts.movements} movements",
                        fontSize = 13.sp
                    )
                    Text(
                        "This will replace all current RangeWater records. An emergency rollback copy will be created first.",
                        color = Color(0xFFFF9100),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (dataOperationInProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Restoring…", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            dataOperationInProgress = true
                            try {
                                withContext(Dispatchers.IO) { backupManager.restore(prepared) }
                                resetAfterRestore()
                                emergencyBackupAvailable = backupManager.hasEmergencyBackup()
                                showRestoreConfirmDialog = false
                                pendingRestore = null
                                Toast.makeText(context, "RangeWater restore completed", Toast.LENGTH_LONG).show()
                            } catch (error: Exception) {
                                Toast.makeText(context, error.message ?: "Restore failed; current data was preserved", Toast.LENGTH_LONG).show()
                            } finally {
                                dataOperationInProgress = false
                            }
                        }
                    },
                    enabled = !dataOperationInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9100), contentColor = Color.Black)
                ) { Text("Replace and Restore") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmDialog = false
                        pendingRestore = null
                    },
                    enabled = !dataOperationInProgress
                ) { Text("Cancel") }
            }
        )
    }

    if (showLayersDialog) {
        AlertDialog(
            onDismissRequest = { showLayersDialog = false },
            title = { Text("Map Layers", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Coverage Scope", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            SpatialCoverageScope.PHYSICAL_RADIUS to "Physical Radius",
                            SpatialCoverageScope.ACCESSIBLE_COVERAGE to "Accessible Coverage"
                        ).forEach { (coverageScope, label) ->
                            FilterChip(
                                selected = displayPreferences.coverageScope == coverageScope,
                                onClick = {
                                    displayPreferences = displayPreferences.copy(
                                        coverageScope = coverageScope
                                    )
                                    displayPreferencesRepository.saveCoverageScope(coverageScope)
                                },
                                label = { Text(label, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                colors = mapModeChipColors()
                            )
                        }
                    }
                    Text("Water Display", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            WaterCoverageMode.FULL to "Full",
                            WaterCoverageMode.LINES_ONLY to "Lines",
                            WaterCoverageMode.OFF to "Off"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = displayPreferences.coverageMode == mode,
                                onClick = {
                                    displayPreferences = displayPreferences.copy(coverageMode = mode)
                                    displayPreferencesRepository.saveCoverageMode(mode)
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = mapModeChipColors()
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pasture Fill", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Boundary lines and pasture selection remain available.",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = displayPreferences.pastureFillEnabled,
                            onCheckedChange = { enabled ->
                                displayPreferences = displayPreferences.copy(
                                    pastureFillEnabled = enabled
                                )
                                displayPreferencesRepository.savePastureFillEnabled(enabled)
                            }
                        )
                    }
                    MapLayerToggle(
                        label = "Pasture Boundaries",
                        description = "Hide fence outlines without removing pasture records.",
                        checked = displayPreferences.pastureBoundariesEnabled,
                        onCheckedChange = { enabled ->
                            displayPreferences = displayPreferences.copy(
                                pastureBoundariesEnabled = enabled
                            )
                            displayPreferencesRepository.savePastureBoundariesEnabled(enabled)
                        }
                    )
                    MapLayerToggle(
                        label = "Water Points",
                        description = "Coverage rings remain controlled separately above.",
                        checked = displayPreferences.waterPointsEnabled,
                        onCheckedChange = { enabled ->
                            displayPreferences = displayPreferences.copy(waterPointsEnabled = enabled)
                            displayPreferencesRepository.saveWaterPointsEnabled(enabled)
                        }
                    )
                    MapLayerToggle(
                        label = "Gates",
                        description = "Gate placement and movement temporarily reveal this layer.",
                        checked = displayPreferences.gatesEnabled,
                        onCheckedChange = { enabled ->
                            displayPreferences = displayPreferences.copy(gatesEnabled = enabled)
                            displayPreferencesRepository.saveGatesEnabled(enabled)
                        }
                    )
                    MapLayerToggle(
                        label = "Herd Badges",
                        description = "Herd records and movement history remain unchanged.",
                        checked = displayPreferences.herdBadgesEnabled,
                        onCheckedChange = { enabled ->
                            displayPreferences = displayPreferences.copy(herdBadgesEnabled = enabled)
                            displayPreferencesRepository.saveHerdBadgesEnabled(enabled)
                        }
                    )
                    if (focusedHerd != null) {
                        OutlinedButton(
                            onClick = {
                                focusedHerdId = null
                                showLayersDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Herd Focus: ${focusedHerd.name}", maxLines = 1)
                        }
                    }
                    if (interactionState == InteractionState.WATER_MOVING) {
                        Text(
                            "Full coverage stays visible while moving water, then your setting returns.",
                            color = Color(0xFFFFD600),
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showLayersDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun MapLayerToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun applyLayerVisibility(
    map: MapLibreMap,
    preferences: DisplayPreferences,
    isMovingWater: Boolean,
    isEditingWater: Boolean,
    isEditingPasture: Boolean,
    isEditingGate: Boolean
) {
    val layerState = MapLayerVisibilityController.computeVisibility(
        preferences = preferences,
        isMovingWater = isMovingWater,
        isEditingWater = isEditingWater,
        isEditingPasture = isEditingPasture,
        isEditingGate = isEditingGate
    )
    map.getStyle { style ->
        style.getLayer(MapConfig.LAYER_WATER_PREFERRED_FILL)?.setProperties(
            visibility(if (layerState.preferredFillVisible) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(MapConfig.LAYER_WATER_TRANSITION_FILL)?.setProperties(
            visibility(if (layerState.transitionFillVisible) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(MapConfig.LAYER_WATER_RINGS_LINE)?.setProperties(
            visibility(if (layerState.ringsLineVisible) Property.VISIBLE else Property.NONE)
        )
        style.getLayer(MapConfig.LAYER_PASTURE_FILL)?.setProperties(
            fillOpacity(layerState.pastureFillOpacity)
        )
        val pastureBoundaryVisibility = if (layerState.pastureBoundariesVisible) Property.VISIBLE else Property.NONE
        style.getLayer(MapConfig.LAYER_PASTURE_CASING)?.setProperties(visibility(pastureBoundaryVisibility))
        style.getLayer(MapConfig.LAYER_PASTURE_LINE)?.setProperties(visibility(pastureBoundaryVisibility))

        val waterVisibility = if (layerState.waterPinsVisible) Property.VISIBLE else Property.NONE
        style.getLayer(MapConfig.LAYER_WATER_POINTS_HIGHLIGHT)?.setProperties(visibility(waterVisibility))
        style.getLayer(MapConfig.LAYER_WATER_POINTS)?.setProperties(visibility(waterVisibility))
        style.getLayer(MapConfig.LAYER_WATER_POINT_ICONS)?.setProperties(visibility(waterVisibility))

        val gateVisibility = if (layerState.gatesVisible) Property.VISIBLE else Property.NONE
        listOf(
            MapConfig.LAYER_GATE_OVERVIEW,
            MapConfig.LAYER_GATE_ARC,
            MapConfig.LAYER_GATE_LEAF_CASING,
            MapConfig.LAYER_GATE_LEAF,
            MapConfig.LAYER_GATE_TOUCH_TARGET
        ).forEach { layerId ->
            style.getLayer(layerId)?.setProperties(visibility(gateVisibility))
        }

        val herdVisibility = if (layerState.herdBadgesVisible) Property.VISIBLE else Property.NONE
        listOf(
            MapConfig.LAYER_HERD_BADGE_CASING_0,
            MapConfig.LAYER_HERD_BADGE_FILL_0,
            MapConfig.LAYER_HERD_BADGE_ICON_0,
            MapConfig.LAYER_HERD_BADGE_CASING_1,
            MapConfig.LAYER_HERD_BADGE_FILL_1,
            MapConfig.LAYER_HERD_BADGE_ICON_1,
            MapConfig.LAYER_HERD_BADGE_CASING_2,
            MapConfig.LAYER_HERD_BADGE_FILL_2,
            MapConfig.LAYER_HERD_BADGE_ICON_2
        ).forEach { layerId ->
            style.getLayer(layerId)?.setProperties(visibility(herdVisibility))
        }
    }
}

@Composable
private fun MapStatusAndModeControls(
    activeMode: ActiveMapMode,
    currentZoom: Double,
    isMapRendering: Boolean,
    hasLoadError: Boolean,
    interactionState: InteractionState,
    coverageMode: WaterCoverageMode,
    pastureFillEnabled: Boolean,
    focusedHerdName: String?,
    focusedHerdColorHex: String?,
    onModeSelected: (ActiveMapMode) -> Unit,
    onLayersClick: () -> Unit,
    onDataClick: () -> Unit,
    onHerdsClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(8.dp).background(
                            when {
                                hasLoadError -> Color.Red
                                interactionState == InteractionState.PASTURE_DRAWING ||
                                    interactionState == InteractionState.PASTURE_EDITING -> Color(0xFFFF2D95)
                                interactionState == InteractionState.WATER_PLACEMENT ||
                                    interactionState == InteractionState.WATER_MOVING -> Color(0xFF00E5FF)
                                interactionState == InteractionState.GATE_PLACEMENT ||
                                    interactionState == InteractionState.GATE_MOVING -> Color(0xFFFF9100)
                                focusedHerdColorHex != null -> Color(
                                    android.graphics.Color.parseColor(focusedHerdColorHex)
                                )
                                activeMode == ActiveMapMode.LABELED -> Color(0xFFFFC107)
                                currentZoom >= MapConfig.DETAIL_TRANSITION_ZOOM -> Color(0xFF4CAF50)
                                else -> Color(0xFF2196F3)
                            },
                            CircleShape
                        )
                    )
                    Spacer(Modifier.width(7.dp))
                    val zoom = String.format(Locale.US, "%.1f", currentZoom)
                    Text(
                        when {
                            hasLoadError -> "Imagery load issue"
                            interactionState == InteractionState.PASTURE_DRAWING -> "Tap fence corners • z$zoom"
                            interactionState == InteractionState.PASTURE_EDITING -> "Edit boundary • z$zoom"
                            interactionState == InteractionState.WATER_PLACEMENT -> "Tap pasture to place • z$zoom"
                            interactionState == InteractionState.WATER_MOVING -> "Move water • z$zoom"
                            interactionState == InteractionState.GATE_PLACEMENT -> "Tap fence for gate • z$zoom"
                            interactionState == InteractionState.GATE_MOVING -> "Tap fence to move gate • z$zoom"
                            focusedHerdName != null -> "$focusedHerdName • z$zoom"
                            activeMode == ActiveMapMode.LABELED -> "USGS Labeled • z$zoom"
                            currentZoom >= MapConfig.DETAIL_TRANSITION_ZOOM -> "USDA Detail • z$zoom"
                            else -> "USGS Overview • z$zoom"
                        },
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    if (isMapRendering) {
                        Spacer(Modifier.width(7.dp))
                        CircularProgressIndicator(Modifier.size(11.dp), strokeWidth = 1.5.dp, color = Color.White)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = activeMode == ActiveMapMode.AERIAL,
                    onClick = { onModeSelected(ActiveMapMode.AERIAL) },
                    label = { Text("Aerial", fontSize = 12.sp) },
                    colors = mapModeChipColors()
                )
                FilterChip(
                    selected = activeMode == ActiveMapMode.LABELED,
                    onClick = { onModeSelected(ActiveMapMode.LABELED) },
                    label = { Text("Labeled", fontSize = 12.sp) },
                    colors = mapModeChipColors()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onLayersClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.82f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                val coverageLabel = when (coverageMode) {
                    WaterCoverageMode.FULL -> "Full"
                    WaterCoverageMode.LINES_ONLY -> "Lines"
                    WaterCoverageMode.OFF -> "Off"
                }
                Text(
                    "Layers • $coverageLabel${if (pastureFillEnabled) "" else " • No fill"}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onHerdsClick,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("Herds", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = onDataClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.82f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) { Text("⋮", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BoxScope.PastureGeometryControls(
    acres: Double,
    canUndo: Boolean,
    canFinish: Boolean,
    canRemove: Boolean,
    editing: Boolean,
    addCornerArmed: Boolean,
    selectedCorner: Int?,
    onUndo: () -> Unit,
    onAddCorner: () -> Unit,
    onMove: () -> Unit,
    onMerge: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    onFinish: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 52.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E).copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                String.format(Locale.US, "%.1f acres", acres),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (restStatus?.state == PastureRestState.RESTING && restStatus.restStartedAt != null) {
                Text(
                    "Based on recorded herd movements • departure " +
                        SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(restStatus.restStartedAt)),
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            } else if (restStatus?.state == PastureRestState.NO_RECORDED_DEPARTURE) {
                Text(
                    "Direct edits or unrecorded field moves can make this history incomplete.",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            Text(
                when {
                    addCornerArmed -> "Tap the fence line where the new corner belongs"
                    editing && selectedCorner != null -> "Corner $selectedCorner selected — drag to move"
                    editing -> "Tap a corner to select it, or choose Add Corner"
                    else -> "Map estimate — not a surveyed boundary"
                },
                color = if (addCornerArmed) Color(0xFFFFD600) else Color.LightGray,
                fontSize = 10.sp
            )
            if (editing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddCorner,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (addCornerArmed) Color(0xFFFFD600) else Color(0xFFFF2D95),
                            contentColor = if (addCornerArmed) Color.Black else Color.White
                        )
                    ) { Text(if (addCornerArmed) "Cancel Add" else "Add Corner", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = onMove,
                        enabled = selectedCorner != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Move", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = onRemove,
                        enabled = canRemove,
                        modifier = Modifier.weight(1f)
                    ) { Text("Remove", fontSize = 12.sp) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMerge,
                        enabled = selectedCorner != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("Merge Nearby", fontSize = 12.sp) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    OutlinedButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.weight(1f)
                    ) { Text("Undo", fontSize = 12.sp) }
                    Button(
                        onClick = onFinish,
                        enabled = canFinish,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save", fontSize = 12.sp) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    OutlinedButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.weight(1f)
                    ) { Text("Undo", fontSize = 12.sp) }
                    Button(
                        onClick = onFinish,
                        enabled = canFinish,
                        modifier = Modifier.weight(1f)
                    ) { Text("Finish", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.BottomInstruction(text: String, action: String, onAction: () -> Unit) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, bottom = 56.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.84f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text, color = Color.White)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun BoxScope.WaterInspectionCard(
    point: WaterPointEntity,
    assignedPastureCount: Int,
    onClose: () -> Unit,
    onMove: () -> Unit,
    onAssignPastures: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 52.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E).copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(point.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "${point.sourceType.displayName()} • Tiered coverage",
                        color = Color(0xFFFFD600),
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onClose) { Text("Close", color = Color.LightGray) }
            }
            Text(
                String.format(Locale.US, "Lat %.5f  •  Lng %.5f", point.latitude, point.longitude),
                color = Color.LightGray,
                fontSize = 12.sp
            )
            if (point.notes.isNotBlank()) Text(point.notes, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            Text(
                if (assignedPastureCount == 0) {
                    "Pastures: Unassigned"
                } else {
                    "Pastures: $assignedPastureCount assigned"
                },
                color = if (assignedPastureCount == 0) Color(0xFFFFD600) else Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onMove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color.Black
                    )
                ) { Text("Move Location", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onAssignPastures,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2C2C2C),
                        contentColor = Color.White
                    )
                ) { Text("Assign Pastures", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun BoxScope.WaterMoveControls(
    draftLocation: LatLng?,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 52.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E).copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Move Water Location", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                if (draftLocation == null) {
                    "Tap the map to preview a new location"
                } else {
                    String.format(
                        Locale.US,
                        "Proposed: %.5f, %.5f • Previewing tiered coverage",
                        draftLocation.latitude,
                        draftLocation.longitude
                    )
                },
                color = if (draftLocation == null) Color.LightGray else Color(0xFFFFD600),
                fontSize = 11.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = onSave,
                    enabled = draftLocation != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GrazingCircuitManagerDialog(
    circuits: List<GrazingCircuitEntity>,
    memberships: List<GrazingCircuitPastureEntity>,
    pastures: List<PastureWithVertices>,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Grazing Circuits") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "A Grazing Circuit groups pastures for planning. It does not move the herd.",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("Create Circuit") }
                if (circuits.isEmpty()) {
                    Text("No Grazing Circuits yet.", color = Color.Gray)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(circuits) { circuit ->
                            val memberNames = memberships
                                .filter { it.circuitId == circuit.id }
                                .sortedBy { it.sequence }
                                .mapNotNull { member ->
                                    pastures.firstOrNull { it.pasture.id == member.pastureId }?.pasture?.name
                                }
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clickable { onEdit(circuit.id) },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF252525)
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(circuit.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${memberNames.size} pastures • ${memberNames.joinToString(" → ")}",
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    TextButton(onClick = { onArchive(circuit.id) }) {
                                        Text("Archive", color = Color(0xFFFF6B6B), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun GrazingCircuitEditorDialog(
    circuit: GrazingCircuitEntity?,
    memberships: List<GrazingCircuitPastureEntity>,
    roles: List<GrazingCircuitPastureRoleEntity>,
    pastures: List<PastureWithVertices>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<GrazingCircuitPastureDraft>) -> Unit
) {
    var name by remember(circuit?.id) { mutableStateOf(circuit?.name.orEmpty()) }
    var notes by remember(circuit?.id) { mutableStateOf(circuit?.notes.orEmpty()) }
    val selectedIds = remember(circuit?.id) {
        mutableStateListOf<Long>().apply {
            addAll(memberships.sortedBy { it.sequence }.map { it.pastureId })
        }
    }
    val roleSelections = remember(circuit?.id) {
        mutableStateMapOf<Long, Set<SeasonalPastureRole>>().apply {
            roles.groupBy { it.pastureId }.forEach { (pastureId, rows) ->
                put(pastureId, rows.map { it.role }.toSet())
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (circuit == null) "Create Circuit" else "Edit Grazing Circuit") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                item {
                    Text(
                        "Seasonal roles describe intended use and do not determine forage readiness.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(name, { name = it }, label = { Text("Circuit name") }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
                    Text("Pastures and planning order", fontWeight = FontWeight.Bold)
                }
                items(pastures) { pasture ->
                    val pastureId = pasture.pasture.id
                    val selected = pastureId in selectedIds
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) Color(0xFF263238) else Color.Transparent
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedIds.add(pastureId) else {
                                            selectedIds.remove(pastureId)
                                            roleSelections.remove(pastureId)
                                        }
                                    }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(pasture.pasture.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        String.format(Locale.US, "%.1f mapped acres", AcreageCalculator.calculateAcres(pasture.orderedCoordinates())),
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                                if (selected) {
                                    val index = selectedIds.indexOf(pastureId)
                                    TextButton(
                                        enabled = index > 0,
                                        onClick = {
                                            selectedIds.removeAt(index)
                                            selectedIds.add(index - 1, pastureId)
                                        }
                                    ) { Text("Up") }
                                    TextButton(
                                        enabled = index >= 0 && index < selectedIds.lastIndex,
                                        onClick = {
                                            selectedIds.removeAt(index)
                                            selectedIds.add(index + 1, pastureId)
                                        }
                                    ) { Text("Down") }
                                }
                            }
                            if (selected) {
                                SeasonalPastureRole.entries.forEach { role ->
                                    val checked = role in roleSelections[pastureId].orEmpty()
                                    FilterChip(
                                        selected = checked,
                                        onClick = {
                                            val current = roleSelections[pastureId].orEmpty()
                                            roleSelections[pastureId] = if (checked) current - role else current + role
                                        },
                                        label = { Text(role.displayLabel(), fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && selectedIds.isNotEmpty(),
                onClick = {
                    onSave(
                        name,
                        notes,
                        selectedIds.map { pastureId ->
                            GrazingCircuitPastureDraft(pastureId, roleSelections[pastureId].orEmpty())
                        }
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun HerdCircuitAssignmentDialog(
    herd: HerdEntity,
    circuits: List<GrazingCircuitEntity>,
    assignedCircuitId: Long?,
    onDismiss: () -> Unit,
    onAssign: (Long?) -> Unit
) {
    var selectedId by remember(herd.id, assignedCircuitId) { mutableStateOf(assignedCircuitId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Circuit — ${herd.name}") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                item {
                    Text(
                        "A Grazing Circuit groups pastures for planning. It does not move the herd.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selectedId == null, { selectedId = null })
                        Text("No Circuit")
                    }
                }
                items(circuits) { circuit ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selectedId == circuit.id, { selectedId = circuit.id })
                        Text(circuit.name)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onAssign(selectedId) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ForageObservationDialog(
    pastureId: Long,
    pastureName: String,
    acreage: Double,
    onDismiss: () -> Unit,
    onSave: (PastureForageObservationEntity) -> Unit
) {
    var heightText by remember(pastureId) { mutableStateOf("8") }
    var sampleCountText by remember(pastureId) { mutableStateOf("5") }
    var residualText by remember(pastureId) { mutableStateOf("3") }
    var customLowText by remember(pastureId) { mutableStateOf("") }
    var customHighText by remember(pastureId) { mutableStateOf("") }
    var standType by remember(pastureId) { mutableStateOf(ForageStandType.TALL_FESCUE_CLOVER) }
    var condition by remember(pastureId) { mutableStateOf(ForageStandCondition.GOOD) }
    var notes by remember(pastureId) { mutableStateOf("") }
    val height = heightText.toDoubleOrNull()
    val sampleCount = sampleCountText.toIntOrNull()
    val residual = residualText.toDoubleOrNull()
    val calibration = if (standType == ForageStandType.OTHER_CUSTOM) {
        val low = customLowText.toDoubleOrNull()
        val high = customHighText.toDoubleOrNull()
        if (low != null && high != null && low > 0 && high >= low) {
            com.sagewire.rangewater.data.ForageCalibrationRange(low, high)
        } else null
    } else {
        ForageCalculator.ohioCalibration(standType, condition)
    }
    val estimate = if (height != null && height > 0 && residual != null && residual >= 0 && acreage > 0 && calibration != null) {
        ForageCalculator.estimate(height, residual, calibration, acreage)
    } else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Forage — $pastureName") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                item {
                    Text("Measure representative undisturbed points with the grazing stick.", color = Color.Gray, fontSize = 11.sp)
                    OutlinedTextField(heightText, { heightText = it }, label = { Text("Average height (inches)") }, singleLine = true)
                    OutlinedTextField(sampleCountText, { sampleCountText = it }, label = { Text("Number of samples") }, singleLine = true)
                    Text("Stand type", fontWeight = FontWeight.Bold)
                    ForageStandType.entries.forEach { type ->
                        FilterChip(
                            selected = standType == type,
                            onClick = { standType = type },
                            label = { Text(type.displayLabel(), fontSize = 10.sp) }
                        )
                    }
                    Text("Stand condition", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ForageStandCondition.entries.forEach { value ->
                            FilterChip(
                                selected = condition == value,
                                onClick = { condition = value },
                                label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    OutlinedTextField(residualText, { residualText = it }, label = { Text("Residual retained (inches)") }, singleLine = true)
                    if (standType == ForageStandType.OTHER_CUSTOM) {
                        OutlinedTextField(customLowText, { customLowText = it }, label = { Text("Custom low lb DM/acre/in") }, singleLine = true)
                        OutlinedTextField(customHighText, { customHighText = it }, label = { Text("Custom high lb DM/acre/in") }, singleLine = true)
                    } else if (calibration != null) {
                        Text(
                            "Ohio USDA-NRCS/GLCI calibration: ${calibration.low.toInt()}–${calibration.high.toInt()} lb DM/acre/in",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp
                        )
                    }
                    Text(String.format(Locale.US, "Mapped acreage snapshot: %.1f acres", acreage), fontSize = 11.sp)
                    estimate?.let {
                        Text("Estimated available dry matter", fontWeight = FontWeight.Bold)
                        Text(
                            String.format(
                                Locale.US,
                                "max(%.1f − %.1f, 0) × %d–%d = %.0f–%.0f lb DM/acre",
                                height,
                                residual,
                                requireNotNull(calibration).low.toInt(),
                                requireNotNull(calibration).high.toInt(),
                                it.availableDmLowLbsPerAcre,
                                it.availableDmHighLbsPerAcre
                            ),
                            fontSize = 11.sp
                        )
                        Text(
                            String.format(
                                Locale.US,
                                "Mapped-area estimate: %.0f–%.0f lb (%.1f–%.1f tons)",
                                it.mappedStockpileLowLbs,
                                it.mappedStockpileHighLbs,
                                it.mappedStockpileLowTons,
                                it.mappedStockpileHighTons
                            ),
                            color = Color(0xFF81C784),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        "Estimate uses mapped pasture acres. Unmapped roads, woods, ponds, and other ungrazable areas are not automatically removed.",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = height != null && height > 0 && sampleCount != null && sampleCount > 0 &&
                    residual != null && residual >= 0 && acreage > 0 && calibration != null,
                onClick = {
                    onSave(
                        PastureForageObservationEntity(
                            pastureId = pastureId,
                            observedAt = System.currentTimeMillis(),
                            averageHeightInches = requireNotNull(height),
                            sampleCount = requireNotNull(sampleCount),
                            forageStandType = standType,
                            standCondition = condition,
                            residualHeightInches = requireNotNull(residual),
                            dmPerAcreInchLow = requireNotNull(calibration).low,
                            dmPerAcreInchHigh = requireNotNull(calibration).high,
                            calibrationSource = if (standType == ForageStandType.OTHER_CUSTOM) {
                                ForageCalibrationSource.CUSTOM_OPERATOR_VALUE
                            } else {
                                ForageCalibrationSource.OHIO_NRCS_GLCI_GRAZING_STICK
                            },
                            acreageSnapshot = acreage,
                            notes = notes
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun SeasonalPastureRole.displayLabel(): String = when (this) {
    SeasonalPastureRole.WINTER -> "Winter"
    SeasonalPastureRole.CALVING -> "Calving"
    SeasonalPastureRole.ROTATION -> "Rotation"
    SeasonalPastureRole.STOCKPILED_WINTER -> "Stockpiled Winter Grazing"
}

private fun ForageStandType.displayLabel(): String = when (this) {
    ForageStandType.PERENNIAL_RYEGRASS_CLOVER -> "Perennial ryegrass & clover"
    ForageStandType.TALL_FESCUE_NITROGEN -> "Tall fescue & nitrogen"
    ForageStandType.TALL_FESCUE_CLOVER -> "Tall fescue & clover"
    ForageStandType.OTHER_CUSTOM -> "Other / Custom"
}

@Composable
private fun BoxScope.PastureInspectionCard(
    pasture: PastureWithVertices,
    metrics: PastureCoverageMetrics,
    restStatus: PastureRestStatus?,
    latestForageObservation: PastureForageObservationEntity?,
    isStockpiledWinter: Boolean,
    onClose: () -> Unit,
    onAddGate: () -> Unit,
    onEditDetails: () -> Unit,
    onRecordForage: () -> Unit,
    onEditBoundary: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 52.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E).copy(alpha = 0.96f),
        tonalElevation = 6.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(pasture.pasture.name, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        String.format(Locale.US, "%.1f acres • Map estimate", metrics.totalAcreage),
                        color = Color(0xFFFF2D95),
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onClose) { Text("Close", color = Color.LightGray) }
            }
            if (pasture.pasture.notes.isNotBlank()) {
                Text(pasture.pasture.notes, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Text(
                when (restStatus?.state) {
                    PastureRestState.OCCUPIED -> "Recorded rest: occupied now"
                    PastureRestState.RESTING -> "Recorded rest: ${restStatus.daysSinceRecordedDeparture} days since departure"
                    PastureRestState.NO_RECORDED_DEPARTURE -> "Recorded rest: no completed departure recorded"
                    null -> "Recorded rest: loading…"
                },
                color = Color(0xFFFFD54F),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            latestForageObservation?.let { observation ->
                val estimate = ForageCalculator.estimate(observation)
                Text(
                    String.format(
                        Locale.US,
                        "%s: %.1f in avg • %.0f–%.0f lb DM/ac available",
                        if (isStockpiledWinter) "Latest stockpile estimate" else "Latest forage estimate",
                        observation.averageHeightInches,
                        estimate.availableDmLowLbsPerAcre,
                        estimate.availableDmHighLbsPerAcre
                    ),
                    color = Color(0xFF81C784),
                    fontSize = 11.sp
                )
            }
            Text(
                if (metrics.assignedWaterCount == 0) {
                    "Water Coverage: No sources assigned"
                } else {
                    "Water Coverage: ${metrics.assignedWaterCount} ${if (metrics.assignedWaterCount == 1) "source" else "sources"} assigned"
                },
                color = if (metrics.assignedWaterCount == 0) Color(0xFFFFD600) else Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            CoverageMetricRow(
                label = "Preferred (0–800 ft)",
                color = Color(0xFF81C784),
                acreage = metrics.preferredAcreage,
                percentage = metrics.preferredPercentage
            )
            CoverageMetricRow(
                label = "Transition only (800–1,000 ft)",
                color = Color(0xFFFFD54F),
                acreage = metrics.transitionOnlyAcreage,
                percentage = metrics.transitionOnlyPercentage
            )
            CoverageMetricRow(
                label = "Beyond planned coverage",
                color = Color(0xFFB0BEC5),
                acreage = metrics.beyondAcreage,
                percentage = metrics.beyondPercentage
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onAddGate,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9100),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Add Gate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onEditBoundary,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("Edit Boundary", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onEditDetails,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("Details", color = Color.White, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onRecordForage,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("Forage", color = Color(0xFF81C784), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun BoxScope.GatePlacementCard(
    candidate: SnappedGateCandidate?,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.90f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Place Gate", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                text = when {
                    candidate == null -> "Tap a fence line to align the gate"
                    candidate.isShared -> "${candidate.pastureAName} ⟷ ${candidate.pastureBName}"
                    else -> "${candidate.pastureAName} ⟷ Outside"
                },
                color = if (candidate == null) Color.LightGray else Color(0xFFFF9100),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = Color.Red)
                }
                Button(
                    onClick = onSave,
                    enabled = candidate != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9100),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Save Gate", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.GateInspectionCard(
    item: GateWithConnectivity,
    onClose: () -> Unit,
    onToggleStatus: () -> Unit,
    onMove: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val gate = item.gate
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(gate.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClose) { Text("×", color = Color.White, fontSize = 20.sp) }
            }
            Text(item.connectivityDescription, color = Color(0xFFFF9100), fontSize = 13.sp)
            Text(
                String.format(
                    Locale.US,
                    "%.1f m (~%.0f ft) • %s • %s",
                    gate.widthMeters,
                    gate.widthMeters * 3.28084,
                    gate.gateType,
                    gate.status
                ),
                color = Color.LightGray,
                fontSize = 11.sp
            )
            if (gate.notes.isNotBlank()) Text(gate.notes, color = Color.Gray, fontSize = 11.sp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onToggleStatus,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (gate.status == GateEntity.STATUS_CLOSED) {
                            Color(0xFF2E7D32)
                        } else {
                            Color(0xFFD32F2F)
                        }
                    )
                ) {
                    Text(if (gate.status == GateEntity.STATUS_CLOSED) "Open" else "Close", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onMove,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9100))
                ) { Text("Move", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) { Text("Edit", color = Color.White, fontSize = 12.sp) }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) { Text("Delete", color = Color.Red, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun BoxScope.GateMoveControls(
    gate: GateEntity?,
    candidate: SnappedGateCandidate?,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.90f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Move ${gate?.name.orEmpty()}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text(
                text = when {
                    candidate == null -> "Tap this fence to slide, or another fence to relocate"
                    candidate.isShared -> "Proposed: ${candidate.pastureAName} ⟷ ${candidate.pastureBName}"
                    else -> "Proposed: ${candidate.pastureAName} ⟷ Outside"
                },
                color = if (candidate == null) Color.LightGray else Color(0xFFFF9100),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = Color.Red)
                }
                Button(
                    onClick = onSave,
                    enabled = candidate != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9100),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Save Position", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CoverageMetricRow(
    label: String,
    color: Color,
    acreage: Double,
    percentage: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("● $label", color = color, fontSize = 10.sp)
        Text(
            String.format(Locale.US, "%.1f ac (%.1f%%)", acreage, percentage),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BoxScope.ActiveAttribution(activeMode: ActiveMapMode, currentZoom: Double) {
    Surface(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 16.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color.Black.copy(alpha = 0.55f)
    ) {
        Text(
            when {
                activeMode == ActiveMapMode.LABELED -> MapConfig.TOPO_ATTRIBUTION
                currentZoom >= MapConfig.DETAIL_TRANSITION_ZOOM -> MapConfig.USDA_ATTRIBUTION
                else -> MapConfig.USGS_ATTRIBUTION
            },
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun pushAllOverlays(
    map: MapLibreMap?,
    waterPoints: List<WaterPointEntity>,
    selectedWaterId: Long?,
    pastures: List<PastureWithVertices>,
    selectedPastureId: Long?,
    gates: List<GateWithConnectivity>,
    boundaryGates: List<GateWithConnectivity>,
    selectedGateId: Long?,
    herds: List<HerdEntity>,
    selectedHerdId: Long?,
    focusedPastureId: Long?,
    focusedPastureIds: Set<Long>,
    focusColorHex: String?,
    activeMovement: CattleMovementEntity?,
    draftVertices: List<PastureCoordinate>,
    selectedVertexIndex: Int?,
    coverageScope: SpatialCoverageScope,
    assignments: Map<Long, List<Long>>,
    sharedJunctionIds: Set<Long>,
    activeSnappedJunction: FenceJunctionEntity?
) {
    map?.getStyle { style ->
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_POINTS)
            ?.setGeoJson(WaterFeatureConverter.toPointFeatures(waterPoints, selectedWaterId))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_RINGS)
            ?.setGeoJson(
                WaterFeatureConverter.toRingFeatures(
                    points = waterPoints,
                    selectedId = selectedWaterId,
                    scope = coverageScope,
                    assignments = assignments,
                    pastures = pastures
                )
            )
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_PASTURES)
            ?.setGeoJson(
                PastureFeatureConverter.toPastureFeatures(
                    pastures = pastures,
                    selectedId = selectedPastureId,
                    focusedPastureId = focusedPastureId,
                    focusColorHex = focusColorHex,
                    focusedPastureIds = focusedPastureIds
                )
            )
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_PASTURE_LINES)
            ?.setGeoJson(
                PastureFeatureConverter.toPastureBoundaryLines(
                    pastures = pastures,
                    gates = boundaryGates,
                    selectedId = selectedPastureId,
                    focusedPastureId = focusedPastureId,
                    focusColorHex = focusColorHex,
                    focusedPastureIds = focusedPastureIds
                )
            )
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_GATES)
            ?.setGeoJson(GateFeatureConverter.toGateFeatures(gates, selectedGateId))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_HERD_BADGES)
            ?.setGeoJson(HerdFeatureConverter.toHerdBadges(herds, pastures, selectedHerdId))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_MOVEMENT_ROUTE)
            ?.setGeoJson(MovementFeatureConverter.toMovementRouteLines(activeMovement, pastures, gates))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_MOVEMENT_HIGHLIGHT)
            ?.setGeoJson(MovementFeatureConverter.toMovementHighlights(activeMovement, pastures))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_PASTURE_DRAFT)
            ?.setGeoJson(PastureFeatureConverter.draftFeature(draftVertices))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_PASTURE_HANDLES)
            ?.setGeoJson(
                PastureFeatureConverter.handleFeatures(
                    draftVertices,
                    selectedVertexIndex,
                    sharedJunctionIds
                )
            )
        val snapFeatures = activeSnappedJunction?.let { junction ->
            listOf(Feature.fromGeometry(Point.fromLngLat(junction.longitude, junction.latitude)))
        }.orEmpty()
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_SNAPPED_JUNCTION)
            ?.setGeoJson(FeatureCollection.fromFeatures(snapFeatures))
    }
}

private fun SnappedGateCandidate.toConnectivity(gate: GateEntity) = GateWithConnectivity(
    gate = gate,
    derivedCoordinate = derivedCoordinate,
    hingeCoordinate = hingeCoordinate,
    latchCoordinate = latchCoordinate,
    pastureAId = pastureAId,
    pastureAName = pastureAName,
    pastureBId = pastureBId,
    pastureBName = pastureBName,
    isShared = isShared
)

internal fun applyJunctionMovePreview(
    pastures: List<PastureWithVertices>,
    moves: Map<Long, PastureCoordinate>
): List<PastureWithVertices> {
    if (moves.isEmpty()) return pastures
    return pastures.map { pasture ->
        pasture.copy(
            vertices = pasture.vertices.map { vertex ->
                val move = moves[vertex.junctionId]
                if (move == null) vertex else vertex.copy(
                    junction = vertex.junction.copy(
                        latitude = move.latitude,
                        longitude = move.longitude
                    )
                )
            }
        )
    }
}

internal fun applyWaterMovePreview(
    waterPoints: List<WaterPointEntity>,
    movingWaterPoint: WaterPointEntity?,
    draftLocation: LatLng?
): List<WaterPointEntity> {
    if (movingWaterPoint == null || draftLocation == null) return waterPoints
    return waterPoints.map { point ->
        if (point.id == movingWaterPoint.id) {
            point.copy(
                latitude = draftLocation.latitude,
                longitude = draftLocation.longitude
            )
        } else {
            point
        }
    }
}

private fun queryFeaturesNear(
    map: MapLibreMap,
    point: PointF,
    radius: Float,
    vararg layers: String
) = map.queryRenderedFeatures(
    RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius),
    *layers
)

/**
 * Each stacked badge is rendered at a different screen translation even though its
 * GeoJSON point is shared. Point queries preserve that rendered separation; the old
 * large rectangle overlapped the whole stack and repeatedly returned index zero.
 */
private fun queryHerdFeatureAt(map: MapLibreMap, point: PointF): Feature? {
    val iconLayers = arrayOf(
        MapConfig.LAYER_HERD_BADGE_ICON_2,
        MapConfig.LAYER_HERD_BADGE_ICON_1,
        MapConfig.LAYER_HERD_BADGE_ICON_0
    )
    iconLayers.forEach { layer ->
        map.queryRenderedFeatures(point, layer).firstOrNull()?.let { return it }
    }

    val fillLayers = arrayOf(
        MapConfig.LAYER_HERD_BADGE_FILL_2,
        MapConfig.LAYER_HERD_BADGE_FILL_1,
        MapConfig.LAYER_HERD_BADGE_FILL_0
    )
    fillLayers.forEach { layer ->
        map.queryRenderedFeatures(point, layer).firstOrNull()?.let { return it }
    }
    return null
}

private fun LatLng.toPastureCoordinate() = PastureCoordinate(latitude, longitude)

private fun CameraPosition.toSavedMapCamera() = SavedMapCamera(
    latitude = target?.latitude ?: MapConfig.DEFAULT_LATITUDE,
    longitude = target?.longitude ?: MapConfig.DEFAULT_LONGITUDE,
    zoom = zoom,
    bearing = bearing,
    tilt = tilt
)

private fun applyMapMode(map: MapLibreMap, mode: ActiveMapMode) {
    when (mode) {
        ActiveMapMode.AERIAL -> map.setMaxZoomPreference(MapConfig.MAX_AERIAL_ZOOM)
        ActiveMapMode.LABELED -> {
            if (map.cameraPosition.zoom > MapConfig.MAX_LABELED_ZOOM) {
                map.animateCamera(
                    CameraUpdateFactory.zoomTo(MapConfig.MAX_LABELED_ZOOM),
                    400,
                    object : MapLibreMap.CancelableCallback {
                        override fun onFinish() = map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)
                        override fun onCancel() = map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)
                    }
                )
            } else {
                map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)
            }
        }
    }
    map.getStyle { style ->
        val aerial = if (mode == ActiveMapMode.AERIAL) Property.VISIBLE else Property.NONE
        val labeled = if (mode == ActiveMapMode.LABELED) Property.VISIBLE else Property.NONE
        style.getLayer(MapConfig.LAYER_AERIAL_OVERVIEW)?.setProperties(visibility(aerial))
        style.getLayer(MapConfig.LAYER_AERIAL_DETAIL)?.setProperties(visibility(aerial))
        style.getLayer(MapConfig.LAYER_LABELED_TOPO)?.setProperties(visibility(labeled))
    }
}

private fun WaterSourceType.displayName(): String =
    name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }

@Composable
private fun mapModeChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Black.copy(alpha = 0.6f),
    labelColor = Color.White,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = Color.White
)
