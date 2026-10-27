import Observation
import Shared
import SwiftUI

/// SwiftUI-facing wrapper around the shared `OnboardingViewModel`: one per onboarding screen (the first launch, or
/// each visit of « Mon foyer »).
///
/// The Kotlin ViewModel reads the household and downloads a missing catalog as soon as it exists, so it is resolved
/// on the first `observe()` (when the screen appears), not when SwiftUI builds the `@State` value. Deinit clears it.
@MainActor
@Observable
final class OnboardingModel {
    private(set) var state: any OnboardingUiState = OnboardingUiStateLoading.shared

    /// The last step change went forward (the next step slides in from the trailing edge), or back.
    private(set) var forward = true

    /// The current screen, or `nil` while loading.
    var ready: OnboardingUiStateReady? {
        state as? OnboardingUiStateReady
    }

    @ObservationIgnored private let mode: OnboardingMode
    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private var viewModel: OnboardingViewModel?

    init(mode: OnboardingMode) {
        self.mode = mode
    }

    /// Mirrors the ViewModel state until the calling task is cancelled.
    func observe() async {
        let viewModel = resolveViewModel()
        for await newState in viewModel.state {
            let newStep = (newState as? OnboardingUiStateReady)?.step
            let stepChanged = newStep != ready?.step
            // A new step slides in (its transition brings its own timing); changes within a step (a stepper, a chip)
            // are not animated here.
            withAnimation(stepChanged ? Motion.standard() : nil) {
                if stepChanged, let newStep, let oldStep = ready?.step {
                    forward = newStep.order >= oldStep.order
                }
                state = newState
            }
        }
    }

    func send(_ event: any OnboardingEvent) {
        viewModel?.onEvent(event: event)
    }

    private func resolveViewModel() -> OnboardingViewModel {
        if let viewModel {
            return viewModel
        }
        let created = OnboardingViewModelProvider().create(mode: mode, owner: owner)
        viewModel = created
        return created
    }

    isolated deinit {
        owner.clear()
    }
}

extension OnboardingStep {
    /// Position in the onboarding: « Mon foyer » comes before its sections, the recap last.
    var order: Int {
        switch self {
        case .welcome: 0
        case .overview: 1
        case .household: 2
        case .diet: 3
        case .avoid: 4
        case .essentials: 5
        case .recurring: 6
        case .rhythm: 7
        case .recap: 8
        }
    }
}
