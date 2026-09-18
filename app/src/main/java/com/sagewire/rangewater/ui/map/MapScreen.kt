package com.sagewire.rangewater.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.util.Locale
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

enum class ActiveMapMode {
    AERIAL,
    LABELED
}

/*
 * 🪨 BLOCK 1 — MAP HOST COMPOSABLE
 * Purpose: Hosts MapLibre with the verified lifecycle/memory bridge, hybrid imagery,
 *          and native water-point placement.
 * 🎮 Behavior: Switches overview/detail sources by zoom and Aerial/Labeled layers
 *    by visibility, while a memory-only GeoJSON overlay displays one water point
 *    and its 243.84-meter geodesic coverage ring.
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

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var activeMode by remember { mutableStateOf(ActiveMapMode.AERIAL) }
    var currentZoom by remember { mutableDoubleStateOf(initialZoom) }
    var isMapRendering by remember { mutableStateOf(true) }
    var hasLoadError by remember { mutableStateOf(false) }
    var isPlacementArmed by remember { mutableStateOf(false) }
    var placedWaterPoint by remember { mutableStateOf<LatLng?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
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

            override fun onLowMemory() {
                mapView.onLowMemory()
            }

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

    // 🪨 BLOCK 3 — ONE-SHOT MAP TAP LISTENER
    // Re-registering when the arm state changes gives the listener the current state;
    // disposal removes the exact listener instance and prevents accumulation.
    DisposableEffect(mapInstance, isPlacementArmed) {
        val map = mapInstance
        if (map == null) {
            onDispose { }
        } else {
            val clickListener = MapLibreMap.OnMapClickListener { coordinate ->
                if (!isPlacementArmed) {
                    false
                } else {
                    placedWaterPoint = coordinate
                    isPlacementArmed = false
                    map.getStyle { style -> updateWaterOverlay(style, coordinate) }
                    true
                }
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

                        map.setStyle(MapConfig.createStyleBuilder()) { style ->
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(initialLat, initialLng))
                                .zoom(initialZoom)
                                .build()
                            applyMapMode(map, activeMode)
                            placedWaterPoint?.let { updateWaterOverlay(style, it) }
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

        // 🖍️ BLOCK 4 — MAP MODE AND STATUS CONTROLS
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

        // 🖍️ BLOCK 5 — DELIBERATE WATER-PLACEMENT CONTROLS
        // Elevated above both provider attribution and MapLibre's required logo.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.8f),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { isPlacementArmed = !isPlacementArmed },
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
                        text = if (isPlacementArmed) "Cancel" else "Place Water",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (placedWaterPoint != null) {
                    OutlinedButton(
                        onClick = {
                            placedWaterPoint = null
                            isPlacementArmed = false
                            mapInstance?.getStyle(::clearWaterOverlay)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Clear", fontSize = 13.sp)
                    }
                }
            }
        }

        // 🌐 BLOCK 6 — ACTIVE SOURCE ATTRIBUTION
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
}

private fun updateWaterOverlay(style: Style, coordinate: LatLng) {
    val center = Point.fromLngLat(coordinate.longitude, coordinate.latitude)
    val ring = TurfTransformation.circle(
        center,
        MapConfig.BUFFER_RADIUS_METERS,
        MapConfig.BUFFER_CIRCLE_STEPS,
        TurfConstants.UNIT_METERS
    )

    style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_POINT)?.setGeoJson(center)
    style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_RING)?.setGeoJson(ring)
}

private fun clearWaterOverlay(style: Style) {
    val empty = FeatureCollection.fromFeatures(emptyArray<Feature>())
    style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_POINT)?.setGeoJson(empty)
    style.getSourceAs<GeoJsonSource>(MapConfig.SOURCE_WATER_RING)?.setGeoJson(empty)
}

private fun applyMapMode(map: MapLibreMap, mode: ActiveMapMode) {
    when (mode) {
        ActiveMapMode.AERIAL -> {
            map.setMaxZoomPreference(MapConfig.MAX_AERIAL_ZOOM)
        }

        ActiveMapMode.LABELED -> {
            if (map.cameraPosition.zoom > MapConfig.MAX_LABELED_ZOOM) {
                map.animateCamera(
                    CameraUpdateFactory.zoomTo(MapConfig.MAX_LABELED_ZOOM),
                    400,
                    object : MapLibreMap.CancelableCallback {
                        override fun onFinish() {
                            map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)
                        }

                        override fun onCancel() {
                            map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)
                        }
                    }
                )
            } else {
                map.setMaxZoomPreference(MapConfig.MAX_LABELED_ZOOM)
            }
        }
    }

    map.getStyle { style ->
        val aerialVisibility = if (mode == ActiveMapMode.AERIAL) {
            Property.VISIBLE
        } else {
            Property.NONE
        }
        val labeledVisibility = if (mode == ActiveMapMode.LABELED) {
            Property.VISIBLE
        } else {
            Property.NONE
        }

        style.getLayer(MapConfig.LAYER_AERIAL_OVERVIEW)
            ?.setProperties(visibility(aerialVisibility))
        style.getLayer(MapConfig.LAYER_AERIAL_DETAIL)
            ?.setProperties(visibility(aerialVisibility))
        style.getLayer(MapConfig.LAYER_LABELED_TOPO)
            ?.setProperties(visibility(labeledVisibility))
    }
}

@Composable
private fun mapModeChipColors() =
    FilterChipDefaults.filterChipColors(
        containerColor = Color.Black.copy(alpha = 0.6f),
        labelColor = Color.White,
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = Color.White
    )
