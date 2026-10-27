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

    /// How the last state change moved through the questions, for the transition of the screens.
    private(set) var change: QuestionStepChange = .forward

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
            let change = QuestionStepChange(from: state, to: newState)
            // One animation for every change; each screen's transition brings its own timing.
            withAnimation(Motion.standard()) {
                self.change = change
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

/// How the questionnaire moved from one state to the next.
enum QuestionStepChange {
    /// The next question: it slides in from the trailing edge (motion 1).
    case forward
    /// Back to an earlier question (« Annuler »): it slides in from the leading edge.
    case backward
    /// From a card (or an aisle) to the next one: the card behind already came forward, the screens cross-fade.
    case nextCard

    init(from old: any QuestionUiState, to new: any QuestionUiState) {
        if let before = QuestionStep(old).progress?.answered, let after = QuestionStep(new).progress?.answered, after < before {
            self = .backward
        } else if Self.isCard(old), Self.isCard(new) {
            self = .nextCard
        } else {
            self = .forward
        }
    }

    private static func isCard(_ state: any QuestionUiState) -> Bool {
        state is QuestionUiStateCard || state is QuestionUiStateGate
    }
}
