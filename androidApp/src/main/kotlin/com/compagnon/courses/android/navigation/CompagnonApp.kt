package com.compagnon.courses.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.compagnon.courses.android.home.HomeScreen
import com.compagnon.courses.android.list.ShoppingListScreen
import com.compagnon.courses.android.questions.QuestionnaireScreen

/**
 * The three screens of V0 on a Navigation 3 back stack (saved across configuration changes and process death).
 *
 * Each entry owns its ViewModels ([rememberViewModelStoreNavEntryDecorator]): the question session's ViewModel
 * exists only while the questionnaire is on the stack, and leaving it keeps the session open in the database
 * (the next questionnaire resumes it).
 */
@Composable
fun CompagnonApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    // At the root, NavDisplay leaves the system back to the platform (the app goes to the background).
    val goBack: () -> Unit = { AppNavigation.back(backStack) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = goBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeScreen(
                    onStartSession = { AppNavigation.startSession(backStack) },
                    onOpenList = { AppNavigation.showList(backStack) },
                )
            }
            entry<QuestionnaireKey> {
                QuestionnaireScreen(
                    onClose = goBack,
                    onOpenList = { AppNavigation.showList(backStack) },
                )
            }
            entry<ShoppingListKey> {
                ShoppingListScreen(
                    onBack = goBack,
                    onStartSession = { AppNavigation.startSession(backStack) },
                )
            }
        },
    )
}
