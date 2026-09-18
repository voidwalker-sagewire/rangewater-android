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

    @Test
    fun tapProjectsOntoClosestSegmentAndReturnsInsertionIndex() {
        val square = listOf(
            PastureCoordinate(40.0, -81.0),
            PastureCoordinate(40.0, -80.99),
            PastureCoordinate(40.01, -80.99),
            PastureCoordinate(40.01, -81.0)
        )

        val result = GeometryValidator.projectOntoClosestSegment(
            PastureCoordinate(40.005, -80.9899),
            square
        )!!

        assertEquals(2, result.insertionIndex)
        assertEquals(-80.99, result.coordinate.longitude, 1e-9)
        assertEquals(40.005, result.coordinate.latitude, 1e-9)
        assertTrue(result.distanceMeters in 8.0..10.0)
    }

    @Test
    fun closingSegmentInsertionAppendsAfterLastStoredVertex() {
        val triangle = listOf(
            PastureCoordinate(40.0, -81.0),
            PastureCoordinate(40.0, -80.99),
            PastureCoordinate(40.01, -81.0)
        )

        val result = GeometryValidator.projectOntoClosestSegment(
            PastureCoordinate(40.005, -81.0001),
            triangle
        )!!

        assertEquals(3, result.insertionIndex)
        assertEquals(-81.0, result.coordinate.longitude, 1e-9)
    }
}
