import Observation
import Shared

/// SwiftUI-facing wrapper around the shared `CatalogStatusViewModel`: the catalog published by the server
/// (`state`) and the local, offline catalog with its last sync (`sync`).
///
/// Creating the model has no side effect: the Kotlin ViewModel (which syncs as soon as it exists) is resolved
/// from Koin on the first `observe()`, i.e. when the view appears. SwiftUI may build and discard `@State`
/// initial values several times, so this keeps those throwaway instances from sending requests.
///
/// The ViewModel belongs to `owner`: deinit clears it, which runs `onCleared()` and cancels its `viewModelScope`.
@MainActor
@Observable
final class CatalogStatusModel {
    private(set) var state: any CatalogStatusUiState = CatalogStatusUiStateLoading.shared
    private(set) var sync = CatalogSyncUiState(
        syncing: false,
        localVersion: nil,
        lastSyncedAt: nil,
        productCount: 0,
        categoryCount: 0,
        lastOutcome: nil,
        canCheck: true
    )

    /// A check is running. Without server (`sync.canCheck` false), the server state never loads: never busy.
    var isBusy: Bool {
        sync.syncing || (sync.canCheck && state is CatalogStatusUiStateLoading)
    }

    /// Whether the catalog can be checked again (pull to refresh, tap on the line).
    var canRefresh: Bool {
        sync.canCheck
    }

    @ObservationIgnored private let country: String
    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private var viewModel: CatalogStatusViewModel?

    init(country: String) {
        self.country = country
    }

    /// Mirrors both ViewModel flows until the calling task is cancelled. Run it from `.task { await model.observe() }`.
    func observe() async {
        _ = resolveViewModel()
        // The ViewModel is read back from `self` in each task: Kotlin objects are not Sendable.
        await withDiscardingTaskGroup { group in
            group.addTask { await self.observeState() }
            group.addTask { await self.observeSync() }
        }
    }

    // SKIE bridges each Kotlin StateFlow to an AsyncSequence that starts with the current value.
    private func observeState() async {
        guard let viewModel else { return }
        for await newState in viewModel.state {
            state = newState
        }
    }

    private func observeSync() async {
        guard let viewModel else { return }
        for await newSync in viewModel.sync {
            sync = newSync
        }
    }

    /// Syncs the local catalog and reloads the server status. The ViewModel ignores it while a sync is running.
    func refresh() {
        viewModel?.onEvent(event: CatalogStatusEventRefresh.shared)
    }

    /// `refresh()`, then waits (up to 30 s) for the sync to end: for `.refreshable`.
    func refreshAndWait() async {
        refresh()
        // The sync flag rises on the next state emission; give it a moment before polling for the end.
        try? await Task.sleep(for: .milliseconds(300))
        for _ in 0..<300 where isBusy {
            try? await Task.sleep(for: .milliseconds(100))
        }
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
