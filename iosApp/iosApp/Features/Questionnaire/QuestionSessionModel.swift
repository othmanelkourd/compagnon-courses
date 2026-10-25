import Observation
import Shared
import SwiftUI

/// SwiftUI-facing wrapper around the shared `QuestionSessionViewModel`: one per questionnaire screen.
///
/// The Kotlin ViewModel resumes the unfinished session or plans a new one as soon as it exists, so it is
/// resolved on the first `observe()` (when the screen appears), not when SwiftUI builds the `@State` value.
/// Deinit clears it; the session itself stays open and the next screen resumes it.
@MainActor
@Observable
final class QuestionSessionModel {
    private(set) var state: any QuestionUiState = QuestionUiStateLoading.shared

    /// The last haptic and a counter that changes with each one, for `.sensoryFeedback(trigger:)`.
    private(set) var haptic: (kind: QuestionHaptic, tick: Int)?

    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private var viewModel: QuestionSessionViewModel?

    /// Mirrors the state and the haptics until the calling task is cancelled.
    func observe() async {
        _ = resolveViewModel()
        // The ViewModel is read back from `self` in each task: Kotlin objects are not Sendable.
        await withDiscardingTaskGroup { group in
            group.addTask { await self.observeState() }
            group.addTask { await self.observeHaptics() }
        }
    }

    private func observeState() async {
        guard let viewModel else { return }
        for await newState in viewModel.state {
            // One animation for every step: the outgoing card leaves, the next one comes in.
            withAnimation(.snappy(duration: 0.28)) {
                state = newState
            }
        }
    }

    private func observeHaptics() async {
        guard let viewModel else { return }
        for await kind in viewModel.haptics {
            haptic = (kind, (haptic?.tick ?? 0) + 1)
        }
    }

    func send(_ event: any QuestionEvent) {
        viewModel?.onEvent(event: event)
    }

    private func resolveViewModel() -> QuestionSessionViewModel {
        if let viewModel {
            return viewModel
        }
        let created = QuestionSessionViewModelProvider().create(owner: owner)
        viewModel = created
        return created
    }

    isolated deinit {
        owner.clear()
    }
}
