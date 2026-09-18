// 🪨 BLOCK 1 — ROOT BUILD ENGINE PLUGINS
// 🎮 Behavior: Binds the API 36-capable Android, Kotlin, and Compose plugins.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
