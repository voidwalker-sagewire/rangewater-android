package com.sagewire.rangewater.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ForageStandType {
    PERENNIAL_RYEGRASS_CLOVER,
    TALL_FESCUE_NITROGEN,
    TALL_FESCUE_CLOVER,
    OTHER_CUSTOM
}

enum class ForageStandCondition { EXCELLENT, GOOD, FAIR }

enum class ForageCalibrationSource {
    OHIO_NRCS_GLCI_GRAZING_STICK,
    CUSTOM_OPERATOR_VALUE
}

@Entity(
    tableName = "pasture_forage_observations",
    foreignKeys = [
        ForeignKey(
            entity = PastureEntity::class,
            parentColumns = ["id"],
            childColumns = ["pastureId"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["pastureId"]),
        Index(value = ["pastureId", "observedAt"])
    ]
)
data class PastureForageObservationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pastureId: Long,
    val observedAt: Long,
    val averageHeightInches: Double,
    val sampleCount: Int,
    val forageStandType: ForageStandType,
    val standCondition: ForageStandCondition,
    val residualHeightInches: Double = 3.0,
    val dmPerAcreInchLow: Double,
    val dmPerAcreInchHigh: Double,
    val calibrationSource: ForageCalibrationSource,
    val acreageSnapshot: Double,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    init {
        require(pastureId > 0) { "Pasture ID must be positive" }
        require(observedAt > 0) { "Observation time must be positive" }
        require(averageHeightInches.isFinite() && averageHeightInches > 0.0) {
            "Average forage height must be positive"
        }
        require(sampleCount > 0) { "Sample count must be positive" }
        require(residualHeightInches.isFinite() && residualHeightInches >= 0.0) {
            "Residual height cannot be negative"
        }
        require(dmPerAcreInchLow.isFinite() && dmPerAcreInchLow > 0.0) {
            "Dry-matter calibration must be positive"
        }
        require(dmPerAcreInchHigh.isFinite() && dmPerAcreInchHigh >= dmPerAcreInchLow) {
            "Dry-matter calibration range is invalid"
        }
        require(acreageSnapshot.isFinite() && acreageSnapshot > 0.0) {
            "Acreage snapshot must be positive"
        }
        if (forageStandType == ForageStandType.OTHER_CUSTOM) {
            require(calibrationSource == ForageCalibrationSource.CUSTOM_OPERATOR_VALUE) {
                "Other forage stands require a custom calibration"
            }
        }
    }
}

