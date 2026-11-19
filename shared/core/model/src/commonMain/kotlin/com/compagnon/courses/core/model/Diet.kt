package com.compagnon.courses.core.model

/**
 * The household's diet (one choice, onboarding step 2). It can reveal a religious belief or health data (GDPR
 * art. 9): it stays on the device.
 */
enum class Diet {
    /** « Je mange de tout ». */
    OMNIVORE,

    /** « Pescétarien »: fish, no meat. */
    PESCATARIAN,

    /** « Végétarien »: no meat, no fish. */
    VEGETARIAN,

    /** « Végan »: vegetarian, without dairy and eggs. Honey is not tagged yet. */
    VEGAN,
}

/** Something the household avoids (allergy, intolerance or choice), on top of its [Diet]. */
enum class Avoidance { GLUTEN, LACTOSE, NUTS, EGGS, ALCOHOL }

/** The single mapping between diets, avoidances and the catalog tags (`catalog-content/README.md` § Tags). */
object DietaryTags {
    val ALCOHOL: TagId = TagId("contains-alcohol")
    val GLUTEN: TagId = TagId("contains-gluten")
    val LACTOSE: TagId = TagId("contains-lactose")
    val MEAT: TagId = TagId("contains-meat")
    val FISH: TagId = TagId("contains-fish")
    val NUTS: TagId = TagId("contains-nuts")
    val EGGS: TagId = TagId("contains-eggs")

    /** Products of the baby aisle: only asked to a household with a baby. */
    val BABY: TagId = TagId("baby")

    /** Products of the pet aisle: only asked to a household with a pet. */
    val PET: TagId = TagId("pet")

    /** Every tag this object refers to (they must all exist in the catalog). */
    val ALL: Set<TagId> = setOf(ALCOHOL, GLUTEN, LACTOSE, MEAT, FISH, NUTS, EGGS, BABY, PET)

    fun excludedBy(diet: Diet): Set<TagId> = when (diet) {
        Diet.OMNIVORE -> emptySet()
        Diet.PESCATARIAN -> setOf(MEAT)
        Diet.VEGETARIAN -> setOf(MEAT, FISH)
        Diet.VEGAN -> setOf(MEAT, FISH, LACTOSE, EGGS)
    }

    fun excludedBy(avoidance: Avoidance): TagId = when (avoidance) {
        Avoidance.GLUTEN -> GLUTEN
        Avoidance.LACTOSE -> LACTOSE
        Avoidance.NUTS -> NUTS
        Avoidance.EGGS -> EGGS
        Avoidance.ALCOHOL -> ALCOHOL
    }

    /** The tags never asked about for [diet] and [avoid]. */
    fun excludedBy(diet: Diet, avoid: Set<Avoidance>): Set<TagId> = excludedBy(diet) + avoid.map(::excludedBy)

    /** Avoidances already implied by [diet] (shown checked and locked: « Déjà exclu par le régime végan »). */
    fun impliedBy(diet: Diet): Set<Avoidance> {
        val tags = excludedBy(diet)
        return Avoidance.entries.filterTo(LinkedHashSet()) { excludedBy(it) in tags }
    }
}
