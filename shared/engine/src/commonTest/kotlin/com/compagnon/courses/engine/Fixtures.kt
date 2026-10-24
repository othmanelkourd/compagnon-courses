package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.Category
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CountryCode
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.HouseholdProfile
import com.compagnon.courses.core.model.LocalizedName
import com.compagnon.courses.core.model.MeasurementSystem
import com.compagnon.courses.core.model.PackageFormat
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.ProductMarket
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.Rational
import com.compagnon.courses.core.model.StorageZone
import com.compagnon.courses.core.model.TagId
import com.compagnon.courses.core.model.UnitCatalog
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.model.UnitDefinition
import com.compagnon.courses.core.model.UnitPolicy
import com.compagnon.courses.core.model.ZoneId
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Small catalog and household builders shared by the engine tests. */
internal object Fixtures {
    val NOW: Instant = Instant.parse("2026-10-24T10:00:00Z")

    val GRAM = UnitCode.GRAM
    val KILOGRAM = UnitCode.KILOGRAM
    val MILLILITER = UnitCode.MILLILITER
    val LITER = UnitCode.LITER
    val PIECE = UnitCode.PIECE
    val DOZEN = UnitCode("dozen")
    val BOTTLE = UnitCode("bottle")
    val BOX = UnitCode("box")

    val UNITS: UnitCatalog = UnitCatalog(
        listOf(
            UnitDefinition(GRAM, Dimension.MASS, Rational.ONE),
            UnitDefinition(KILOGRAM, Dimension.MASS, Rational.of(1000)),
            UnitDefinition(MILLILITER, Dimension.VOLUME, Rational.ONE),
            UnitDefinition(LITER, Dimension.VOLUME, Rational.of(1000)),
            UnitDefinition(PIECE, Dimension.COUNT, Rational.ONE),
            UnitDefinition(DOZEN, Dimension.COUNT, Rational.of(12)),
            UnitDefinition(BOTTLE, Dimension.PACKAGE, null),
            UnitDefinition(BOX, Dimension.PACKAGE, null),
        ),
    )

    val DAIRY = category("cremerie", position = 20)
    val PRODUCE = category("fruits-legumes", position = 10)
    val CLEANING = category("entretien", position = 30)
    val DRINKS = category("boissons", position = 40)
    val CATEGORIES: List<Category> = listOf(DAIRY, PRODUCE, CLEANING, DRINKS)

    val FRIDGE = zone("frigo", position = 10)
    val CUPBOARD = zone("placard", position = 20)
    val FRUIT_BASKET = zone("fruits", position = 30)
    val ZONES: List<StorageZone> = listOf(FRIDGE, CUPBOARD, FRUIT_BASKET)

    val PORK = TagId("pork")

    fun category(slug: String, position: Int): Category = Category(
        id = CategoryId("cat-$slug"),
        slug = slug,
        position = position,
        name = slug.replaceFirstChar { it.uppercase() },
        gateQuestion = "Besoin de quelque chose en $slug ?",
    )

    fun zone(slug: String, position: Int): StorageZone = StorageZone(ZoneId("zone-$slug"), slug, position, slug)

    fun policy(
        unit: UnitCode,
        step: String = "1",
        presets: List<String> = listOf("1", "2"),
        min: String? = null,
        max: String? = null,
        isDefault: Boolean = true,
    ): UnitPolicy = UnitPolicy(
        unit = unit,
        isDefault = isDefault,
        step = Quantity.parse(step, unit),
        presets = presets.map { Quantity.parse(it, unit) },
        min = min?.let { Quantity.parse(it, unit) },
        max = max?.let { Quantity.parse(it, unit) },
    )

    /**
     * A product whose cold-start score is exactly [penetration] (staple weight 1), so that tests choose the
     * tier directly.
     */
    fun product(
        slug: String,
        penetration: Double? = 0.5,
        category: Category = DAIRY,
        zone: StorageZone = FRIDGE,
        dimension: Dimension = Dimension.VOLUME,
        cycleClass: CycleClass = CycleClass.SHORT,
        isStaple: Boolean = true,
        repurchaseDays: Int? = null,
        candidate: Boolean = true,
        availability: Availability = Availability.COMMON,
        policies: List<UnitPolicy> = listOf(policy(LITER)),
        packages: List<PackageFormat> = emptyList(),
        tags: Set<TagId> = emptySet(),
        popularityRank: Int? = null,
    ): Product = Product(
        id = ProductId("prod-$slug"),
        slug = slug,
        categoryId = category.id,
        name = LocalizedName(slug),
        dimension = dimension,
        storageZone = zone.id,
        cycleClass = cycleClass,
        isStaple = isStaple,
        market = ProductMarket(
            availability = availability,
            typicalRepurchaseDays = repurchaseDays,
            householdPenetration = penetration,
            isQuestionCandidate = candidate,
            popularityRank = popularityRank,
        ),
        unitPolicies = policies,
        packages = packages,
        tags = tags,
    )

    fun household(adults: Int = 2, excludedTags: Set<TagId> = emptySet()): HouseholdProfile = HouseholdProfile(
        adults = adults,
        country = CountryCode("FR"),
        system = MeasurementSystem.METRIC,
        excludedTags = excludedTags,
    )

    fun boughtDaysAgo(days: Int, purchases: Int = 1): ProductStats =
        ProductStats(nPurchases = purchases, lastAt = NOW - days.days)

    fun context(
        mode: SessionMode = SessionMode.QUICK,
        persons: Int = 2,
        budget: Int? = null,
        meta: List<MetaKey> = emptyList(),
        horizonDays: Int = 7,
        zone: StorageZone? = null,
        categoryOrder: List<Category> = emptyList(),
        reuseLastList: Boolean = false,
        guests: Int = 0,
    ): SessionContext = SessionContext(
        mode = mode,
        horizonDays = horizonDays,
        usualHorizonDays = 7,
        persons = persons,
        guests = guests,
        zone = zone?.id,
        categoryOrder = categoryOrder.map { it.id },
        budgetOverride = budget,
        metaQuestions = meta,
        reuseLastList = reuseLastList,
    )

    fun input(
        products: List<Product>,
        context: SessionContext = context(),
        stats: Map<Product, ProductStats> = emptyMap(),
        household: HouseholdProfile = household(),
        signals: MarketSignals = MarketSignals.NONE,
        lastList: Map<Product, Quantity> = emptyMap(),
        seed: Long = 0L,
    ): PlanningInput = PlanningInput(
        products = products,
        categories = CATEGORIES,
        storageZones = ZONES,
        units = UNITS,
        household = household,
        stats = stats.mapKeys { it.key.id },
        context = context,
        signals = signals,
        lastList = lastList.mapKeys { it.key.id },
        now = NOW,
        seed = seed,
    )
}

internal fun QuestionPlan.cards(): List<PlannedQuestion.Card> = questions.filterIsInstance<PlannedQuestion.Card>()

internal fun QuestionPlan.gates(): List<PlannedQuestion.Gate> = questions.filterIsInstance<PlannedQuestion.Gate>()

internal val List<ProposedItem>.slugs: List<String> get() = map { it.product.slug }

internal val PlannedQuestion.label: String
    get() = when (this) {
        is PlannedQuestion.Card -> item.product.slug
        is PlannedQuestion.Gate -> "gate:${category.slug}"
    }
