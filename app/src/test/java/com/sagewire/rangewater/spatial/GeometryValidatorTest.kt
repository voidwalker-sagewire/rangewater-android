package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryValidatorTest {
    @Test
    fun simplePastureIsAccepted() {
        val pasture = listOf(
            PastureCoordinate(40.0, -81.0),
            PastureCoordinate(40.0, -80.99),
            PastureCoordinate(40.01, -80.99),
            PastureCoordinate(40.01, -81.0)
        )

        assertFalse(GeometryValidator.isSelfIntersecting(pasture))
        assertEquals(null, GeometryValidator.validationError(pasture))
    }

    @Test
    fun figureEightIsRejected() {
        val figureEight = listOf(
            PastureCoordinate(40.0, -81.0),
            PastureCoordinate(40.01, -80.99),
            PastureCoordinate(40.0, -80.99),
            PastureCoordinate(40.01, -81.0)
        )

        assertTrue(GeometryValidator.isSelfIntersecting(figureEight))
        assertNotNull(GeometryValidator.validationError(figureEight))
    }

    @Test
    fun duplicateAndCollinearVerticesAreRejected() {
        val duplicate = listOf(
            PastureCoordinate(40.0, -81.0),
            PastureCoordinate(40.0, -81.0),
            PastureCoordinate(40.01, -80.99)
        )
        val collinear = listOf(
            PastureCoordinate(40.0, -81.0),
            PastureCoordinate(40.01, -81.0),
            PastureCoordinate(40.02, -81.0)
        )

        assertNotNull(GeometryValidator.validationError(duplicate))
        assertNotNull(GeometryValidator.validationError(collinear))
    }
}
