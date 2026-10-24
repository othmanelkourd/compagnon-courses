package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Dimension
import com.compagnon.courses.core.model.PackageFormat
import com.compagnon.courses.core.model.ProductStats
import com.compagnon.courses.core.model.PurchaseSource
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.engine.Fixtures.BOTTLE
import com.compagnon.courses.engine.Fixtures.KILOGRAM
import com.compagnon.courses.engine.Fixtures.LITER
import com.compagnon.courses.engine.Fixtures.NOW
import com.compagnon.courses.engine.Fixtures.UNITS
import com.compagnon.courses.engine.Fixtures.household
import com.compagnon.courses.engine.Fixtures.policy
import com.compagnon.courses.engine.Fixtures.product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class PredictorTest {
    private val predictor = Predictor()
    private val milk = product(
        "lait",
        policies = listOf(policy(BOTTLE), policy(LITER, isDefault = false)),
        packages = listOf(PackageFormat(BOTTLE, Quantity.of(1, LITER), typical = true)),
    )
    private val apples = product("pommes", dimension = Dimension.MASS, policies = listOf(policy(KILOGRAM, "0.5", listOf("1"))))

    @Test
    fun firstPurchaseStartsTheHistoryInTheBaseUnit() {
        val stats = predictor.onPurchase(null, milk, Quantity.of(2, BOTTLE), NOW, UNITS)

        assertEquals(1, stats.nPurchases)
        assertEquals(NOW, stats.lastAt)
        assertNull(stats.ewmaIntervalDays)
        assertEquals(2000.0, stats.ewmaQtyBase)
        assertEquals(2000.0, stats.lastQtyBase)
    }

    @Test
    fun nextPurchasesSmoothTheIntervalAndTheQuantity() {
        val first = predictor.onPurchase(null, apples, Quantity.of(1, KILOGRAM), NOW - 10.days, UNITS)
        val second = predictor.onPurchase(first, apples, Quantity.of(2, KILOGRAM), NOW, UNITS)
        val third = predictor.onPurchase(second, apples, Quantity.of(1, KILOGRAM), NOW + 4.days, UNITS)

        assertEquals(2, second.nPurchases)
        assertEquals(10.0, second.ewmaIntervalDays!!, 1e-9)
        assertEquals(0.3 * 2000.0 + 0.7 * 1000.0, second.ewmaQtyBase!!, 1e-9)
        assertEquals(3, third.nPurchases)
        assertEquals(0.3 * 4.0 + 0.7 * 10.0, third.ewmaIntervalDays!!, 1e-9)
        assertEquals(1000.0, third.lastQtyBase)
    }

    @Test
    fun implicitPurchasesWeighHalf() {
        val first = predictor.onPurchase(null, apples, Quantity.of(1, KILOGRAM), NOW - 10.days, UNITS)
        val second = predictor.onPurchase(first, apples, Quantity.of(1, KILOGRAM), NOW, UNITS, PurchaseSource.IMPLICIT)
        val third = predictor.onPurchase(second, apples, Quantity.of(1, KILOGRAM), NOW + 20.days, UNITS, PurchaseSource.IMPLICIT)

        assertEquals(0.15 * 20.0 + 0.85 * 10.0, third.ewmaIntervalDays!!, 1e-9)
    }

    @Test
    fun aPurchaseOnTheSameTripAddsItsQuantityWithoutAnInterval() {
        val first = predictor.onPurchase(null, apples, Quantity.of(1, KILOGRAM), NOW, UNITS)
        val again = predictor.onPurchase(first, apples, Quantity.of(1, KILOGRAM), NOW + 2.hours, UNITS)

        assertEquals(1, again.nPurchases)
        assertNull(again.ewmaIntervalDays)
        assertEquals(2000.0, again.lastQtyBase)
    }

    @Test
    fun aPurchaseResetsTheNegativeSignals() {
        val stats = ProductStats(nPurchases = 1, lastAt = NOW - 7.days, consecutiveNo = 2.0, watch = true)

        val updated = predictor.onPurchase(stats, milk, Quantity.of(1, BOTTLE), NOW, UNITS)

        assertEquals(0.0, updated.consecutiveNo)
        assertFalse(updated.watch)
    }

    @Test
    fun noIncrementsTheStreakAndDelaysTheProduct() {
        val weekly = product("yaourt", repurchaseDays = 20)

        val updated = predictor.onAnswer(null, weekly, AnswerKind.NO, NOW, household())

        assertEquals(1.0, updated.consecutiveNo)
        assertEquals(NOW, updated.lastNoAt)
        // Î = 20 days for a household of 2 (the reference size): due again after 10 days.
        assertEquals(NOW + 10.days, updated.nextDueAt)
        val withoutPrior = predictor.onAnswer(null, milk, AnswerKind.NO, NOW, household())
        assertEquals(NOW + 3.days, withoutPrior.nextDueAt)
    }

    @Test
    fun nuancedAnswersSetTheMatchingPreference() {
        val household = household()
        assertTrue(predictor.onAnswer(null, milk, AnswerKind.LITTLE_LEFT, NOW, household).watch)
        assertEquals(NOW + 30.days, predictor.onAnswer(null, milk, AnswerKind.HAVE_ENOUGH, NOW, household, days = 30).snoozeUntil)
        assertTrue(predictor.onAnswer(null, milk, AnswerKind.NEVER, NOW, household).neverAsk)
        val stats = ProductStats(consecutiveNo = 1.0)
        assertEquals(stats, predictor.onAnswer(stats, milk, AnswerKind.NOT_THIS_TIME, NOW, household))
        assertEquals(stats, predictor.onAnswer(stats, milk, AnswerKind.LATER, NOW, household))
    }

    @Test
    fun yesRemembersTheQuantityAndClearsTheStreak() {
        val stats = ProductStats(consecutiveNo = 2.0, watch = true)

        val updated = predictor.onAnswer(stats, milk, AnswerKind.YES, NOW, household(), quantity = Quantity.of(6, BOTTLE))

        assertEquals(Quantity.of(6, BOTTLE), updated.usualQty)
        assertEquals(0.0, updated.consecutiveNo)
        assertFalse(updated.watch)
    }

    @Test
    fun aGateNoCountsHalfANo() {
        assertEquals(0.5, predictor.onGateNo(null).consecutiveNo)
        assertEquals(1.5, predictor.onGateNo(ProductStats(consecutiveNo = 1.0)).consecutiveNo)
    }
}
