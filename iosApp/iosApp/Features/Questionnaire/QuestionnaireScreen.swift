import Shared
import SwiftUI

/// « Questionnaire »: one question per screen, with the progress « 8 / 12 » on top and « Annuler » always at
/// thumb height. Leaving the screen keeps the session open; the next visit resumes it.
struct QuestionnaireScreen: View {
    let onShowList: () -> Void
    let onClose: () -> Void

    @State private var model = QuestionSessionModel()

    var body: some View {
        QuestionContent(state: model.state, send: model.send, onShowList: onShowList, onClose: onClose)
            .task {
                await model.observe()
            }
            .sensoryFeedback(trigger: model.haptic?.tick) { _, _ in
                switch model.haptic?.kind {
                case .itemAdded: .success
                case .answered: .selection
                case .undone: .impact(weight: .light)
                case nil: nil
                }
            }
    }
}

/// Stateless rendering of a `QuestionUiState` with its navigation bar and bottom bar, so that every state can be
/// previewed (and shown in the Debug demo screens) with a fake state.
struct QuestionContent: View {
    let state: any QuestionUiState
    let send: (any QuestionEvent) -> Void
    var onShowList: () -> Void = {}
    var onClose: () -> Void = {}

    @State private var confirmsAbandon = false

    var body: some View {
        let step = QuestionStep(state)
        screen
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(step.isFinished)
            .toolbar {
                if let progress = step.progress {
                    ToolbarItem(placement: .principal) {
                        QuestionProgressView(progress: progress)
                    }
                }
                if step.isInteractive {
                    ToolbarItem(placement: .primaryAction) {
                        Menu("Options", systemImage: "ellipsis.circle") {
                            Button("Recommencer", systemImage: "arrow.counterclockwise") {
                                send(QuestionEventStartOver.shared)
                            }
                            Button("Abandonner la séance", systemImage: "xmark", role: .destructive) {
                                confirmsAbandon = true
                            }
                        }
                    }
                    ToolbarItemGroup(placement: .bottomBar) {
                        Button {
                            send(QuestionEventUndo.shared)
                        } label: {
                            Label("Annuler", systemImage: "arrow.uturn.backward")
                                .labelStyle(.titleAndIcon)
                        }
                        .disabled(!step.canUndo)
                        .accessibilityHint("Revient à la question précédente")
                        Spacer()
                        if step.canEnd {
                            Button {
                                send(QuestionEventEndQuestions.shared)
                            } label: {
                                Label("Terminer", systemImage: "forward.end")
                                    .labelStyle(.titleAndIcon)
                            }
                            .accessibilityHint("Passe les questions restantes et montre le récapitulatif")
                        }
                    }
                }
            }
            .confirmationDialog("Abandonner la séance\u{00A0}?", isPresented: $confirmsAbandon, titleVisibility: .visible) {
                Button("Abandonner", role: .destructive) {
                    send(QuestionEventAbandon.shared)
                }
            } message: {
                Text("Rien ne sera ajouté à votre liste. Les réponses déjà données sont gardées.")
            }
    }

    @ViewBuilder
    private var screen: some View {
        switch onEnum(of: state) {
        case .loading:
            QuestionBusyView(title: "Préparation des questions…")
        case .committing:
            QuestionBusyView(title: "Ajout à votre liste…")
        case .meta(let meta):
            MetaQuestionView(question: meta.question, send: send)
                .id(meta.question.kind)
                .transition(.cardStep)
        case .suggested(let suggested):
            ItemChecklistView(
                title: Text("Probablement nécessaire"),
                subtitle: Text("Décochez ce dont vous n'avez pas besoin cette fois."),
                items: suggested.items,
                checkedCount: Int(suggested.checkedCount),
                send: send
            )
            .transition(.cardStep)
        case .card(let card):
            ProductCardScreen(card: card.card, send: send)
                .id(card.card.productId)
                .transition(.cardStep)
        case .gate(let gate):
            CategoryGateScreen(gate: gate.gate, send: send)
                .id(gate.gate.categoryId)
                .transition(.cardStep)
        case .gateItems(let items):
            ItemChecklistView(
                title: Text(verbatim: items.gate.name),
                subtitle: Text("Cochez ce qu'il vous faut."),
                items: items.items,
                checkedCount: Int(items.checkedCount),
                send: send
            )
            .id(items.gate.categoryId)
            .transition(.cardStep)
        case .review(let review):
            ReviewView(review: review, send: send)
                .transition(.cardStep)
        case .done(let done):
            SessionDoneView(done: done, onShowList: onShowList, onClose: onClose)
                .transition(.opacity)
        case .error(let error):
            SessionErrorView(error: error, send: send)
        }
    }
}

extension AnyTransition {
    /// The next question comes in from slightly behind; the previous one (already swiped away) fades out.
    static var cardStep: AnyTransition {
        .asymmetric(insertion: .scale(scale: 0.94).combined(with: .opacity), removal: .opacity)
    }
}

/// What the bars need from any state.
struct QuestionStep {
    let progress: QuestionProgress?
    let canUndo: Bool
    /// A question is on screen: the bars offer « Annuler », « Terminer » and « Abandonner ».
    let isInteractive: Bool
    /// « Terminer » (skip the remaining questions) makes sense: not on the review.
    let canEnd: Bool
    let isFinished: Bool

    init(_ state: any QuestionUiState) {
        switch onEnum(of: state) {
        case .meta(let s): self.init(progress: s.progress, canUndo: s.canUndo, canEnd: true)
        case .suggested(let s): self.init(progress: s.progress, canUndo: s.canUndo, canEnd: true)
        case .card(let s): self.init(progress: s.progress, canUndo: s.canUndo, canEnd: true)
        case .gate(let s): self.init(progress: s.progress, canUndo: s.canUndo, canEnd: true)
        case .gateItems(let s): self.init(progress: s.progress, canUndo: s.canUndo, canEnd: true)
        case .review(let s): self.init(progress: s.progress, canUndo: s.canUndo, canEnd: false)
        case .done: self.init(progress: nil, canUndo: false, canEnd: false, isFinished: true)
        case .loading, .committing, .error: self.init(progress: nil, canUndo: false, canEnd: false)
        }
    }

    private init(progress: QuestionProgress?, canUndo: Bool, canEnd: Bool, isFinished: Bool = false) {
        self.progress = progress
        self.canUndo = canUndo
        self.isInteractive = progress != nil
        self.canEnd = canEnd
        self.isFinished = isFinished
    }
}
