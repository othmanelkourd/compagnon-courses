import Shared
import SwiftUI

/// The onboarding « Mon foyer »: the first launch (before the home screen) or « Mon foyer » (pushed from the home
/// card). `onExit` is called once when the onboarding must close (`state.exit`): home, or a question session.
struct OnboardingScreen: View {
    let mode: OnboardingMode
    let onExit: (OnboardingExit) -> Void

    @State private var model: OnboardingModel

    init(mode: OnboardingMode, onExit: @escaping (OnboardingExit) -> Void) {
        self.mode = mode
        self.onExit = onExit
        _model = State(initialValue: OnboardingModel(mode: mode))
    }

    var body: some View {
        OnboardingContent(state: model.state, send: model.send)
            .task {
                await model.observe()
            }
            .onChange(of: model.ready?.exit) { _, exit in
                guard let exit else { return }
                onExit(exit)
                model.send(OnboardingEventExitHandled.shared)
            }
            // Pushed from the home screen, the system edge swipe would leave « Mon foyer » from any step: inside a
            // step, the swipe goes back one step instead (`onboardingEdgeSwipe`).
            .onChange(of: blocksSystemSwipe, initial: true) { _, blocked in
                SwipeBack.isBlocked = blocked
            }
            .onDisappear {
                SwipeBack.isBlocked = false
            }
    }

    private var blocksSystemSwipe: Bool {
        guard let step = model.ready?.step else { return false }
        return mode == .edit && step != .overview
    }
}

/// Stateless rendering of an `OnboardingUiState`, so that every step can be previewed (and shown in the Debug demo
/// screens) with a fake state.
struct OnboardingContent: View {
    let state: any OnboardingUiState
    let send: (any OnboardingEvent) -> Void

    var body: some View {
        Group {
            switch onEnum(of: state) {
            case .loading:
                Palette.canvas
            case .ready(let ready):
                ReadyOnboarding(state: ready, send: send)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.canvas)
        .toolbar(.hidden, for: .navigationBar)
    }
}

private struct ReadyOnboarding: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    var body: some View {
        VStack(spacing: 0) {
            if showsTopBar {
                OnboardingTopBar(
                    stepNumber: state.stepNumber?.intValue,
                    stepCount: Int(state.stepCount),
                    canSkip: state.canSkip,
                    closeInsteadOfBack: state.closeInsteadOfBack,
                    backLabel: state.step == .overview ? Text("Retour à l'accueil") : Text("Retour"),
                    enabled: !state.saving,
                    onBack: { send(OnboardingEventBack.shared) },
                    onSkip: { send(OnboardingEventSkip.shared) }
                )
                .padding(.horizontal, Spacing.screen)
                .padding(.top, Spacing.top)
            }
            if state.error != nil {
                OnboardingErrorBanner(onRetry: { send(OnboardingEventRetry.shared) })
                    .padding(.horizontal, Spacing.screen)
                    .padding(.top, 12)
                    .transition(.opacity)
            }
            stepView
                .id(state.step)
                .transition(.asymmetric(insertion: .opacity.combined(with: .offset(x: 24)), removal: .opacity))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .onboardingEdgeSwipe(enabled: edgeSwipeBack) {
            send(OnboardingEventBack.shared)
        }
    }

    private var showsTopBar: Bool {
        state.stepNumber != nil || state.step == .overview
    }

    /// The previous step (or leaving a section) by swiping from the edge; not on the first and last screens of the
    /// first launch, nor on « Mon foyer » (the system swipe leaves it).
    private var edgeSwipeBack: Bool {
        switch state.step {
        case .welcome, .recap, .overview: false
        default: !state.saving
        }
    }

    @ViewBuilder
    private var stepView: some View {
        switch state.step {
        case .welcome: WelcomeStepView(state: state, send: send)
        case .overview: HouseholdOverviewView(state: state, send: send)
        case .household: HouseholdStepView(state: state, send: send)
        case .diet: DietStepView(state: state, send: send)
        case .avoid: AvoidStepView(state: state, send: send)
        case .essentials: EssentialsStepView(state: state, send: send)
        case .recurring: RecurringStepView(state: state, send: send)
        case .rhythm: RhythmStepView(state: state, send: send)
        case .recap: RecapStepView(state: state, send: send)
        }
    }
}
