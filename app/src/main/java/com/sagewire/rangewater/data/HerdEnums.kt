package com.sagewire.rangewater.data

enum class CountUnit { HEAD, PAIRS }

enum class StockClass(val displayName: String) {
    COW_CALF_PAIRS("Cow-Calf Pairs"),
    STOCKERS_YEARLINGS("Stockers/Yearlings"),
    DRY_COWS("Dry Cows"),
    BULLS("Bulls"),
    REPLACEMENT_HEIFERS("Replacement Heifers"),
    CALVES("Calves"),
    MIXED("Mixed"),
    OTHER("Other")
}

enum class HerdLocationKind { PASTURE, PEN, IN_TRANSIT, OFF_RANCH, UNKNOWN }

enum class MovementStatus { PLANNED, COMPLETED, CANCELED }
