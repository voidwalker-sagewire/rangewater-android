package com.sagewire.rangewater.ui.help

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
class HelpPreferencesRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: HelpPreferencesRepository

    @Before
    fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(HelpPreferencesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        repository = HelpPreferencesRepository(context)
    }

    @After
    fun cleanUp() {
        context.getSharedPreferences(HelpPreferencesRepository.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun defaultsPersistAndResetWithoutRanchData() {
        assertTrue(repository.get().fieldTipsEnabled)
        assertTrue(repository.get().contextualHintsEnabled)
        assertFalse(repository.get().welcomeSeen)

        repository.setFieldTipsEnabled(false)
        repository.setContextualHintsEnabled(false)
        repository.markWelcomeSeen()
        repository.dismissHint("draw_pasture")
        assertEquals(0, repository.advanceTip(3))
        assertEquals(1, repository.advanceTip(3))

        val reopened = HelpPreferencesRepository(context)
        assertFalse(reopened.get().fieldTipsEnabled)
        assertFalse(reopened.get().contextualHintsEnabled)
        assertTrue(reopened.get().welcomeSeen)
        assertTrue("draw_pasture" in reopened.get().dismissedHintIds)

        reopened.resetHints()
        reopened.replayWelcome()
        assertTrue(reopened.get().dismissedHintIds.isEmpty())
        assertFalse(reopened.get().welcomeSeen)
    }
}
