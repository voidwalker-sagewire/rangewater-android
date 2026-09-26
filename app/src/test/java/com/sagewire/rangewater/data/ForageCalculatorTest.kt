package com.sagewire.rangewater.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ForageCalculatorTest {
    @Test
    fun ohioPreset_matchesEveryValuePrintedOnTheGrazingStick() {
        assertRange(ForageStandType.PERENNIAL_RYEGRASS_CLOVER, ForageStandCondition.EXCELLENT, 350.0, 400.0)
        assertRange(ForageStandType.PERENNIAL_RYEGRASS_CLOVER, ForageStandCondition.GOOD, 250.0, 300.0)
        assertRange(ForageStandType.PERENNIAL_RYEGRASS_CLOVER, ForageStandCondition.FAIR, 150.0, 200.0)
        assertRange(ForageStandType.TALL_FESCUE_NITROGEN, ForageStandCondition.EXCELLENT, 350.0, 400.0)
        assertRange(ForageStandType.TALL_FESCUE_NITROGEN, ForageStandCondition.GOOD, 200.0, 250.0)
        assertRange(ForageStandType.TALL_FESCUE_NITROGEN, ForageStandCondition.FAIR, 100.0, 150.0)
        assertRange(ForageStandType.TALL_FESCUE_CLOVER, ForageStandCondition.EXCELLENT, 350.0, 400.0)
        assertRange(ForageStandType.TALL_FESCUE_CLOVER, ForageStandCondition.GOOD, 300.0, 350.0)
        assertRange(ForageStandType.TALL_FESCUE_CLOVER, ForageStandCondition.FAIR, 150.0, 200.0)
    }

    @Test
    fun estimate_subtractsResidualThenScalesByAcreage() {
        val result = ForageCalculator.estimate(
            averageHeightInches = 8.0,
            residualHeightInches = 3.0,
            calibration = ForageCalibrationRange(300.0, 350.0),
            acreage = 12.0
        )

        assertEquals(5.0, result.availableHeightInches, 0.0)
        assertEquals(1_500.0, result.availableDmLowLbsPerAcre, 0.0)
        assertEquals(1_750.0, result.availableDmHighLbsPerAcre, 0.0)
        assertEquals(18_000.0, result.mappedStockpileLowLbs, 0.0)
        assertEquals(21_000.0, result.mappedStockpileHighLbs, 0.0)
        assertEquals(9.0, result.mappedStockpileLowTons, 0.0)
        assertEquals(10.5, result.mappedStockpileHighTons, 0.0)
    }

    @Test
    fun estimate_neverReportsNegativeAvailableForage() {
        val result = ForageCalculator.estimate(
            averageHeightInches = 2.5,
            residualHeightInches = 3.0,
            calibration = ForageCalibrationRange(300.0, 350.0),
            acreage = 12.0
        )

        assertEquals(0.0, result.availableHeightInches, 0.0)
        assertEquals(0.0, result.mappedStockpileHighLbs, 0.0)
    }

    @Test
    fun ohioPreset_rejectsUncalibratedOtherStand() {
        try {
            ForageCalculator.ohioCalibration(ForageStandType.OTHER_CUSTOM, ForageStandCondition.GOOD)
            fail("Expected an undefined-preset rejection")
        } catch (_: IllegalArgumentException) {
            // A custom stand requires the operator's own calibration.
        }
    }

    private fun assertRange(
        type: ForageStandType,
        condition: ForageStandCondition,
        low: Double,
        high: Double
    ) {
        val actual = ForageCalculator.ohioCalibration(type, condition)
        assertEquals(low, actual.low, 0.0)
        assertEquals(high, actual.high, 0.0)
    }
}
