package com.compagnon.courses.android.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {

    @Test
    fun startSessionOpensTheQuestionnaireOnce() {
        val stack = mutableListOf<NavKey>(HomeKey)

        AppNavigation.startSession(stack)
        AppNavigation.startSession(stack)

        assertEquals(listOf(HomeKey, QuestionnaireKey), stack)
    }

    @Test
    fun showListFromHomePushesTheList() {
        val stack = mutableListOf<NavKey>(HomeKey)

        AppNavigation.showList(stack)

        assertEquals(listOf(HomeKey, ShoppingListKey), stack)
    }

    @Test
    fun showListAfterASessionReplacesTheQuestionnaire() {
        val stack = mutableListOf<NavKey>(HomeKey, QuestionnaireKey)

        AppNavigation.showList(stack)

        assertEquals(listOf(HomeKey, ShoppingListKey), stack)
    }

    @Test
    fun aSessionStartedFromTheListReturnsToThatList() {
        val stack = mutableListOf<NavKey>(HomeKey, ShoppingListKey)
        AppNavigation.startSession(stack)

        AppNavigation.showList(stack)

        assertEquals(listOf(HomeKey, ShoppingListKey), stack)
    }

    @Test
    fun backPopsButKeepsTheRoot() {
        val stack = mutableListOf<NavKey>(HomeKey, ShoppingListKey)

        assertTrue(AppNavigation.back(stack))
        assertFalse(AppNavigation.back(stack))
        assertEquals(listOf(HomeKey), stack)
    }

    @Test
    fun openHouseholdPushesItOnce() {
        val stack = mutableListOf<NavKey>(HomeKey)

        AppNavigation.openHousehold(stack)
        AppNavigation.openHousehold(stack)

        assertEquals(listOf(HomeKey, HouseholdKey), stack)
    }

    @Test
    fun leavingTheHouseholdGoesBackHome() {
        val stack = mutableListOf<NavKey>(HomeKey, HouseholdKey)

        AppNavigation.leaveHousehold(stack, startSession = false)

        assertEquals(listOf(HomeKey), stack)
    }

    @Test
    fun takingStockFromTheHouseholdReplacesItWithTheQuestionnaire() {
        val stack = mutableListOf<NavKey>(HomeKey, HouseholdKey)

        AppNavigation.leaveHousehold(stack, startSession = true)

        assertEquals(listOf(HomeKey, QuestionnaireKey), stack)
    }
}
