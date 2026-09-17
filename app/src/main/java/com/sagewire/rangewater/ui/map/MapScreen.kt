package com.sagewire.rangewater.ui.map

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView

/*
 * 🪨 BLOCK 1 — MAPLIBRE HOST COMPOSABLE
 * Purpose: Hosts MapLibre's native MapView inside Jetpack Compose.
 * 🎮 Behavior: Creates one MapView, loads the configured style, and forwards host
 *    lifecycle and memory-pressure callbacks to the OpenGL-backed view.
 * 🔧 Unfinished Work: Add water placement and buffer controls in RW-TX-003.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    initialLat: Double = MapConfig.DEFAULT_LATITUDE,
    initialLng: Double = MapConfig.DEFAULT_LONGITUDE,
    initialZoom: Double = MapConfig.DEFAULT_ZOOM
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var engineStatus by remember { mutableStateOf("Loading USGS imagery…") }

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    // 🪨 BLOCK 2 — NATIVE LIFECYCLE AND MEMORY BRIDGE
    // A guarded destroy path prevents duplicate MapView.onDestroy() calls when the
    // lifecycle event and Compose disposal happen during the same Activity teardown.
    DisposableEffect(lifecycleOwner, mapView, context) {
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

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            destroyMapViewOnce()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        map.setStyle(MapConfig.createStyleBuilder()) {
                            map.cameraPosition = CameraPosition.Builder()
                                .target(LatLng(initialLat, initialLng))
                                .zoom(initialZoom)
                                .build()
                            engineStatus = "RangeWater • ${MapConfig.ATTRIBUTION_LABEL}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 🌐 BLOCK 3 — PUBLIC DIAGNOSTIC STATUS
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.72f)
        ) {
            Text(
                text = engineStatus,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
