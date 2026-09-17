package com.sagewire.rangewater

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.sagewire.rangewater.ui.map.MapScreen
import org.maplibre.android.MapLibre

/*
 * 🪨 BLOCK 1 — APPLICATION ENTRY HOST
 * Purpose: Application entry point and primary Activity.
 * 🎮 Behavior: Initializes MapLibre before creating its MapView, then hosts the map UI.
 * 🪨 Protected: MapLibre initialization must precede MapScreen composition.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MapScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}
