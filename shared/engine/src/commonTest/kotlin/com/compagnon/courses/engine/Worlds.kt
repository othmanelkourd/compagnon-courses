package com.compagnon.courses.engine

import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Availability
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.CycleClass
import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.EventId
import com.compagnon.courses.core.model.LocalizedName
import com.compagnon.courses.core.model.PackageFormat
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.ProductMarket
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.TagId
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.core.model.UnitPolicy
import com.compagnon.courses.engine.Fixtures.BOTTLE
import com.compagnon.courses.engine.Fixtures.BOX
import com.compagnon.courses.engine.Fixtures.CATEGORIES
import com.compagnon.courses.engine.Fixtures.DOZEN
import com.compagnon.courses.engine.Fixtures.GRAM
import com.compagnon.courses.engine.Fixtures.KILOGRAM
import com.compagnon.courses.engine.Fixtures.LITER
import com.compagnon.courses.engine.Fixtures.MILLILITER
import com.compagnon.courses.engine.Fixtures.NOW
import com.compagnon.courses.engine.Fixtures.PIECE
import com.compagnon.courses.engine.Fixtures.UNITS
import com.compagnon.courses.engine.Fixtures.ZONES
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** A random but valid planning world: config, catalog, household, statistics and session context. */
internal data class World(val config: PlannerConfig, val input: PlanningInput) {
    val budget: Int? get() = config.budgetFor(input.context)

    override fun toString(): String = "World(config=$config, input=$input)"
}

internal object Worlds {
    val TAGS: List<TagId> = listOf(TagId("pork"), TagId("alcohol"), TagId("gluten"), TagId("lactose"))
    private val UNIT_CODES: List<UnitCode> = listOf(GRAM, KILOGRAM, MILLILITER, LITER, PIECE, DOZEN, BOTTLE, BOX)
    private val STEPS: List<Long> = listOf(1L, 5L, 50L, 100L, 250L, 500L, 1000L, 2000L)

    /** A valid unit policy, with bounds that are not necessarily aligned on the step. */
    fun policy(unit: UnitCode, isDefault: Boolean) = arbitrary {
        val step = Arb.element(STEPS).bind()
        val lowSteps = Arb.long(1L..5L).bind()
        val min = if (Arb.boolean().bind()) Quantity(lowSteps * step - Arb.long(0L until step).bind(), unit) else null
        val firstSteps = if (min == null) 1L else lowSteps
        val highSteps = firstSteps + Arb.long(0L..20L).bind()
        val max = if (Arb.boolean().bind()) Quantity(highSteps * step + Arb.long(0L until step).bind(), unit) else null
        val presets = Arb.list(Arb.long(firstSteps..(if (max == null) firstSteps + 10 else highSteps)), 0..4).bind()
            .distinct()
            .map { Quantity(it * step, unit) }
        UnitPolicy(unit, isDefault, Quantity(step, unit), presets, min, max)
    }

    private val product = arbitrary {
        val unitCount = Arb.int(0..3).bind()
        val units = UNIT_CODES.shuffled(Random(Arb.long().bind())).take(unitCount)
        val defaultIndex = if (units.isEmpty()) -1 else Arb.int(units.indices).bind()
        val policies = units.mapIndexed { index, unit -> policy(unit, isDefault = index == defaultIndex).bind() }
        val dimension = Arb.element(Dimension.entries).bind()
        val packages = if (BOTTLE in units && Arb.boolean().bind()) {
            val content = Quantity(Arb.long(250L..2000L).bind(), if (dimension == Dimension.MASS) GRAM else LITER)
            listOf(PackageFormat(BOTTLE, content, typical = true))
        } else {
            emptyList()
        }
        val unknownCategory = Arb.int(0..19).bind() == 0
        Product(
            id = ProductId("tmp"),
            slug = "tmp",
            categoryId = if (unknownCategory) CategoryId("cat-unknown") else Arb.element(CATEGORIES).bind().id,
            name = LocalizedName("produit"),
            dimension = dimension,
            storageZone = Arb.element(ZONES).bind().id,
            cycleClass = Arb.element(CycleClass.entries).bind(),
            isStaple = Arb.boolean().bind(),
            market = ProductMarket(
                availability = Arb.element(Availability.entries).bind(),
                typicalRepurchaseDays = Arb.int(1..120).orNull(0.3).bind(),
                householdPenetration = Arb.double(0.0, 1.0, false).orNull(0.2).bind(),
                isQuestionCandidate = Arb.int(0..4).bind() != 0,
                popularityRank = Arb.int(1..100).orNull(0.5).bind(),
            ),
            unitPolicies = policies,
            packages = packages,
            tags = TAGS.filter { Arb.int(0..5).bind() == 0 }.toSet(),
        )
    }

