import Observation
import Shared

/// SwiftUI-facing wrapper around the shared `CatalogStatusViewModel`.
///
/// Creating the model has no side effect: the Kotlin ViewModel (which starts loading as soon as it exists) is
/// resolved from Koin on the first `observe()`, i.e. when the view appears. SwiftUI may build and discard
/// `@State` initial values several times, so this keeps those throwaway instances from sending requests.
///
/// The ViewModel belongs to `owner`: deinit clears it, which runs `onCleared()` and cancels its `viewModelScope`.
@MainActor
@Observable
final class CatalogStatusModel {
    private(set) var state: any CatalogStatusUiState = CatalogStatusUiStateLoading.shared

    var isLoading: Bool {
        state is CatalogStatusUiStateLoading
    }

    @ObservationIgnored private let country: String
    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private var viewModel: CatalogStatusViewModel?

    init(country: String) {
        self.country = country
    }

    /// Mirrors the ViewModel state until the calling task is cancelled. Run it from `.task { await model.observe() }`.
    func observe() async {
        let viewModel = resolveViewModel()
        // SKIE bridges the Kotlin StateFlow to an AsyncSequence that starts with the current value.
        for await newState in viewModel.state {
            state = newState
        }
    }

    /// Reloads the catalog status. The ViewModel ignores it while a load is running.
    func refresh() {
        viewModel?.onEvent(event: CatalogStatusEventRefresh.shared)
    }

    private func resolveViewModel() -> CatalogStatusViewModel {
        if let viewModel {
            return viewModel
        }
        let created = CatalogStatusViewModelProvider().create(country: country, owner: owner)
        viewModel = created
        return created
    }

    isolated deinit {
        owner.clear()
    }
}
