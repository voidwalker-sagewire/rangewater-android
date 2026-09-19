package com.sagewire.rangewater.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.sagewire.rangewater.data.RangeWaterDatabase
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
import com.sagewire.rangewater.spatial.AcreageCalculator
import com.sagewire.rangewater.spatial.GeometryValidator
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
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource

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
    val waterPoints by waterDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val pastures by pastureDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

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
    val undoSnapshots = remember { mutableStateListOf<List<PastureCoordinate>>() }
    var draftRevision by remember { mutableIntStateOf(0) }

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
    val effectiveWaterPoints = applyWaterMovePreview(
        waterPoints = waterPoints,
        movingWaterPoint = movingWaterPoint,
        draftLocation = draftWaterLocation
    )
    val mapView = remember { MapView(context).apply { onCreate(Bundle()) } }

    fun replaceDraft(vertices: List<PastureCoordinate>) {
        draftVertices.clear()
        draftVertices.addAll(vertices)
        draftRevision++
    }

    fun rememberUndoPoint() {
        undoSnapshots.add(draftVertices.toList())
    }

    fun leaveGeometryMode() {
        interactionState = InteractionState.ORDINARY
        draftVertices.clear()
        undoSnapshots.clear()
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
        pastures,
        selectedWaterId,
        selectedPastureId,
        selectedVertexIndex,
        draftRevision,
        interactionState,
        mapInstance
    ) {
        pushAllOverlays(
            map = mapInstance,
            waterPoints = effectiveWaterPoints,
            selectedWaterId = selectedWaterId,
            pastures = pastures,
            selectedPastureId = selectedPastureId,
            draftVertices = draftVertices.toList(),
            selectedVertexIndex = selectedVertexIndex
        )
    }
    LaunchedEffect(waterPoints, pastures, selectedWaterId, selectedPastureId) {
        if (selectedWaterId != null && selectedWater == null) selectedWaterId = null
        if (selectedPastureId != null && selectedPasture == null) selectedPastureId = null
    }

    // 🎮 BLOCK 4 — MODE-AWARE MAP TAP ROUTING
    DisposableEffect(mapInstance, interactionState, selectedVertexIndex, pastureEditMode) {
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
                        rememberUndoPoint()
                        draftVertices.add(coordinate.toPastureCoordinate())
                        selectedVertexIndex = draftVertices.lastIndex
                        draftRevision++
                    }
                    InteractionState.PASTURE_EDITING -> {
                        val screenPoint = map.projection.toScreenLocation(coordinate)
                        if (pastureEditMode == PastureEditMode.ADD_CORNER) {
                            val lineHit = queryFeaturesNear(
                                map,
                                screenPoint,
                                28f,
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
                                rememberUndoPoint()
                                draftVertices.add(projection.insertionIndex, projection.coordinate)
                                selectedVertexIndex = projection.insertionIndex
                                pastureEditMode = PastureEditMode.SELECT_OR_DRAG
                                draftRevision++
                                Toast.makeText(context, "Corner added", Toast.LENGTH_SHORT).show()
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
    DisposableEffect(mapView, mapInstance, interactionState, pastureEditMode) {
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
                            dragIndex = hit.getNumberProperty("index").toInt()
                            selectedVertexIndex = dragIndex
                            rememberUndoPoint()
                            map.uiSettings.isScrollGesturesEnabled = false
                            true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val index = dragIndex
                        if (index == null || index !in draftVertices.indices) {
                            false
                        } else {
                            val moved = map.projection.fromScreenLocation(PointF(event.x, event.y))
                            draftVertices[index] = moved.toPastureCoordinate()
                            draftRevision++
                            true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (dragIndex == null) {
                            false
                        } else {
                            dragIndex = null
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
                            pushAllOverlays(
                                map,
                                effectiveWaterPoints,
                                selectedWaterId,
                                pastures,
                                selectedPastureId,
                                draftVertices.toList(),
                                selectedVertexIndex
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
            onModeSelected = { mode ->
                activeMode = mode
                mapInstance?.let { applyMapMode(it, mode) }
            }
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
                        replaceDraft(undoSnapshots.removeAt(undoSnapshots.lastIndex))
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
                            scope.launch {
                                pastureDao.replaceVertices(pastureId, draftVertices.toList())
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
                    onDelete = { showWaterDeleteDialog = true }
                )
            }
        }

        selectedPasture?.let { pasture ->
            if (interactionState == InteractionState.ORDINARY) {
                PastureInspectionCard(
                    pasture = pasture,
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
                        selectedVertexIndex = null
                        pastureEditMode = PastureEditMode.SELECT_OR_DRAG
                    },
                    onDelete = { showPastureDeleteDialog = true }
                )
            }
        }

        ActiveAttribution(activeMode, currentZoom)
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
            text = { Text("This removes the saved water point and its 800-foot ring from this device.") },
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
}

@Composable
private fun MapStatusAndModeControls(
    activeMode: ActiveMapMode,
    currentZoom: Double,
    isMapRendering: Boolean,
    hasLoadError: Boolean,
    interactionState: InteractionState,
    onModeSelected: (ActiveMapMode) -> Unit
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
                        onClick = onRemove,
                        enabled = canRemove,
                        modifier = Modifier.weight(1f)
                    ) { Text("Remove", fontSize = 12.sp) }
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
    onClose: () -> Unit,
    onMove: () -> Unit,
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
                    Text("${point.sourceType.displayName()} • 800 ft ring", color = Color(0xFFFFD600), fontSize = 12.sp)
                }
                TextButton(onClick = onClose) { Text("Close", color = Color.LightGray) }
            }
            Text(
                String.format(Locale.US, "Lat %.5f  •  Lng %.5f", point.latitude, point.longitude),
                color = Color.LightGray,
                fontSize = 12.sp
            )
            if (point.notes.isNotBlank()) Text(point.notes, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            Button(
                onClick = onMove,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                )
            ) {
                Text("Move Location", fontWeight = FontWeight.Bold)
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
                        "Proposed: %.5f, %.5f • Previewing 800 ft ring",
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
    onClose: () -> Unit,
    onEditDetails: () -> Unit,
    onEditBoundary: () -> Unit,
    onDelete: () -> Unit
) {
    val acres = AcreageCalculator.calculateAcres(pasture.orderedCoordinates())
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
                    Text(String.format(Locale.US, "%.1f acres • Map estimate", acres), color = Color(0xFFFF2D95), fontSize = 12.sp)
                }
                TextButton(onClick = onClose) { Text("Close", color = Color.LightGray) }
            }
            if (pasture.pasture.notes.isNotBlank()) {
                Text(pasture.pasture.notes, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onEditDetails) { Text("Details") }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(onClick = onEditBoundary) { Text("Boundary") }
                Spacer(Modifier.width(6.dp))
                Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                    Text("Delete")
                }
            }
        }
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
    selectedVertexIndex: Int?
) {
    map?.getStyle { style ->
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_POINTS)
            ?.setGeoJson(WaterFeatureConverter.toPointFeatures(waterPoints, selectedWaterId))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_RINGS)
            ?.setGeoJson(WaterFeatureConverter.toRingFeatures(waterPoints, selectedWaterId))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_PASTURES)
            ?.setGeoJson(PastureFeatureConverter.toPastureFeatures(pastures, selectedPastureId))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_PASTURE_DRAFT)
            ?.setGeoJson(PastureFeatureConverter.draftFeature(draftVertices))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_PASTURE_HANDLES)
            ?.setGeoJson(PastureFeatureConverter.handleFeatures(draftVertices, selectedVertexIndex))
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
