package com.sagewire.rangewater.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.RectF
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.sagewire.rangewater.data.RangeWaterDatabase
import com.sagewire.rangewater.data.WaterPointEntity
import com.sagewire.rangewater.data.WaterSourceType
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

enum class ActiveMapMode {
    AERIAL,
    LABELED
}

/*
 * 🪨 BLOCK 1 — PERSISTENT MULTI-WATER MAP HOST
 * Purpose: Preserves the verified MapLibre lifecycle while rendering Room-backed assets.
 * 🎮 Behavior: Adds, selects, edits, and deletes local water points; every point receives
 *    a derived 243.84-meter geodesic ring that remains above either imagery mode.
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
    val waterPointDao = remember(database) { database.waterPointDao() }
    val waterPoints by waterPointDao.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var activeMode by remember { mutableStateOf(ActiveMapMode.AERIAL) }
    var currentZoom by remember { mutableDoubleStateOf(initialZoom) }
    var isMapRendering by remember { mutableStateOf(true) }
    var hasLoadError by remember { mutableStateOf(false) }
    var isPlacementArmed by remember { mutableStateOf(false) }
    var selectedPointId by remember { mutableStateOf<Long?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editNotes by remember { mutableStateOf("") }
    var editType by remember { mutableStateOf(WaterSourceType.TROUGH) }

    val selectedPoint = waterPoints.firstOrNull { it.id == selectedPointId }
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }

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
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    mapView.onLowMemory()
                }
            }
        }
        val willRenderListener = MapView.OnWillStartRenderingMapListener {
            isMapRendering = true
        }
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
    LaunchedEffect(waterPoints, selectedPointId, mapInstance) {
        pushWaterOverlays(mapInstance, waterPoints, selectedPointId)
    }
    LaunchedEffect(waterPoints, selectedPointId) {
        if (selectedPointId != null && selectedPoint == null) {
            selectedPointId = null
            showEditDialog = false
            showDeleteDialog = false
        }
    }

    // 🎮 BLOCK 4 — PLACEMENT AND FEATURE-SELECTION TAP ROUTING
    DisposableEffect(mapInstance, isPlacementArmed, waterPointDao, scope) {
        val map = mapInstance
        if (map == null) {
            onDispose { }
        } else {
            val clickListener = MapLibreMap.OnMapClickListener { coordinate ->
                if (isPlacementArmed) {
                    isPlacementArmed = false
                    scope.launch {
                        selectedPointId = waterPointDao.insertWithDefaultName(
                            latitude = coordinate.latitude,
                            longitude = coordinate.longitude
                        )
                    }
                } else {
                    val screenPoint = map.projection.toScreenLocation(coordinate)
                    selectedPointId = map.queryRenderedFeatures(
                        RectF(
                            screenPoint.x - 24f,
                            screenPoint.y - 24f,
                            screenPoint.x + 24f,
                            screenPoint.y + 24f
                        ),
                        MapConfig.LAYER_WATER_POINTS
                    ).firstOrNull()?.getNumberProperty("id")?.toLong()
                }
                true
            }
            map.addOnMapClickListener(clickListener)
            onDispose { map.removeOnMapClickListener(clickListener) }
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
                            pushWaterOverlays(map, waterPoints, selectedPointId)
                        }
                        map.addOnCameraMoveListener {
                            currentZoom = map.cameraPosition.zoom
                        }
                        map.addOnCameraIdleListener {
                            currentZoom = map.cameraPosition.zoom
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 🖍️ BLOCK 5 — MAP STATUS AND MODE CONTROLS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
                .align(Alignment.TopCenter),
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
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = when {
                                        hasLoadError -> Color.Red
                                        isPlacementArmed -> Color(0xFF00E5FF)
                                        activeMode == ActiveMapMode.LABELED -> Color(0xFFFFC107)
                                        currentZoom >= MapConfig.DETAIL_TRANSITION_ZOOM -> Color(0xFF4CAF50)
                                        else -> Color(0xFF2196F3)
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        val formattedZoom = String.format(Locale.US, "%.1f", currentZoom)
                        Text(
                            text = when {
                                hasLoadError -> "Imagery load issue"
                                isPlacementArmed -> "Tap pasture to place • z$formattedZoom"
                                activeMode == ActiveMapMode.LABELED -> "USGS Labeled • z$formattedZoom"
                                currentZoom >= MapConfig.DETAIL_TRANSITION_ZOOM -> "USDA Detail • z$formattedZoom"
                                else -> "USGS Overview • z$formattedZoom"
                            },
                            fontSize = 12.sp
                        )
                        if (isMapRendering) {
                            Spacer(modifier = Modifier.width(7.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                strokeWidth = 1.5.dp,
                                color = Color.White
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = activeMode == ActiveMapMode.AERIAL,
                        onClick = {
                            activeMode = ActiveMapMode.AERIAL
                            mapInstance?.let { applyMapMode(it, ActiveMapMode.AERIAL) }
                        },
                        label = { Text("Aerial", fontSize = 12.sp) },
                        colors = mapModeChipColors()
                    )
                    FilterChip(
                        selected = activeMode == ActiveMapMode.LABELED,
                        onClick = {
                            activeMode = ActiveMapMode.LABELED
                            mapInstance?.let { applyMapMode(it, ActiveMapMode.LABELED) }
                        },
                        label = { Text("Labeled", fontSize = 12.sp) },
                        colors = mapModeChipColors()
                    )
                }
            }
        }

        // 🖍️ BLOCK 6 — ADD-WATER CONTROL
        if (selectedPoint == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.8f),
                tonalElevation = 4.dp
            ) {
                Button(
                    onClick = { isPlacementArmed = !isPlacementArmed },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlacementArmed) {
                            Color(0xFF00E5FF)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isPlacementArmed) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = if (isPlacementArmed) "Cancel" else "Add Water",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 🖍️ BLOCK 7 — SELECTED-ASSET INSPECTION CARD
        selectedPoint?.let { point ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 52.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E1E).copy(alpha = 0.96f),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(point.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "${point.sourceType.displayName()} • 800 ft ring",
                                color = Color(0xFFFFD600),
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = { selectedPointId = null }) {
                            Text("Close", color = Color.LightGray)
                        }
                    }
                    Text(
                        String.format(
                            Locale.US,
                            "Lat %.5f  •  Lng %.5f",
                            point.latitude,
                            point.longitude
                        ),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    if (point.notes.isNotBlank()) {
                        Text(point.notes, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                editName = point.name
                                editNotes = point.notes
                                editType = point.sourceType
                                showEditDialog = true
                            }
                        ) {
                            Text("Edit")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD32F2F)
                            )
                        ) {
                            Text("Delete")
                        }
                    }
                }
            }
        }

        // 🌐 BLOCK 8 — ACTIVE SOURCE ATTRIBUTION
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 16.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text = when {
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

    if (showEditDialog && selectedPoint != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Water Asset") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Water type", fontSize = 12.sp)
                    WaterSourceType.entries.chunked(3).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            rowTypes.forEach { type ->
                                FilterChip(
                                    selected = editType == type,
                                    onClick = { editType = type },
                                    label = { Text(type.displayName(), fontSize = 10.sp) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val current = selectedPoint
                        if (current != null) {
                            scope.launch {
                                waterPointDao.update(
                                    current.copy(
                                        name = editName.trim().ifBlank { current.name },
                                        sourceType = editType,
                                        notes = editNotes.trim(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                showEditDialog = false
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog && selectedPoint != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${selectedPoint.name}?") },
            text = { Text("This removes the saved water point and its 800-foot ring from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        val id = selectedPoint.id
                        showDeleteDialog = false
                        scope.launch {
                            waterPointDao.deleteById(id)
                            selectedPointId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun pushWaterOverlays(
    map: MapLibreMap?,
    points: List<WaterPointEntity>,
    selectedId: Long?
) {
    map?.getStyle { style ->
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_POINTS)
            ?.setGeoJson(WaterFeatureConverter.toPointFeatures(points, selectedId))
        style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_RINGS)
            ?.setGeoJson(WaterFeatureConverter.toRingFeatures(points, selectedId))
    }
}

private fun applyMapMode(map: MapLibreMap, mode: ActiveMapMode) {
    when (mode) {
        ActiveMapMode.AERIAL -> map.setMaxZoomPreference(MapConfig.MAX_AERIAL_ZOOM)
        ActiveMapMode.LABELED -> {
            if (map.cameraPosition.zoom > MapConfig.MAX_LABELED_ZOOM) {
                map.animateCamera(
                    CameraUpdateFactory.zoomTo(MapConfig.MAX_LABELED_ZOOM),
                    400,
                    object : MapLibreMap.CancelableCallback {
                        override fun onFinish() =
                            map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)

                        override fun onCancel() =
                            map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)
                    }
                )
            } else {
                map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)
            }
        }
    }

    map.getStyle { style ->
        val aerialVisibility = if (mode == ActiveMapMode.AERIAL) Property.VISIBLE else Property.NONE
        val labeledVisibility = if (mode == ActiveMapMode.LABELED) Property.VISIBLE else Property.NONE
        style.getLayer(MapConfig.LAYER_AERIAL_OVERVIEW)
            ?.setProperties(visibility(aerialVisibility))
        style.getLayer(MapConfig.LAYER_AERIAL_DETAIL)
            ?.setProperties(visibility(aerialVisibility))
        style.getLayer(MapConfig.LAYER_LABELED_TOPO)
            ?.setProperties(visibility(labeledVisibility))
    }
}

private fun WaterSourceType.displayName(): String =
    name.lowercase().replaceFirstChar { it.titlecase(Locale.US) }

@Composable
private fun mapModeChipColors() =
    FilterChipDefaults.filterChipColors(
        containerColor = Color.Black.copy(alpha = 0.6f),
        labelColor = Color.White,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = Color.White
    )
