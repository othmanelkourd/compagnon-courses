package com.compagnon.courses.core.data

import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Diet
import com.compagnon.courses.core.model.DietaryTags
import com.compagnon.courses.core.model.TagId
import com.compagnon.courses.engine.PlannedQuestion
import com.compagnon.courses.engine.QuestionPlan
import com.compagnon.courses.engine.QuestionPlanner
import com.compagnon.courses.engine.ScoredProduct
import com.compagnon.courses.engine.SessionMode
import com.compagnon.courses.engine.Tier
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The budget allocation on the real seed (312 products, FR), cold start, default household
 * (2 adults, no history): `docs/question-engine.md` §6.3 steps 4 and 5. Cards come before gates, so a
 * quick session asks about products and not only about aisles.
 */
class SeedPlanningTest {
    private val planner = QuestionPlanner()

    @Test
    fun aColdQuickSessionOnTheSeedAsksProductCards() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 42))
        val input = setup.input

        val plan = planner.plan(input)
        println("seed quick cold start: ${plan.describe()}")

        val budget = assertNotNull(plan.budget)
        assertEquals(12, budget)
        assertTrue(plan.cost <= budget, "cost ${plan.cost} > budget $budget")
        assertTrue(plan.meta.size <= 3)
        assertTrue(plan.suggested.isNotEmpty(), "a cold start has staples to suggest")
        assertTrue(plan.suggested.size <= 15, "T1 screen of ${plan.suggested.size} items")
        assertTrue(plan.cards.size >= 4, "only ${plan.cards.size} cards: ${plan.describe()}")
        assertTrue(plan.cards.all { it.item.tier == Tier.T2 || it.item.tier == Tier.T1 })

        val asked = plan.askedProducts.map { it.productId }
        assertEquals(asked.size, asked.toSet().size, "a product is asked twice")
        assertTrue(asked.none { it in plan.deferred.toSet() }, "a product is both asked and deferred")
        val excluded = input.household.excludedTags
        assertTrue(plan.askedProducts.none { item -> item.product.tags.any { it in excluded } })

        // Cards are the likeliest T2 products: no T2 product behind a gate is likelier than a card. (T1 products
        // beyond the screen cap do fold into gates, by design.)
        val lowestCard = plan.cards.minOf { it.item.score }
        plan.gates.flatMap { it.items }.filter { it.tier == Tier.T2 }
            .forEach { assertTrue(it.score <= lowestCard, "${it.product.slug} ${it.score} > $lowestCard") }

        // The same plan with known meta answers frees their slots for more cards.
        val known = planner.plan(input.copy(context = input.context.copy(metaQuestions = emptyList())))
        println("seed quick, meta known: ${known.describe()}")
        assertTrue(known.cards.size > plan.cards.size || known.gates.size > plan.gates.size)
        assertTrue(known.cost <= budget)
    }

    @Test
    fun seedTagsReachTheEngineAndExcludeTheTaggedProducts() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 42))
        val pork = TagId("contains-pork")
        val porkProducts = setup.input.products.filter { pork in it.tags }
        assertTrue(porkProducts.size >= 5, "the seed tags pork products: ${porkProducts.map { it.slug }}")
        assertTrue(setup.input.products.first { it.slug == "jambon-blanc" }.tags.contains(pork))

        val excluding = setup.input.household.copy(excludedTags = setOf(pork, TagId("contains-alcohol")))
        val plan = planner.plan(setup.input.copy(household = excluding))
        val proposed = plan.askedProducts + plan.gates.flatMap { it.items }
        assertTrue(proposed.none { pork in it.product.tags || TagId("contains-alcohol") in it.product.tags })
    }

    @Test
    fun aColdQuickSessionNeverAsksAboutExcludedProducts() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 42))
        val tags = setup.input.products.flatMap { it.tags }.toSet()
        tags.forEach { tag ->
            val household = setup.input.household.copy(excludedTags = setOf(tag))
            val plan = planner.plan(setup.input.copy(household = household))
            assertTrue(plan.askedProducts.none { tag in it.product.tags }, "$tag asked")
            assertTrue(plan.cost <= 12)
            assertTrue(plan.suggested.size <= 15)
        }
    }

    private suspend fun TestData.planFor(setup: HouseholdSetup?, seed: Long = 42): Pair<QuestionPlan, List<ScoredProduct>> {
        if (setup != null) households.saveSetup(setup)
        val input = assertNotNull(questions.planningSetup(SessionMode.QUICK, clock.now, seed)).input
        return planner.plan(input) to planner.score(input)
    }

    private fun QuestionPlan.everyProposed() = askedProducts + gates.flatMap { it.items }

    @Test
    fun aVegetarianFamilyWithABabyIsNeverAskedAboutMeatFishOrPetProducts() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()

        val (plan, scored) = data.planFor(
            HouseholdSetup(adults = 2, children = listOf(AgeBand.BABY, AgeBand.CHILD), diet = Diet.VEGETARIAN),
        )
        println("vegetarian family with a baby: ${plan.describe()}")

        val tags = scored.flatMap { it.product.tags }.toSet()
        assertTrue(DietaryTags.MEAT !in tags && DietaryTags.FISH !in tags, "meat or fish scored")
        assertTrue(DietaryTags.PET !in tags, "a pet product without a pet")
        // Baby products are not editorial candidates: they come from the essentials the onboarding pre-checks.
        val animal = setOf(DietaryTags.MEAT, DietaryTags.FISH)
        assertTrue(plan.everyProposed().none { product -> product.product.tags.any { it in animal } })
    }

    @Test
    fun aVeganLivingAloneIsNeverAskedAboutAnimalBabyOrPetProducts() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()

        val (plan, scored) = data.planFor(HouseholdSetup(adults = 1, diet = Diet.VEGAN))
        println("vegan alone: ${plan.describe()}")

        val forbidden =
            setOf(DietaryTags.MEAT, DietaryTags.FISH, DietaryTags.LACTOSE, DietaryTags.EGGS, DietaryTags.BABY, DietaryTags.PET)
        assertTrue(scored.none { item -> item.product.tags.any { it in forbidden } })
        assertTrue(plan.cards.size >= 4, "only ${plan.cards.size} cards: ${plan.describe()}")
    }

    @Test
    fun theHouseholdsEssentialsAndCadencesShapeTheFirstSession() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()
        val baguette = seedProductId("baguette")
        val couches = seedProductId("couches")
        val painDeMie = seedProductId("pain-de-mie")

        val (plan, scored) = data.planFor(
            HouseholdSetup(
                adults = 2,
                children = listOf(AgeBand.BABY),
                essentials = setOf(baguette, painDeMie, seedProductId("huile-d-olive")),
                cadences = mapOf(baguette to 7, couches to 14),
            ),
        )
        println("household essentials: ${plan.describe()}")

        // Weekly → the T1 screen; every two weeks → a card; the catalog's other staples are no longer essentials.
        assertTrue(plan.suggested.any { it.productId == baguette }, "the weekly baguette is not on the T1 screen")
        assertTrue(plan.cards.any { it.item.productId == couches }, "the diapers every 2 weeks are not a card")
        val bananas = scored.firstOrNull { it.product.slug == "bananes" }
        assertTrue(bananas == null || bananas.tier != Tier.T1, "a catalog staple stays in T1 after the household chose")
        assertTrue(scored.first { it.product.id == painDeMie }.tier <= Tier.T2, "a chosen essential is at least a card")
    }

    @Test
    fun twoSeedsAskDifferentCardsWithTheSameTiers() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()
        data.households.saveSetup(HouseholdSetup(adults = 2))

        val (first, firstScores) = data.planFor(null, seed = 1)
        val others = (2L..6L).map { data.planFor(null, seed = it) }

        assertTrue(
            others.any { (plan, _) ->
                plan.cards.map {
                    it.item.productId
                }.toSet() != first.cards.map { it.item.productId }.toSet()
            },
        )
        others.forEach { (_, scores) ->
            assertEquals(firstScores.associate { it.product.id to it.tier }, scores.associate { it.product.id to it.tier })
        }
        // Cards spread over the aisles.
        assertTrue(first.cards.groupingBy { it.item.product.categoryId }.eachCount().values.all { it <= 3 })
    }

    @Test
    fun everyDietaryTagExistsInTheSeed() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.installSeed()
        val tags = assertNotNull(data.catalog.catalog()).products.flatMap { it.tags }.toSet()
        assertEquals(emptySet(), DietaryTags.ALL - tags)
    }
}

private val QuestionPlan.cards: List<PlannedQuestion.Card> get() = questions.filterIsInstance<PlannedQuestion.Card>()

private val QuestionPlan.gates: List<PlannedQuestion.Gate> get() = questions.filterIsInstance<PlannedQuestion.Gate>()

private fun QuestionPlan.describe(): String =
    "meta=${meta.size} t1=${suggested.size} cards=${cards.size} gates=${gates.size} " +
        "(gate items ${gates.sumOf { it.items.size }}) deferred=${deferred.size} cost=$cost; " +
        "cards ${cards.map { it.item.product.slug }}, gates ${gates.map { it.category.slug + "/" + it.items.size }}"
