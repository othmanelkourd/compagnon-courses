import Shared
import SwiftUI

/// « Besoin de quelque chose en Entretien ? » (mockup `Gate`): a dark card answered like a product card (swipe
/// or buttons). Product examples are left out: the state does not give them.
struct CategoryGateScreen: View {
    let gate: CategoryGateUi
    let canUndo: Bool
    let send: (any QuestionEvent) -> Void

    @State private var command: SwipeCommand?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let scrolls = dynamicTypeSize.isAccessibilitySize
        VStack(spacing: 0) {
            Group {
                if scrolls {
                    ScrollView { card(dragEnabled: false) }
                } else {
                    card(dragEnabled: true)
                    Spacer(minLength: 16)
                }
            }

            RatioHStack {
                Button {
                    command = SwipeCommand(direction: .no) { answer(.no) }
                } label: {
                    Text("Rien")
                }
                .buttonStyle(.secondary(height: 60))
                .accessibilityHint("Passe tout le rayon")

                Button {
                    command = SwipeCommand(direction: .yes) { answer(.yes) }
                } label: {
                    ButtonLabel("Oui, voir le rayon", icon: .arrowRight, trailingIcon: true)
                }
                .buttonStyle(.primary(height: 60))
                .accessibilityHint("Montre les produits de ce rayon")
            }
            .padding(.top, scrolls ? 12 : 0)

            QuestionFooter(canUndo: canUndo, onUndo: { send(QuestionEventUndo.shared) }) {
                Button("Terminer") {
                    send(QuestionEventEndQuestions.shared)
                }
                .buttonStyle(.text)
                .accessibilityHint("Passe les questions restantes et montre le récapitulatif")
            }
            .padding(.top, 10)
        }
        .padding(.horizontal, Spacing.screen)
        .padding(.bottom, 4)
    }

    private func card(dragEnabled: Bool) -> some View {
        SwipeableCard(command: $command, dragEnabled: dragEnabled, onDark: true, onSwipe: answer) {
            content
        }
        .accessibilityElement(children: .contain)
        .accessibilityAction(named: Text("Oui")) { answer(.yes) }
        .accessibilityAction(named: Text("Non")) { answer(.no) }
        .padding(.top, 28)
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .center) {
                IconView(.basket, size: 32, weight: .regular)
                    .foregroundStyle(Palette.surface)
                    .frame(width: 64, height: 64)
                    .background(Palette.surface.opacity(0.12), in: .rect(cornerRadius: 20))
                Spacer(minLength: 8)
                Overline(text: Text("Tout un rayon"), color: Palette.surface.opacity(0.8))
            }
            question
                .textStyle(.screenTitleLarge)
                .foregroundStyle(Palette.surface)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if gate.productCount > 0 {
                Text("Une seule question pour \(Int(gate.productCount)) produits. Si c'est non, on passe tout le rayon.")
                    .textStyle(.bodyCompact)
                    .foregroundStyle(Palette.surface.opacity(0.82))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(EdgeInsets(top: 26, leading: 22, bottom: 24, trailing: 22))
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardBackground(Palette.darkCard, radius: Radius.card, elevation: .darkCard)
    }

    private var question: Text {
        if let question = gate.question, !question.isEmpty {
            return Text(verbatim: question)
        }
        return Text("Besoin de quelque chose en \(gate.name)\u{00A0}?")
    }

    private func answer(_ direction: SwipeDirection) {
        send(QuestionEventAnswerGate(yes: direction == .yes))
    }
}
