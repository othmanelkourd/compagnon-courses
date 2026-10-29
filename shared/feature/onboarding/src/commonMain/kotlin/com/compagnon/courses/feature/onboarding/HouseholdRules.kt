package com.compagnon.courses.feature.onboarding

import com.compagnon.courses.core.data.Household
import com.compagnon.courses.core.data.HouseholdSetup
import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.Avoidance
import com.compagnon.courses.core.model.Catalog
import com.compagnon.courses.core.model.Diet
import com.compagnon.courses.core.model.DietaryTags
import com.compagnon.courses.core.model.PetKind
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId

internal fun DietChoice.toModel(): Diet = Diet.valueOf(name)

internal fun Diet.toUi(): DietChoice = DietChoice.valueOf(name)

internal fun AvoidChoice.toModel(): Avoidance = Avoidance.valueOf(name)

internal fun Avoidance.toUi(): AvoidChoice = AvoidChoice.valueOf(name)

internal fun PetChoice.toModel(): PetKind = PetKind.valueOf(name)

internal fun PetKind.toUi(): PetChoice = PetChoice.valueOf(name)

/**
 * The products a household may choose as essentials or recurring products: available, with a unit, not
 * excluded by its diet or avoidances; baby products with a baby, pet products with a pet.
 */
internal class Eligibility(
    diet: Diet,
    avoid: Set<Avoidance>,
    private val hasBaby: Boolean,
    private val hasPet: Boolean,
) {
    private val excluded = DietaryTags.excludedBy(diet, avoid)

    fun accepts(product: Product): Boolean =
        product.market.availability != Availability.UNAVAILABLE &&
            product.unitPolicies.isNotEmpty() &&
            product.tags.none { it in excluded } &&
            (hasBaby || DietaryTags.BABY !in product.tags) &&
            (hasPet || DietaryTags.PET !in product.tags)

    companion object {
        fun of(setup: HouseholdSetup): Eligibility = Eligibility(
            diet = setup.diet,
            avoid = setup.avoid,
            hasBaby = AgeBand.BABY in setup.children,
            hasPet = setup.pets.isNotEmpty(),
        )
    }
}

/**
 * Essentials pre-checked until the household chooses: the catalog's staples, plus the usual first products of
 * a baby or of the household's pets (editorial choice on the FR seed).
 */
internal fun defaultEssentials(catalog: Catalog, setup: HouseholdSetup): Set<ProductId> {
    val eligibility = Eligibility.of(setup)
    val starters = buildSet {
        if (AgeBand.BABY in setup.children) addAll(BABY_STARTERS)
        setup.pets.forEach { addAll(PET_STARTERS[it].orEmpty()) }
    }
    return catalog.products.filter { eligibility.accepts(it) && (it.isStaple || it.slug in starters) }
        .mapTo(LinkedHashSet()) { it.id }
}

/** Order of the products of an aisle: most popular first, then most common, then name. */
internal val byPopularity: Comparator<Product> = compareBy<Product> { it.market.popularityRank ?: Int.MAX_VALUE }
    .thenByDescending { it.market.householdPenetration ?: 0.0 }
    .thenBy { it.name.text }

/** The cadence closest to [days] (« Semaine », « 2 semaines », « Mois »). */
internal fun nearestCadence(days: Int?): CadenceChoice =
    if (days == null) CadenceChoice.WEEK else CadenceChoice.entries.minBy { kotlin.math.abs(it.days - days) }

internal fun rhythmChoiceOf(days: Int): RhythmChoice = when (days) {
    CadenceChoice.WEEK.days -> RhythmChoice.WEEK
    CadenceChoice.TWO_WEEKS.days -> RhythmChoice.TWO_WEEKS
    CadenceChoice.MONTH.days -> RhythmChoice.MONTH
    else -> RhythmChoice.OTHER
}

/** The summary of [setup]: essentials are those it chose, or the catalog's when it did not choose. */
internal fun summaryOf(setup: HouseholdSetup, catalog: Catalog?, onboarded: Boolean): HouseholdSummaryUi {
    val eligibility = Eligibility.of(setup)
    val products = catalog?.products.orEmpty()
    val essentials = setup.essentials?.let { chosen -> products.count { it.id in chosen && eligibility.accepts(it) } }
        ?: products.count { it.isStaple && eligibility.accepts(it) }
    return HouseholdSummaryUi(
        adults = setup.adults,
        babies = setup.children.count { it == AgeBand.BABY },
        children = setup.children.count { it == AgeBand.CHILD },
        teens = setup.children.count { it == AgeBand.TEEN },
        pets = setup.pets.sortedBy { it.ordinal }.map { it.toUi() },
        diet = setup.diet.toUi(),
        avoid = setup.avoid.sortedBy { it.ordinal }.map { it.toUi() },
        essentials = essentials,
        recurring = setup.cadences.orEmpty().size,
        horizonDays = setup.usualHorizonDays,
        onboarded = onboarded,
    )
}

/** The saved household as a [HouseholdSetup] (essentials and cadences read separately). */
internal fun Household.toSetup(
    essentials: Set<ProductId>,
    cadences: Map<ProductId, Int>,
): HouseholdSetup = HouseholdSetup(
    adults = profile.adults,
    children = profile.children,
    pets = profile.pets.mapTo(LinkedHashSet()) { it.kind },
    diet = profile.diet,
    avoid = profile.avoid,
    essentials = if (profile.customEssentials) essentials else null,
    cadences = cadences,
    usualHorizonDays = usualHorizonDays,
)

private val BABY_STARTERS = setOf("couches", "lingettes-bebe")

private val PET_STARTERS: Map<PetKind, Set<String>> = mapOf(
    PetKind.CAT to setOf("croquettes-chat", "litiere"),
    PetKind.DOG to setOf("croquettes-chien"),
)
