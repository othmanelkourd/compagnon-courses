package com.compagnon.courses.core.data

import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.TagId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

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
}
