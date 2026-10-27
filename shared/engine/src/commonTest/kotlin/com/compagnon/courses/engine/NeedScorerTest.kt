package com.compagnon.courses.engine

import com.compagnon.courses.core.model.AgeBand
import com.compagnon.courses.core.model.EventId
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.engine.Fixtures.NOW
import com.compagnon.courses.engine.Fixtures.boughtDaysAgo
import com.compagnon.courses.engine.Fixtures.context
import com.compagnon.courses.engine.Fixtures.household
import com.compagnon.courses.engine.Fixtures.product
import kotlin.math.exp
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class NeedScorerTest {
    private val scorer = NeedScorer()
    private val milk = product("lait", repurchaseDays = 10)

    private fun score(
        stats: ProductStats?,
        horizonDays: Int = 7,
        signals: MarketSignals = MarketSignals.NONE,
        guests: Int = 0,
        product: Product = milk,
    ): NeedScore {
        val context = context(horizonDays = horizonDays, guests = guests)
        return scorer.score(product, stats, household(), context, signals, NOW)
    }

    @Test
    fun dueProductsFollowTheLogisticCurve() {
        // Î = 10 days (2-person household = reference), t = 3, H = 7 → r = 1 → p = 0.5.
        val due = score(boughtDaysAgo(3))
        assertEquals(0.5, due.probability, 1e-9)
        assertEquals(10.0, due.intervalDays!!, 1e-9)
        assertEquals(listOf<ReasonCode>(ReasonCode.Due(daysSinceLast = 3, intervalDays = 10, null)), due.reasons)

        // t = 8 → r = 1.5 → p = 1 / (1 + e^-2).
        assertEquals(1.0 / (1.0 + exp(-2.0)), score(boughtDaysAgo(8)).probability, 1e-9)
        // A longer horizon raises p.
        assertTrue(score(boughtDaysAgo(3), horizonDays = 14).probability > 0.5)
    }

    @Test
    fun householdHistoryIsShrunkTowardThePrior() {
        // 5 purchases = 4 observed intervals: Î = (4 × 6 + 2 × 10) / (4 + 2).
        val stats = boughtDaysAgo(1, purchases = 5).copy(ewmaIntervalDays = 6.0)
        assertEquals(44.0 / 6.0, scorer.estimatedInterval(milk, stats, household())!!, 1e-9)
        // Without a prior, the EWMA is used as is.
        assertEquals(6.0, scorer.estimatedInterval(product("x", repurchaseDays = null), stats, household())!!, 1e-9)
        // A cadence set by the user wins.
        assertEquals(14.0, scorer.estimatedInterval(milk, stats.copy(userCadenceDays = 14), household())!!, 1e-9)
    }

    @Test
    fun aBigLastPurchaseLengthensTheInterval() {
        val stats = boughtDaysAgo(1).copy(ewmaQtyBase = 1000.0, lastQtyBase = 1500.0)
        assertEquals(15.0, scorer.estimatedInterval(milk, stats, household())!!, 1e-9)
        // The correction is clamped to [0.5, 2].
        assertEquals(20.0, scorer.estimatedInterval(milk, stats.copy(lastQtyBase = 9000.0), household())!!, 1e-9)
        assertEquals(5.0, scorer.estimatedInterval(milk, stats.copy(lastQtyBase = 10.0), household())!!, 1e-9)
    }

    @Test
    fun biggerHouseholdsRunOutSooner() {
        assertEquals(1.0, scorer.householdSizeFactor(household(adults = 2)), 1e-9)
        assertEquals(0.5.pow(0.7), scorer.householdSizeFactor(household(adults = 4)), 1e-9)
        assertEquals(2.0.pow(0.7), scorer.householdSizeFactor(household(adults = 1)), 1e-9)
        // P_eq = 2 + 0.6 × 2 children.
        val family = household(adults = 2).copy(children = listOf(AgeBand.CHILD, AgeBand.TEEN))
        assertEquals((2.0 / 3.2).pow(0.7), scorer.householdSizeFactor(family), 1e-9)
        // Bounded to [0.3, 3].
        assertEquals(0.3, scorer.householdSizeFactor(household(adults = 20)), 1e-9)
    }

    @Test
    fun coldStartUsesThePenetration() {
        val staple = score(null, product = product("pain", penetration = 0.85))
        assertEquals(0.85, staple.probability, 1e-6)
        assertEquals(listOf(ReasonCode.Staple, ReasonCode.Prior(householdSize = 2)), staple.reasons)
        val chips = product("chips", penetration = 0.85, isStaple = false)
        assertEquals(0.425, score(null, product = chips).probability, 1e-6)
        // Unknown penetration: the configured default.
        assertEquals(0.2, score(null, product = product("x", penetration = null)).probability, 1e-6)
    }

    @Test
    fun modifiersMultiplyTheOdds() {
        val even = boughtDaysAgo(3) // p = 0.5, odds = 1
        val season = MarketSignals(seasonality = mapOf(milk.id to SeasonLevel.HIGH))
        val high = score(even, signals = season)
        assertEquals(1.3 / 2.3, high.probability, 1e-9)
        assertTrue(ReasonCode.Seasonal in high.reasons)
        val lowSeason = MarketSignals(seasonality = mapOf(milk.id to SeasonLevel.LOW))
        assertEquals(0.7 / 1.7, score(even, signals = lowSeason).probability, 1e-9)

        val event = MarketSignals(events = mapOf(milk.id to EventBoost(EventId("ramadan"), 2.0)))
        val boosted = score(even, signals = event)
        assertEquals(2.0 / 3.0, boosted.probability, 1e-9)
        assertTrue(ReasonCode.Event(EventId("ramadan")) in boosted.reasons)

        val reception = MarketSignals(receptionCategories = setOf(milk.categoryId))
        assertEquals(0.5, score(even, signals = reception).probability, 1e-9)
        assertEquals(1.2 / 2.2, score(even, signals = reception, guests = 4).probability, 1e-9)

        val twoNo = 0.6.pow(2)
        assertEquals(twoNo / (1 + twoNo), score(even.copy(consecutiveNo = 2.0)).probability, 1e-9)
        val gateNo = 0.6.pow(0.5)
        assertEquals(gateNo / (1 + gateNo), score(even.copy(consecutiveNo = 0.5)).probability, 1e-9)
    }

    @Test
    fun littleLeftFloorsTheScore() {
        val stats = boughtDaysAgo(0).copy(watch = true, consecutiveNo = 5.0, usualQty = Quantity(2000, Fixtures.LITER))
        val watched = score(stats)
        assertEquals(0.5, watched.probability, 1e-9)
        assertEquals(ReasonCode.LittleLeft, watched.reasons.first())
        assertEquals(Quantity(2000, Fixtures.LITER), (watched.reasons[1] as ReasonCode.Due).usualQuantity)
    }

    @Test
    fun purchasesInTheFutureCountAsToday() {
        val clockSkew = ProductStats(nPurchases = 1, lastAt = NOW + 2.days)
        assertEquals(score(boughtDaysAgo(0)).probability, score(clockSkew).probability, 1e-9)
    }

    @Test
    fun theHouseholdsEssentialsReplaceTheCatalogs() {
        val chosen = household().copy(customEssentials = true)
        val catalogStaple = product("pain", penetration = 0.6, isStaple = true)
        val notChosen = scorer.score(catalogStaple, null, chosen, context(), MarketSignals.NONE, NOW)
        // No longer an essential: the non-staple cold-start weight applies, without the Staple reason.
        assertEquals(0.3, notChosen.probability, 1e-9)
        assertTrue(ReasonCode.Staple !in notChosen.reasons)

        val other = product("riz", penetration = 0.6, isStaple = false)
        val essential = ProductStats(essential = true)
        assertTrue(scorer.isStaple(other, essential, chosen))
        assertTrue(!scorer.isStaple(other, essential, household()), "ignored until the household chose its essentials")
    }

    @Test
    fun aDeclaredProductStartsFromItsPhaseInTheCycle() {
        val baguette = product("baguette", penetration = 0.2, isStaple = false, repurchaseDays = 30)
        fun declared(days: Int, horizon: Int = 7) =
            score(ProductStats(userCadenceDays = days), horizonDays = horizon, product = baguette)

        // H / Î, capped at 0.9: weekly → T1, every two weeks → card, monthly → behind its gate.
        assertEquals(0.9, declared(7).probability, 1e-9)
        assertEquals(0.5, declared(14).probability, 1e-9)
        assertEquals(7.0 / 30.0, declared(30).probability, 1e-9)
        assertEquals(listOf<ReasonCode>(ReasonCode.Cadence(14)), declared(14).reasons)
        // Once bought, the logistic curve takes over (Due).
        val bought = score(boughtDaysAgo(3).copy(userCadenceDays = 14), product = baguette)
        assertTrue(bought.reasons.first() is ReasonCode.Due)
    }

    @Test
    fun aChosenEssentialNeverBoughtHasAFloor() {
        val chosen = household().copy(customEssentials = true)
        val rice = product("riz", penetration = 0.1, isStaple = false, repurchaseDays = 60)
        val score = scorer.score(rice, ProductStats(essential = true), chosen, context(), MarketSignals.NONE, NOW)
        // H / Î = 7 / 60 < 0.35: the floor keeps it on a card.
        assertEquals(0.35, score.probability, 1e-9)
        assertEquals(listOf<ReasonCode>(ReasonCode.Staple), score.reasons)
        // Without any interval, an essential starts at the floor.
        val unknown = product("x", penetration = 0.1, isStaple = false, repurchaseDays = null)
        assertEquals(0.35, scorer.score(unknown, ProductStats(essential = true), chosen, context(), MarketSignals.NONE, NOW).probability, 1e-9)
    }
}
