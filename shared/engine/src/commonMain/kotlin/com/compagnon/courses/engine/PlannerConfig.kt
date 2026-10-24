package com.compagnon.courses.engine

/** Tiers of `docs/question-engine.md` step 4. */
enum class Tier {
    /** p > 0.8: pre-checked on the « Probablement nécessaire » screen. */
    T1,

    /** 0.3 < p ≤ 0.8: one card per product. */
    T2,

    /** 0.1 < p ≤ 0.3: behind the gate question of its category. */
    T3,

    /** p ≤ 0.1: never asked; reachable through search and « Autre chose ? ». */
    T4,
}

/**
 * Coefficients of the need score p (step 3). All of them are still to be calibrated.
 *
 * @property referenceHouseholdSize P_ref: household size the country's `typicalRepurchaseDays` refers to.
 * @property sizeExponent β in `(P_ref / P_eq)^β`.
 * @property childWeight weight of a child in P_eq = adults + 0.6 × children.
 * @property shrinkage k in `Î = (n × EWMA + k × I_prior) / (n + k)`.
 * @property slope steepness of `p₀ = 1 / (1 + e^(−slope × (r − 1)))`.
 * @property stapleWeight cold-start weight of a staple product (`p₀ = penetration × weight`).
 * @property defaultPenetration cold-start penetration when the market has none.
 */
data class ScoreConfig(
    val referenceHouseholdSize: Double = 2.0,
    val sizeExponent: Double = 0.7,
    val childWeight: Double = 0.6,
    val sizeFactorMin: Double = 0.3,
    val sizeFactorMax: Double = 3.0,
    val shrinkage: Double = 2.0,
    val quantityCorrectionMin: Double = 0.5,
    val quantityCorrectionMax: Double = 2.0,
    val slope: Double = 4.0,
    val stapleWeight: Double = 1.0,
    val nonStapleWeight: Double = 0.5,
    val defaultPenetration: Double = 0.2,
    val highSeasonOdds: Double = 1.3,
    val lowSeasonOdds: Double = 0.7,
    val guestsOdds: Double = 1.2,
    val consecutiveNoOdds: Double = 0.6,
    val watchFloor: Double = 0.5,
) {
    init {
        require(referenceHouseholdSize > 0.0 && shrinkage >= 0.0 && slope > 0.0) { "Invalid score coefficients" }
        require(sizeFactorMin in 0.0..sizeFactorMax) { "Invalid household size factor bounds" }
        require(quantityCorrectionMin in 0.0..quantityCorrectionMax) { "Invalid quantity correction bounds" }
        require(watchFloor in 0.0..1.0) { "watchFloor must be a probability" }
    }
}

/**
 * Planner settings (steps 4 to 6). Budgets count questions: one per meta question, one for the T1 screen,
 * one per card and one per category gate.
 *
 * @property radarSlots reserved for long-cycle products already bought before (« radar des oubliés », F04).
 * @property explorationSlots reserved for never-bought products picked with the session seed (V2 territory,
 *   off by default).
 */
data class PlannerConfig(
    val defaultBudget: Int = 15,
    val quickBudget: Int = 12,
    val maxMetaQuestions: Int = 3,
    val radarSlots: Int = 2,
    val explorationSlots: Int = 0,
    val suggestedAbove: Double = 0.8,
    val cardAbove: Double = 0.3,
    val gateAbove: Double = 0.1,
    val overflowToSuggestedAbove: Double = 0.6,
    val score: ScoreConfig = ScoreConfig(),
) {
    init {
        require(defaultBudget >= 0 && quickBudget >= 0) { "Budgets cannot be negative" }
        require(maxMetaQuestions >= 0 && radarSlots >= 0 && explorationSlots >= 0) { "Slots cannot be negative" }
        require(gateAbove <= cardAbove && cardAbove <= suggestedAbove) { "Tier thresholds must be increasing" }
    }

    fun tierOf(probability: Double): Tier = when {
        probability > suggestedAbove -> Tier.T1
        probability > cardAbove -> Tier.T2
        probability > gateAbove -> Tier.T3
        else -> Tier.T4
    }

    /** Q_max for [context], or null when the session has no budget ([SessionMode.FULL]). */
    fun budgetFor(context: SessionContext): Int? = when (context.mode) {
        SessionMode.FULL -> null
        SessionMode.QUICK -> context.budgetOverride ?: quickBudget
        SessionMode.HOME_TOUR, SessionMode.ZONE -> context.budgetOverride ?: defaultBudget
    }
}
