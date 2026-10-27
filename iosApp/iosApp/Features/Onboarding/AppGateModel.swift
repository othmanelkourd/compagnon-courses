import Observation
import Shared

/// SwiftUI-facing wrapper around the shared `AppGateViewModel`: decides at launch whether the first-run onboarding
/// comes before the app, and keeps the household summary of the « Mon foyer » card up to date. One for the app's
/// lifetime, resolved on the first `observe()` like the other models.
@MainActor
@Observable
final class AppGateModel {
    private(set) var state: any AppGateUiState = AppGateUiStateLoading.shared

    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private var viewModel: AppGateViewModel?

    /// Mirrors the ViewModel state until the calling task is cancelled.
    func observe() async {
        let viewModel = resolveViewModel()
        for await newState in viewModel.state {
            state = newState
        }
    }

    func send(_ event: any AppGateEvent) {
        viewModel?.onEvent(event: event)
    }

    private func resolveViewModel() -> AppGateViewModel {
        if let viewModel {
            return viewModel
        }
        let created = AppGateViewModelProvider().create(owner: owner)
        viewModel = created
        return created
    }

    isolated deinit {
        owner.clear()
    }
}
