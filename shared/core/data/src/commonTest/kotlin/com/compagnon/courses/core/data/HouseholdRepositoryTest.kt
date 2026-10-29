package com.compagnon.courses.core.data

import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.Avoidance
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.Diet
import com.compagnon.courses.core.model.PetKind
import com.compagnon.courses.core.model.TagId
import com.compagnon.courses.engine.SessionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class HouseholdRepositoryTest {

    @Test
    fun defaultsToTwoAdultsUntilSaved() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        val household = data.households.household()

        assertEquals(2, household.profile.adults)
        assertEquals(2, household.profile.size)
        assertEquals("FR", household.profile.country.value)
        assertEquals(7, household.usualHorizonDays)
        assertEquals(Household.default("FR"), data.households.observe().first())
    }

    @Test
    fun updateRoundTrips() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val household = Household.default("FR").let {
            it.copy(
                profile = it.profile.copy(adults = 1, children = listOf(AgeBand.BABY, AgeBand.TEEN), excludedTags = setOf(TagId("contains-pork"))),
                usualHorizonDays = 14,
                storeCategoryOrder = listOf(CategoryId("b"), CategoryId("a")),
            )
        }

        data.households.update(household)

        assertEquals(household, data.households.household())
        assertEquals(household, data.households.observe().first())
    }

    @Test
    fun catalogScopeForTheDeviceLanguage() {
        assertEquals(CatalogScope("FR", "fr-FR"), CatalogScope.forDevice("fr-FR"))
        assertEquals(CatalogScope("FR", "fr-FR"), CatalogScope.forDevice("fr_CA"))
        assertEquals(CatalogScope("BE", "fr-BE"), CatalogScope.forDevice("fr", "BE"))
        assertEquals(CatalogScope("US", "fr"), CatalogScope.forDevice("fr", "US"))
        assertEquals(CatalogScope("FR", "en"), CatalogScope.forDevice("en-GB"))
        assertEquals("fr", CatalogScope.DEFAULT.language)
    }

    @Test
    fun theOnboardingIsSavedOnceAndPreFillsMonFoyer() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        assertEquals(false, data.households.observeOnboarded().first())
        val setup = HouseholdSetup(
            adults = 2,
            children = listOf(AgeBand.BABY, AgeBand.CHILD),
            pets = setOf(PetKind.CAT),
            diet = Diet.HALAL,
            avoid = setOf(Avoidance.LACTOSE),
            essentials = setOf(seedProductId("baguette"), seedProductId("bananes")),
            cadences = mapOf(seedProductId("baguette") to 7, seedProductId("couches") to 14),
            usualHorizonDays = 14,
        )

        data.households.saveSetup(setup)

        assertEquals(true, data.households.observeOnboarded().first())
        assertEquals(setup, data.households.setup())
        val household = data.households.household()
        assertEquals(data.clock.now, household.onboardedAt)
        assertEquals(14, household.usualHorizonDays)
        assertEquals(4, household.profile.size)
        assertTrue(household.profile.customEssentials)
        assertEquals(
            setOf("contains-pork", "contains-alcohol", "contains-lactose"),
            household.profile.effectiveExcludedTags.map { it.value }.toSet(),
        )
        assertEquals(setup.essentials, data.households.observeEssentials().first())
    }

    @Test
    fun editingKeepsTheFirstOnboardingDateAndReplacesEssentialsAndCadences() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val first = HouseholdSetup(
            adults = 1,
            essentials = setOf(seedProductId("baguette")),
            cadences = mapOf(seedProductId("baguette") to 7),
        )
        data.households.saveSetup(first)
        val onboardedAt = data.clock.now
        data.clock.advance(3.days)

        data.households.saveSetup(first.copy(adults = 3, essentials = setOf(seedProductId("bananes")), cadences = emptyMap()))

        assertEquals(onboardedAt, data.households.household().onboardedAt)
        val setup = data.households.setup()
        assertEquals(3, setup.adults)
        assertEquals(setOf(seedProductId("bananes")), setup.essentials)
        assertEquals(emptyMap(), setup.cadences)

        // Skipped steps (null) keep what was there.
        data.households.saveSetup(setup.copy(essentials = null, cadences = null, diet = Diet.VEGAN))
        assertEquals(setOf(seedProductId("bananes")), data.households.setup().essentials)
        assertEquals(Diet.VEGAN, data.households.setup().diet)
    }

    @Test
    fun laterMarksTheOnboardingDoneWithTheDefaults() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))

        data.households.skipSetup()

        assertEquals(true, data.households.observeOnboarded().first())
        val household = data.households.household()
        assertEquals(Household.default("FR").profile, household.profile)
        assertEquals(null, data.households.setup().essentials)
    }

    @Test
    fun savingTheHouseholdClosesTheOpenSession() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 1))
        data.questions.startSession(setup, data.lists.activeListId())
        assertNotNull(data.questions.openSession())

        data.households.saveSetup(HouseholdSetup(adults = 1, diet = Diet.VEGETARIAN))

        assertNull(data.questions.openSession())
    }

    @Test
    fun anUnknownStoredValueOnlyResetsItsOwnField() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.households.saveSetup(HouseholdSetup(adults = 3, diet = Diet.VEGAN, pets = setOf(PetKind.DOG)))
        data.database.householdQueries.selectProfile().executeAsOne().let { row ->
            data.database.householdQueries.upsertProfile(
                row.adults, row.children, row.country, row.measurement_system, row.usual_horizon_days,
                row.store_category_order, row.excluded_tags, row.updated_at, "[\"DRAGON\",\"DOG\"]", "PALEO", row.avoid,
                row.essentials_chosen, row.onboarded_at,
            )
        }

        val profile = data.households.household().profile
        assertEquals(3, profile.adults)
        assertEquals(Diet.OMNIVORE, profile.diet)
        assertEquals(listOf(PetKind.DOG), profile.pets.map { it.kind })
    }

    @Test
    fun anEssentialSurvivesTheStatisticsOfAnAnswer() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        val baguette = seedProductId("baguette")
        data.households.saveSetup(HouseholdSetup(adults = 2, essentials = setOf(baguette), cadences = mapOf(baguette to 2)))
        val stats = StatsStore(data.database, data.ids)

        // StatsStore.put rewrites the whole preference row (INSERT OR REPLACE).
        stats.put(baguette, assertNotNull(stats.get(baguette)).copy(watch = true, nPurchases = 1, lastAt = data.clock.now))

        val after = assertNotNull(stats.get(baguette))
        assertTrue(after.essential)
        assertEquals(2, after.userCadenceDays)
        assertEquals(setOf(baguette), data.households.setup().essentials)
    }
}
