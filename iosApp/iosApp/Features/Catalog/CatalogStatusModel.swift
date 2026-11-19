import Observation
import Shared

/// SwiftUI-facing wrapper around the shared `CatalogStatusViewModel`: the catalog held by the device.
///
/// Creating the model has no side effect: the Kotlin ViewModel is resolved from Koin on the first `observe()`, i.e.
/// when the view appears. SwiftUI may build and discard `@State` initial values several times.
///
/// The ViewModel belongs to `owner`: deinit clears it, which runs `onCleared()` and cancels its `viewModelScope`.
@MainActor
@Observable
final class CatalogStatusModel {
    private(set) var state = CatalogStatusUiState(ready: false, version: nil, productCount: 0, categoryCount: 0)

    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private var viewModel: CatalogStatusViewModel?

    /// Mirrors the ViewModel state until the calling task is cancelled. Run it from `.task { await model.observe() }`.
    func observe() async {
        // SKIE bridges the Kotlin StateFlow to an AsyncSequence that starts with the current value.
        for await newState in resolveViewModel().state {
            state = newState
        }
    }

    private func resolveViewModel() -> CatalogStatusViewModel {
        if let viewModel {
            return viewModel
        }
        let created = CatalogStatusViewModelProvider().create(owner: owner)
        viewModel = created
        return created
    }

    isolated deinit {
        owner.clear()
    }
}
