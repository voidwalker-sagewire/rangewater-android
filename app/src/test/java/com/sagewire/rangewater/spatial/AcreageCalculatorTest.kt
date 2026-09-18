package com.sagewire.rangewater.spatial

import com.sagewire.rangewater.data.PastureCoordinate
import kotlin.math.cos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcreageCalculatorTest {
    @Test
    fun localizedOneAcrePastureCalculatesNearOneAcre() {
        val latitude = 40.0
        val sideMeters = 63.6149
        val latitudeDelta = sideMeters / 111_320.0
        val longitudeDelta = sideMeters / (111_320.0 * cos(Math.toRadians(latitude)))
        val pasture = listOf(
            PastureCoordinate(latitude, -81.0),
            PastureCoordinate(latitude, -81.0 + longitudeDelta),
            PastureCoordinate(latitude + latitudeDelta, -81.0 + longitudeDelta),
            PastureCoordinate(latitude + latitudeDelta, -81.0)
        )

        val acres = AcreageCalculator.calculateAcres(pasture)

        assertTrue("Expected approximately one acre, got $acres", acres in 0.97..1.03)
    }

    @Test
    fun squareMeterConversionUsesInternationalAcre() {
        assertEquals(1.0, AcreageCalculator.squareMetersToAcres(4046.8564224), 1e-9)
    }

    @Test
    fun fewerThanThreeVerticesHasNoArea() {
        assertEquals(
            0.0,
            AcreageCalculator.calculateAcres(
                listOf(PastureCoordinate(40.0, -81.0), PastureCoordinate(40.01, -81.0))
            ),
            0.0
        )
    }
}
