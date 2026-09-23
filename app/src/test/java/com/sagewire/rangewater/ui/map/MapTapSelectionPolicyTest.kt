package com.sagewire.rangewater.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTapSelectionPolicyTest {
    @Test
    fun `wide ranch view does not use expanded gate target`() {
        assertFalse(MapTapSelectionPolicy.useExpandedGateTarget(13.9))
        assertFalse(MapTapSelectionPolicy.useExpandedGateTarget(15.9))
    }

    @Test
    fun `detail view restores expanded gate target`() {
        assertTrue(MapTapSelectionPolicy.useExpandedGateTarget(16.0))
        assertTrue(MapTapSelectionPolicy.useExpandedGateTarget(20.0))
    }
}
