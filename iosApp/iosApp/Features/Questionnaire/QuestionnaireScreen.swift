import Shared
import SwiftUI

/// « Questionnaire »: one question per screen, with the progress « 8 / 12 » on top and « Annuler » always at
/// the bottom left. Leaving the screen keeps the session open; the next visit resumes it.
struct QuestionnaireScreen: View {
    let onShowList: () -> Void
    let onClose: () -> Void

    @State private var model = QuestionSessionModel()

    var body: some View {
        QuestionContent(state: model.state, change: model.change, send: model.send, onShowList: onShowList, onClose: onClose)
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

/// Stateless rendering of a `QuestionUiState` with the questionnaire's top bar, so that every state can be
/// previewed (and shown in the Debug demo screens) with a fake state. Each question draws its own bottom row, with
/// « Annuler » at the bottom left.
struct QuestionContent: View {
    let state: any QuestionUiState
    /// How the state was reached: the direction of the step transition, or the pile of cards.
    var change: QuestionStepChange = .forward
    let send: (any QuestionEvent) -> Void
    var onShowList: () -> Void = {}
    var onClose: () -> Void = {}
    /// Opens « Plus d'options » on a product card as soon as it appears (Debug demo screen `options`).
    var showsCardOptions = false

    @State private var confirmsAbandon = false
    @State private var direction = StepDirection()
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let step = QuestionStep(state)
        VStack(spacing: 0) {
            if step.showsTopBar {
                // Same bar as Android: [Fermer]; « n / 12 » while asking; [⋯] while asking and on the review.
                QuestionTopBar(progress: step.isAsking ? step.progress : nil, showsMenu: step.showsMenu, onClose: onClose) {
                    if step.isAsking {
                        Button("Terminer") { send(QuestionEventEndQuestions.shared) }
                    }
                    Button("Recommencer") { send(QuestionEventStartOver.shared) }
                    Button("Abandonner la séance", role: .destructive) { confirmsAbandon = true }
                }
                .padding(.horizontal, Spacing.screen)
                .padding(.top, Spacing.top)
            }
            screen
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Palette.canvas)
        .toolbar(.hidden, for: .navigationBar)
        .alert("Abandonner la séance\u{00A0}?", isPresented: $confirmsAbandon) {
            Button(role: .cancel) {} label: { Text(LocalizedStringResource("dialog.cancel", defaultValue: "Annuler", comment: "Dismisses a confirmation; « Annuler » elsewhere undoes.")) }
            Button("Abandonner", role: .destructive) {
                send(QuestionEventAbandon.shared)
            }
        } message: {
            Text("Rien ne sera ajouté à votre liste. Les réponses déjà données sont gardées.")
        }
    }

    /// The next question slides in (from the leading edge when going back); after a card, the next card's screen
    /// cross-fades over the pile, which has already moved.
    private var stepTransition: AnyTransition {
        direction.forward = change != .backward
        return switch change {
        case .forward, .backward: .step(direction, reduceMotion: reduceMotion)
        case .nextCard: AnyTransition.opacity.animation(reduceMotion ? Motion.fade : Motion.standard())
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
            MetaQuestionView(question: meta.question, canUndo: meta.canUndo, send: send)
                .id(meta.question.kind)
                .transition(stepTransition)
        case .suggested(let suggested):
            ItemChecklistView(
                title: Text("Probablement nécessaire"),
                subtitle: Text("Vos essentiels, à vos quantités habituelles. Décochez ce qui ne sert pas cette fois."),
                items: suggested.items,
                checkedCount: Int(suggested.checkedCount),
                canUndo: suggested.canUndo,
                send: send
            )
            .transition(stepTransition)
        case .card(let card):
            ProductCardScreen(
                card: card.card,
                nextImageKey: card.nextImageKey,
                canUndo: card.canUndo,
                send: send,
                imagePops: change != .nextCard,
                showsOptionsInitially: showsCardOptions
            )
            .id(card.card.productId)
            .transition(stepTransition)
        case .gate(let gate):
            CategoryGateScreen(gate: gate.gate, canUndo: gate.canUndo, send: send)
                .id(gate.gate.categoryId)
                .transition(stepTransition)
        case .gateItems(let items):
            ItemChecklistView(
                title: Text(verbatim: items.gate.name),
                subtitle: Text("Cochez ce qu'il vous faut."),
                items: items.items,
                checkedCount: Int(items.checkedCount),
                canUndo: items.canUndo,
                send: send
            )
            .id(items.gate.categoryId)
            .transition(stepTransition)
        case .review(let review):
            ReviewView(review: review, send: send)
                .transition(stepTransition)
        case .done(let done):
            SessionDoneView(done: done, onShowList: onShowList, onClose: onClose)
                .transition(AnyTransition.opacity.animation(Motion.fade))
        case .error(let error):
            SessionErrorView(error: error, send: send)
        }
    }
}


/// What the top bar needs from any state.
struct QuestionStep {
    let progress: QuestionProgress?
    /// A question is on screen: the counter and « Terminer » (skip the remaining questions) make sense.
    let isAsking: Bool
    /// [⋯] (« Recommencer », « Abandonner la séance »): while asking and on the review.
    let showsMenu: Bool
    /// Nothing while the list is written or once it is done; [Fermer] alone while loading or after an error.
    let showsTopBar: Bool

    init(_ state: any QuestionUiState) {
        switch onEnum(of: state) {
        case .meta(let s): self.init(progress: s.progress, isAsking: true)
        case .suggested(let s): self.init(progress: s.progress, isAsking: true)
        case .card(let s): self.init(progress: s.progress, isAsking: true)
        case .gate(let s): self.init(progress: s.progress, isAsking: true)
        case .gateItems(let s): self.init(progress: s.progress, isAsking: true)
        case .review(let s): self.init(progress: s.progress, isAsking: false, showsMenu: true)
        case .loading, .error: self.init(progress: nil, isAsking: false)
        case .committing, .done: self.init(progress: nil, isAsking: false, showsTopBar: false)
        }
    }

    private init(progress: QuestionProgress?, isAsking: Bool, showsMenu: Bool? = nil, showsTopBar: Bool = true) {
        self.progress = progress
        self.isAsking = isAsking
        self.showsMenu = showsMenu ?? isAsking
        self.showsTopBar = showsTopBar
    }
}
