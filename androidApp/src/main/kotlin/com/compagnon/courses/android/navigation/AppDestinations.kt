package com.compagnon.courses.android.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** « Accueil »: catalog status, « Faire le point », « Ma liste ». The root of the back stack. */
@Serializable
data object HomeKey : NavKey

/** « Questionnaire »: one question session. Its ViewModel lives as long as this entry is on the back stack. */
@Serializable
data object QuestionnaireKey : NavKey

/** « Liste »: the active shopping list. */
@Serializable
data object ShoppingListKey : NavKey

/** « Mon foyer »: the household, edited section by section. Its onboarding ViewModel lives with this entry. */
@Serializable
data object HouseholdKey : NavKey

/** « À propos »: version, catalog, data, links. Opened from the home screen. */
@Serializable
data object AboutKey : NavKey

/** « Licences open source », opened from « À propos ». */
@Serializable
data object LicensesKey : NavKey

/**
 * Back stack moves of the app, kept free of Compose so they are unit tested. The stack always keeps [HomeKey]
 * at its root.
 */
internal object AppNavigation {

    /** Opens the questionnaire, unless it is already on top. */
    fun startSession(backStack: MutableList<NavKey>) {
        if (backStack.lastOrNull() != QuestionnaireKey) backStack += QuestionnaireKey
    }

    /** Opens the list. From the questionnaire, the list replaces it (the session is over). */
    fun showList(backStack: MutableList<NavKey>) {
        while (backStack.size > 1 && backStack.last() == QuestionnaireKey) backStack.removeAt(backStack.lastIndex)
        if (backStack.lastOrNull() != ShoppingListKey) backStack += ShoppingListKey
    }

    /** Opens « Mon foyer », unless it is already on top. */
    fun openHousehold(backStack: MutableList<NavKey>) {
        if (backStack.lastOrNull() != HouseholdKey) backStack += HouseholdKey
    }

    /**
     * Leaves « Mon foyer » (closed, or « Aller à l'accueil »); with [startSession] (« Faire le point maintenant »),
     * the questionnaire replaces it.
     */
    fun leaveHousehold(backStack: MutableList<NavKey>, startSession: Boolean) {
        if (backStack.size > 1 && backStack.last() == HouseholdKey) backStack.removeAt(backStack.lastIndex)
        if (startSession) startSession(backStack)
    }

    /** Opens « À propos », unless it is already on top. */
    fun openAbout(backStack: MutableList<NavKey>) {
        if (backStack.lastOrNull() != AboutKey) backStack += AboutKey
    }

    /** Opens the licenses, unless they are already on top. */
    fun openLicenses(backStack: MutableList<NavKey>) {
        if (backStack.lastOrNull() != LicensesKey) backStack += LicensesKey
    }

    /** Goes back one screen; the root stays. Returns false when there was nothing to pop. */
    fun back(backStack: MutableList<NavKey>): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
