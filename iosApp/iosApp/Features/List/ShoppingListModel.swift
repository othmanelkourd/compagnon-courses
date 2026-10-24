import Observation
import Shared

/// SwiftUI-facing wrapper around the shared `ShoppingListViewModel` (the active list, offline).
///
/// Like `CatalogStatusModel`, the Kotlin ViewModel is resolved on the first `observe()` and cleared on deinit.
@MainActor
@Observable
final class ShoppingListModel {
    private(set) var state: any ShoppingListUiState = ShoppingListUiStateLoading.shared

    /// The loaded state, or `nil` while loading or after a storage error.
    var loaded: ShoppingListUiStateLoaded? {
        state as? ShoppingListUiStateLoaded
    }

    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private var viewModel: ShoppingListViewModel?

    /// Mirrors the ViewModel state until the calling task is cancelled.
    func observe() async {
        let viewModel = resolveViewModel()
        for await newState in viewModel.state {
            state = newState
        }
    }

    func send(_ event: any ShoppingListEvent) {
        viewModel?.onEvent(event: event)
    }

    private func resolveViewModel() -> ShoppingListViewModel {
        if let viewModel {
            return viewModel
        }
        let created = ShoppingListViewModelProvider().create(owner: owner)
        viewModel = created
        return created
    }

    isolated deinit {
        owner.clear()
    }
}
