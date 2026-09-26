package com.sagewire.rangewater.data

import kotlin.math.max

data class ForageCalibrationRange(val low: Double, val high: Double) {
    init {
        require(low > 0.0 && high >= low)
    }
}

data class ForageEstimate(
    val availableHeightInches: Double,
    val availableDmLowLbsPerAcre: Double,
    val availableDmHighLbsPerAcre: Double,
    val mappedStockpileLowLbs: Double,
    val mappedStockpileHighLbs: Double
) {
    val mappedStockpileLowTons: Double get() = mappedStockpileLowLbs / 2_000.0
    val mappedStockpileHighTons: Double get() = mappedStockpileHighLbs / 2_000.0
}

object ForageCalculator {
    private val ohioPreset = mapOf(
        (ForageStandType.PERENNIAL_RYEGRASS_CLOVER to ForageStandCondition.EXCELLENT) to ForageCalibrationRange(350.0, 400.0),
        (ForageStandType.PERENNIAL_RYEGRASS_CLOVER to ForageStandCondition.GOOD) to ForageCalibrationRange(250.0, 300.0),
        (ForageStandType.PERENNIAL_RYEGRASS_CLOVER to ForageStandCondition.FAIR) to ForageCalibrationRange(150.0, 200.0),
        (ForageStandType.TALL_FESCUE_NITROGEN to ForageStandCondition.EXCELLENT) to ForageCalibrationRange(350.0, 400.0),
        (ForageStandType.TALL_FESCUE_NITROGEN to ForageStandCondition.GOOD) to ForageCalibrationRange(200.0, 250.0),
        (ForageStandType.TALL_FESCUE_NITROGEN to ForageStandCondition.FAIR) to ForageCalibrationRange(100.0, 150.0),
        (ForageStandType.TALL_FESCUE_CLOVER to ForageStandCondition.EXCELLENT) to ForageCalibrationRange(350.0, 400.0),
        (ForageStandType.TALL_FESCUE_CLOVER to ForageStandCondition.GOOD) to ForageCalibrationRange(300.0, 350.0),
        (ForageStandType.TALL_FESCUE_CLOVER to ForageStandCondition.FAIR) to ForageCalibrationRange(150.0, 200.0)
    )

    fun ohioCalibration(
        standType: ForageStandType,
        condition: ForageStandCondition
    ): ForageCalibrationRange = requireNotNull(ohioPreset[standType to condition]) {
        "The Ohio grazing-stick preset does not define $standType"
    }

    fun estimate(
        averageHeightInches: Double,
        residualHeightInches: Double,
        calibration: ForageCalibrationRange,
        acreage: Double
    ): ForageEstimate {
        require(averageHeightInches.isFinite() && averageHeightInches > 0.0)
        require(residualHeightInches.isFinite() && residualHeightInches >= 0.0)
        require(acreage.isFinite() && acreage > 0.0)
        val availableHeight = max(averageHeightInches - residualHeightInches, 0.0)
        val lowPerAcre = availableHeight * calibration.low
        val highPerAcre = availableHeight * calibration.high
        return ForageEstimate(
            availableHeightInches = availableHeight,
            availableDmLowLbsPerAcre = lowPerAcre,
            availableDmHighLbsPerAcre = highPerAcre,
            mappedStockpileLowLbs = lowPerAcre * acreage,
            mappedStockpileHighLbs = highPerAcre * acreage
        )
    }

    fun estimate(observation: PastureForageObservationEntity): ForageEstimate = estimate(
        averageHeightInches = observation.averageHeightInches,
        residualHeightInches = observation.residualHeightInches,
        calibration = ForageCalibrationRange(
            observation.dmPerAcreInchLow,
            observation.dmPerAcreInchHigh
        ),
        acreage = observation.acreageSnapshot
    )
}

