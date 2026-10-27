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
            let stepChanged = (newState as? OnboardingUiStateReady)?.step != ready?.step
            // A new step slides in; changes within a step (a stepper, a chip) are not animated here.
            withAnimation(stepChanged ? .snappy(duration: 0.28) : nil) {
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
