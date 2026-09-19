package com.sagewire.rangewater.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapDisplayPreferencesPersistenceTest {
    private lateinit var context: Context
    private lateinit var repository: DisplayPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPreferences()
        repository = DisplayPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun firstLaunchReturnsSafeDefaults() {
        val preferences = repository.getPreferences()

        assertEquals(WaterCoverageMode.FULL, preferences.coverageMode)
        assertTrue(preferences.pastureFillEnabled)
    }

    @Test
    fun coverageModePersistsAcrossRepositoryInstances() {
        repository.saveCoverageMode(WaterCoverageMode.LINES_ONLY)
        assertEquals(
            WaterCoverageMode.LINES_ONLY,
            DisplayPreferencesRepository(context).getPreferences().coverageMode
        )

        repository.saveCoverageMode(WaterCoverageMode.OFF)
        assertEquals(
            WaterCoverageMode.OFF,
            DisplayPreferencesRepository(context).getPreferences().coverageMode
        )
    }

    @Test
    fun pastureFillPreferencePersistsAcrossRepositoryInstances() {
        repository.savePastureFillEnabled(false)
        assertFalse(DisplayPreferencesRepository(context).getPreferences().pastureFillEnabled)

        repository.savePastureFillEnabled(true)
        assertTrue(DisplayPreferencesRepository(context).getPreferences().pastureFillEnabled)
    }

    private fun clearPreferences() {
        context.getSharedPreferences(
            DisplayPreferencesRepository.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }
}
