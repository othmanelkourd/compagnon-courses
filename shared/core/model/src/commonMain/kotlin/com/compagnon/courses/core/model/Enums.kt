package com.compagnon.courses.core.model

/** What a unit measures. Base units: gram (mass), milliliter (volume), piece (count); packages have no base. */
enum class Dimension { MASS, VOLUME, COUNT, PACKAGE }

enum class MeasurementSystem { METRIC, US_CUSTOMARY, UK_MIXED }

/** Repurchase cycle of a product; long-cycle products feed the "forgotten items" radar. */
enum class CycleClass { SHORT, MEDIUM, LONG }

/** Availability of a product in one country. */
enum class Availability(val wireName: String) {
    COMMON("common"),
    AVAILABLE("available"),
    RARE("rare"),
    UNAVAILABLE("unavailable"),
    ;

    companion object {
        /** Parses the API/seed value (`common`, `available`, `rare`, `unavailable`); null when unknown. */
        fun fromWireName(value: String): Availability? = entries.firstOrNull { it.wireName == value }
    }
}

/** How an exact value is rounded to an integer number of units. All values handled here are non-negative. */
enum class Rounding {
    /** Toward zero (floor). */
    DOWN,

    /** Away from zero (ceiling). */
    UP,

    /** To the nearest; ties go up. */
    HALF_UP,

    /** To the nearest; ties go to the even neighbour (banker's rounding). */
    HALF_EVEN,
}
