package com.compagnon.courses.android.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.compagnon.courses.android.home.HomeScreen
import com.compagnon.courses.android.list.ShoppingListScreen
import com.compagnon.courses.android.onboarding.OnboardingScreen
import com.compagnon.courses.android.questions.QuestionnaireScreen
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.feature.onboarding.AppGateEvent
import com.compagnon.courses.feature.onboarding.AppGateUiState
import com.compagnon.courses.feature.onboarding.AppGateViewModel
import com.compagnon.courses.feature.onboarding.HouseholdSummaryUi
import com.compagnon.courses.feature.onboarding.OnboardingExit
import com.compagnon.courses.feature.onboarding.OnboardingMode
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app behind its first-launch gate ([AppGateViewModel], scoped to the activity): a plain background while the
 * household is read, the onboarding « Mon foyer » on the first launch, then the screens of [AppNavDisplay].
 * « Faire le point maintenant » at the end of the onboarding opens the questionnaire on top of the home screen.
 */
@Composable
fun CompagnonApp(modifier: Modifier = Modifier, gateViewModel: AppGateViewModel = koinViewModel()) {
    val gate by gateViewModel.state.collectAsStateWithLifecycle()
    var startSessionOnOpen by rememberSaveable { mutableStateOf(false) }

    when (val current = gate) {
        AppGateUiState.Loading -> Box(modifier = modifier.fillMaxSize().background(CompagnonColors.Background))
        AppGateUiState.NeedsOnboarding -> OnboardingScreen(
            mode = OnboardingMode.FIRST_RUN,
            onExit = { exit ->
                if (exit == OnboardingExit.START_SESSION) startSessionOnOpen = true
                gateViewModel.onEvent(AppGateEvent.OnboardingClosed)
            },
            modifier = modifier,
        )
        is AppGateUiState.Ready -> AppNavDisplay(
            household = current.summary,
            startSession = startSessionOnOpen,
            onSessionStarted = { startSessionOnOpen = false },
            modifier = modifier,
        )
    }
}

/**
 * The screens of the app on a Navigation 3 back stack (saved across configuration changes and process death).
 *
 * Each entry owns its ViewModels ([rememberViewModelStoreNavEntryDecorator]): the question session's ViewModel
 * exists only while the questionnaire is on the stack, and leaving it keeps the session open in the database
 * (the next questionnaire resumes it); « Mon foyer » gets its own onboarding ViewModel each time it is opened.
 */
@Composable
private fun AppNavDisplay(
    household: HouseholdSummaryUi,
    startSession: Boolean,
    onSessionStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(HomeKey)
    // At the root, NavDisplay leaves the system back to the platform (the app goes to the background).
    val goBack: () -> Unit = { AppNavigation.back(backStack) }

    LaunchedEffect(startSession) {
        if (startSession) {
            AppNavigation.startSession(backStack)
            onSessionStarted()
        }
    }

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
                    household = household,
                    onStartSession = { AppNavigation.startSession(backStack) },
                    onOpenList = { AppNavigation.showList(backStack) },
                    onOpenHousehold = { AppNavigation.openHousehold(backStack) },
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
            entry<HouseholdKey> {
                OnboardingScreen(
                    mode = OnboardingMode.EDIT,
                    onExit = { exit ->
                        AppNavigation.leaveHousehold(backStack, startSession = exit == OnboardingExit.START_SESSION)
                    },
                )
            }
        },
    )
}
