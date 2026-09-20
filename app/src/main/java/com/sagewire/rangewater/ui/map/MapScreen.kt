package com.sagewire.rangewater.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.sagewire.rangewater.data.PastureWithVertices
import com.sagewire.rangewater.data.FenceJunctionEntity
import com.sagewire.rangewater.data.RangeWaterDatabase
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import com.sagewire.rangewater.spatial.AcreageCalculator
import com.sagewire.rangewater.spatial.GeometryValidator
import com.sagewire.rangewater.spatial.CornerSnappingEngine
import com.sagewire.rangewater.spatial.PastureAnalyticsCalculator
import com.sagewire.rangewater.spatial.PastureCoverageMetrics
import com.sagewire.rangewater.spatial.PastureFeatureConverter
import com.sagewire.rangewater.spatial.PastureFeatureConverter.orderedCoordinates
import com.sagewire.rangewater.spatial.WaterFeatureConverter
import java.util.Locale
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
    PASTURE_EDITING
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
    val waterPoints by waterDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val pastures by pastureDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val allJunctions by junctionDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val assignments by assignmentDao.observeAll()
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
    var displayPreferences by remember {
        mutableStateOf(displayPreferencesRepository.getPreferences())
    }
    var showLayersDialog by remember { mutableStateOf(false) }
    var showAssignPasturesDialog by remember { mutableStateOf(false) }
    val assignmentDraftIds = remember { mutableStateListOf<Long>() }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var activeMode by remember { mutableStateOf(ActiveMapMode.AERIAL) }
    var interactionState by remember { mutableStateOf(InteractionState.ORDINARY) }
    var currentZoom by remember { mutableDoubleStateOf(initialZoom) }
    var isMapRendering by remember { mutableStateOf(true) }
    var hasLoadError by remember { mutableStateOf(false) }

    var selectedWaterId by remember { mutableStateOf<Long?>(null) }
    var selectedPastureId by remember { mutableStateOf<Long?>(null) }
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
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    fun replaceDraft(vertices: List<PastureCoordinate>) {
        draftVertices.clear()
        draftVertices.addAll(vertices)
        draftRevision++
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
        effectiveWaterPoints,
        effectivePastures,
        selectedWaterId,
        selectedPastureId,
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
            waterPoints = effectiveWaterPoints,
            selectedWaterId = selectedWaterId,
            pastures = effectivePastures,
            selectedPastureId = selectedPastureId,
            draftVertices = draftVertices.toList(),
            selectedVertexIndex = selectedVertexIndex,
            coverageScope = displayPreferences.coverageScope,
            assignments = assignmentMap,
            sharedJunctionIds = junctionUsageMap.filterValues { it.size > 1 }.keys,
            activeSnappedJunction = activeSnappedJunction
        )
    }
    LaunchedEffect(waterPoints, pastures, selectedWaterId, selectedPastureId) {
        if (selectedWaterId != null && selectedWater == null) selectedWaterId = null
        if (selectedPastureId != null && selectedPasture == null) selectedPastureId = null
        if (selectedWater == null) showAssignPasturesDialog = false
    }
    LaunchedEffect(displayPreferences, interactionState, mapInstance) {
        mapInstance?.let { map ->
            applyLayerVisibility(
                map = map,
                preferences = displayPreferences,
                isMovingWater = interactionState == InteractionState.WATER_MOVING
            )
        }
    }

    // 🎮 BLOCK 4 — MODE-AWARE MAP TAP ROUTING
    DisposableEffect(
        mapInstance,
        interactionState,
        selectedVertexIndex,
        pastureEditMode,
        allJunctions
    ) {
        val map = mapInstance
        if (map == null) {
            onDispose { }
        } else {
            val listener = MapLibreMap.OnMapClickListener { coordinate ->
                when (interactionState) {
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
                        val waterHit = queryFeaturesNear(
                            map,
                            screenPoint,
                            24f,
                            MapConfig.LAYER_WATER_POINTS
                        ).firstOrNull()
                        if (waterHit != null) {
                            selectedWaterId = waterHit.getNumberProperty("id").toLong()
                            selectedPastureId = null
                        } else {
                            val pastureHit = map.queryRenderedFeatures(
                                screenPoint,
                                MapConfig.LAYER_PASTURE_FILL
                            ).firstOrNull()
                            selectedPastureId = pastureHit?.getNumberProperty("id")?.toLong()
                            selectedWaterId = null
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
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(initialLat, initialLng))
                                .zoom(initialZoom)
                                .build()
                            applyMapMode(map, activeMode)
                            applyLayerVisibility(
                                map = map,
                                preferences = displayPreferences,
                                isMovingWater = interactionState == InteractionState.WATER_MOVING
                            )
                            pushAllOverlays(
                                map,
                                effectiveWaterPoints,
                                selectedWaterId,
                                pastures,
                                selectedPastureId,
                                draftVertices.toList(),
                                selectedVertexIndex,
                                displayPreferences.coverageScope,
                                assignmentMap,
                                junctionUsageMap.filterValues { it.size > 1 }.keys,
                                activeSnappedJunction
                            )
                        }
                        map.addOnCameraMoveListener { currentZoom = map.cameraPosition.zoom }
                        map.addOnCameraIdleListener { currentZoom = map.cameraPosition.zoom }
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
            coverageScope = displayPreferences.coverageScope,
            pastureFillEnabled = displayPreferences.pastureFillEnabled,
            onModeSelected = { mode ->
                activeMode = mode
                mapInstance?.let { applyMapMode(it, mode) }
            },
            onLayersClick = { showLayersDialog = true }
        )

        if (interactionState == InteractionState.ORDINARY && selectedWater == null && selectedPasture == null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.82f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            interactionState = InteractionState.WATER_PLACEMENT
                            selectedWaterId = null
                            selectedPastureId = null
                        }
                    ) { Text("Add Water") }
                    Button(
                        onClick = {
                            interactionState = InteractionState.PASTURE_DRAWING
                            selectedWaterId = null
                            selectedPastureId = null
                            replaceDraft(emptyList())
                            undoSnapshots.clear()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D95))
                    ) { Text("Draw Pasture") }
                }
            }
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
                                pastureDao.savePastureBoundaryWithJunctions(
                                    pastureId = pastureId,
                                    vertices = draftVertices.toList(),
                                    junctionMoves = ephemeralJunctionMoves.toMap()
                                )
                                leaveGeometryMode()
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
                    onClose = { selectedPastureId = null },
                    onEditDetails = {
                        pastureEditName = pasture.pasture.name
                        pastureEditNotes = pasture.pasture.notes
                        showPastureDetailsDialog = true
                    },
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

        ActiveAttribution(activeMode, currentZoom)
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
                                        pastureDao.mergeJunctions(sourceId, targetId)
                                        showMergeDialog = false
                                        selectedMergeTargetId = null
                                        leaveGeometryMode()
                                        Toast.makeText(context, "Corners merged", Toast.LENGTH_SHORT).show()
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
                            pastureDao.deleteById(id)
                            selectedPastureId = null
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

    if (showLayersDialog) {
        AlertDialog(
            onDismissRequest = { showLayersDialog = false },
            title = { Text("Map Layers", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

private fun applyLayerVisibility(
    map: MapLibreMap,
    preferences: DisplayPreferences,
    isMovingWater: Boolean
) {
    val layerState = MapLayerVisibilityController.computeVisibility(
        preferences = preferences,
        isMovingWater = isMovingWater
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
        if (layerState.waterPinsVisible) {
            style.getLayer(MapConfig.LAYER_WATER_POINTS_HIGHLIGHT)?.setProperties(
                visibility(Property.VISIBLE)
            )
            style.getLayer(MapConfig.LAYER_WATER_POINTS)?.setProperties(
                visibility(Property.VISIBLE)
            )
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
    coverageScope: SpatialCoverageScope,
    pastureFillEnabled: Boolean,
    onModeSelected: (ActiveMapMode) -> Unit,
    onLayersClick: () -> Unit
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
                            activeMode == ActiveMapMode.LABELED -> "USGS Labeled • z$zoom"
                            currentZoom >= MapConfig.DETAIL_TRANSITION_ZOOM -> "USDA Detail • z$zoom"
                            else -> "USGS Overview • z$zoom"
                        },
                        fontSize = 12.sp
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
                val scopeLabel = when (coverageScope) {
                    SpatialCoverageScope.PHYSICAL_RADIUS -> "Physical"
                    SpatialCoverageScope.ACCESSIBLE_COVERAGE -> "Accessible"
                }
                Text(
                    "Layers • $scopeLabel • $coverageLabel${if (pastureFillEnabled) "" else " • No fill"}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
private fun BoxScope.PastureInspectionCard(
    pasture: PastureWithVertices,
    metrics: PastureCoverageMetrics,
    onClose: () -> Unit,
    onEditDetails: () -> Unit,
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
                OutlinedButton(
                    onClick = onEditDetails,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("Details", color = Color.White, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onEditBoundary,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text("Edit", color = Color.White, fontWeight = FontWeight.Bold)
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
            ?.setGeoJson(PastureFeatureConverter.toPastureFeatures(pastures, selectedPastureId))
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
    layer: String
) = map.queryRenderedFeatures(
    RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius),
    layer
)

private fun LatLng.toPastureCoordinate() = PastureCoordinate(latitude, longitude)

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