    private fun stats(product: Product) = arbitrary {
        val purchases = Arb.int(0..10).bind()
        val units = product.unitPolicies.map { it.unit } + KILOGRAM
        ProductStats(
            nPurchases = purchases,
            lastAt = if (purchases > 0) NOW - Arb.int(-2..200).bind().days - Arb.int(0..23).bind().hours else null,
            ewmaIntervalDays = Arb.double(0.5, 100.0, false).orNull(0.4).bind(),
            ewmaQtyBase = Arb.double(1.0, 5000.0, false).orNull(0.4).bind(),
            lastQtyBase = Arb.double(1.0, 10000.0, false).orNull(0.4).bind(),
            consecutiveNo = Arb.int(0..6).bind() / 2.0,
            snoozeUntil = if (Arb.int(0..5).bind() == 0) NOW + Arb.int(-30..30).bind().days else null,
            watch = Arb.int(0..7).bind() == 0,
            neverAsk = Arb.int(0..9).bind() == 0,
            userCadenceDays = Arb.int(1..60).orNull(0.8).bind(),
            usualQty = Arb.long(1L..20_000L).orNull(0.5).bind()?.let { Quantity(it, Arb.element(units).bind()) },
        )
    }

    val world = arbitrary {
        val products = Arb.list(product, 0..40).bind().mapIndexed { index, product ->
            product.copy(id = ProductId("p$index"), slug = "p$index")
        }
        val stats = products.filter { Arb.boolean().bind() }.associate { it.id to stats(it).bind() }
        val mode = Arb.element(SessionMode.entries).bind()
        val horizon = Arb.int(1..30).bind()
        val household = Fixtures.household(adults = Arb.int(1..4).bind()).copy(
            children = Arb.list(Arb.element(AgeBand.entries), 0..3).bind(),
            excludedTags = TAGS.filter { Arb.int(0..2).bind() == 0 }.toSet(),
        )
        val context = SessionContext(
            mode = mode,
            horizonDays = horizon,
            usualHorizonDays = Arb.int(1..30).bind(),
            persons = Arb.int(1..8).bind(),
            guests = Arb.int(0..3).bind(),
            zone = if (mode == SessionMode.ZONE) Arb.element(ZONES).bind().id else null,
            categoryOrder = CATEGORIES.map { it.id }
                .shuffled(Random(Arb.long().bind()))
                .take(Arb.int(0..CATEGORIES.size).bind()),
            budgetOverride = Arb.int(0..20).orNull(0.5).bind(),
            metaQuestions = MetaKey.entries.filter { Arb.boolean().bind() },
            reuseLastList = Arb.boolean().bind(),
        )
        val signals = MarketSignals(
            seasonality = products.filter { Arb.int(0..4).bind() == 0 }
                .associate { it.id to Arb.element(SeasonLevel.entries).bind() },
            events = products.filter { Arb.int(0..9).bind() == 0 }
                .associate { it.id to EventBoost(EventId("event"), Arb.double(0.5, 3.0, false).bind()) },
            receptionCategories = CATEGORIES.filter { Arb.boolean().bind() }.map { it.id }.toSet(),
        )
        val lastList = products.filter { Arb.int(0..3).bind() == 0 }.associate { product ->
            val unit = (product.unitPolicies.map { it.unit } + LITER).let { Arb.element(it).bind() }
            product.id to Quantity(Arb.long(1L..10_000L).bind(), unit)
        }
        val config = PlannerConfig(
            defaultBudget = Arb.int(0..20).bind(),
            quickBudget = Arb.int(0..20).bind(),
            radarSlots = Arb.int(0..3).bind(),
            explorationSlots = Arb.int(0..2).bind(),
            maxSuggested = Arb.int(0..20).bind(),
            gateSlots = Arb.int(0..3).bind(),
        )
        World(
            config = config,
            input = PlanningInput(
                products = products,
                categories = CATEGORIES,
                storageZones = ZONES,
                units = UNITS,
                household = household,
                stats = stats,
                context = context,
                signals = signals,
                lastList = lastList,
                now = NOW,
                seed = Arb.long().bind(),
            ),
        )
    }

    /** The same input with every collection in another order. */
    fun shuffled(input: PlanningInput, random: Random): PlanningInput = input.copy(
        products = input.products.shuffled(random),
        categories = input.categories.shuffled(random),
        storageZones = input.storageZones.shuffled(random),
        stats = input.stats.entries.shuffled(random).associate { it.toPair() },
        lastList = input.lastList.entries.shuffled(random).associate { it.toPair() },
        signals = input.signals.copy(
            seasonality = input.signals.seasonality.entries.shuffled(random).associate { it.toPair() },
            receptionCategories = input.signals.receptionCategories.shuffled(random).toSet(),
        ),
    )
}
