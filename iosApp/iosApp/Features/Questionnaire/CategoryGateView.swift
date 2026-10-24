import Shared
import SwiftUI

/// « Besoin de quelque chose en Entretien ? »: a category gate, answered like a card (swipe or buttons).
struct CategoryGateScreen: View {
    let gate: CategoryGateUi
    let send: (any QuestionEvent) -> Void

    @State private var command: SwipeCommand?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(spacing: 0) {
            SwipeableCard(command: $command, dragEnabled: !dynamicTypeSize.isAccessibilitySize, onSwipe: answer) {
                content
            }
            .accessibilityElement(children: .contain)
            .accessibilityAction(named: Text("Oui")) { answer(.yes) }
            .accessibilityAction(named: Text("Non")) { answer(.no) }
            .padding(.horizontal)
            .padding(.top, 8)
            .frame(maxHeight: .infinity)

            HStack(spacing: 12) {
                Button {
                    command = SwipeCommand(direction: .no) { answer(.no) }
                } label: {
                    Label("Non", systemImage: "xmark")
                }
                .buttonStyle(.big(.secondary))
                .accessibilityHint("Passe ce rayon")

                Button {
                    command = SwipeCommand(direction: .yes) { answer(.yes) }
                } label: {
                    Label("Oui", systemImage: "checkmark")
                }
                .buttonStyle(.big)
                .accessibilityHint("Montre les produits de ce rayon")
            }
            .padding()
        }
    }

    private var content: some View {
        VStack(spacing: 20) {
            Image(systemName: "basket")
                .font(.system(size: 56))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            question
                .font(.title.bold())
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            Text("\(Int(gate.productCount)) produits dans ce rayon")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(28)
        .frame(maxWidth: .infinity, minHeight: 280)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: .rect(cornerRadius: 28))
        .shadow(color: .black.opacity(0.08), radius: 16, y: 6)
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

/// « Courses pour combien de jours ? », « Combien de personnes ? », « Des invités ? »: one tap on a quick answer,
/// or the stepper then « Valider ».
struct MetaQuestionView: View {
    let question: MetaQuestionUi
    let send: (any QuestionEvent) -> Void

    @State private var value: Int

    init(question: MetaQuestionUi, send: @escaping (any QuestionEvent) -> Void) {
        self.question = question
        self.send = send
        _value = State(initialValue: Int(question.value))
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        title
                            .font(.largeTitle.bold())
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                        subtitle
                            .font(.title3)
                            .foregroundStyle(.secondary)
                    }

                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: 10)], spacing: 10) {
                        ForEach(choices, id: \.self) { choice in
                            Button {
                                value = choice
                                send(QuestionEventAnswerMeta(value: Int32(choice)))
                            } label: {
                                valueLabel(choice)
                            }
                            .buttonStyle(.big(choice == Int(question.value) ? .selected : .secondary))
                        }
                    }

                    HStack(spacing: 16) {
                        stepButton(increment: false)
                        valueLabel(value)
                            .font(.title2.weight(.semibold))
                            .monospacedDigit()
                            .frame(maxWidth: .infinity)
                            .contentTransition(.numericText())
                        stepButton(increment: true)
                    }
                    .padding(.vertical, 8)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(title)
                    .accessibilityValue(valueLabel(value))
                    .accessibilityAdjustableAction { direction in
                        switch direction {
                        case .increment: step(1)
                        case .decrement: step(-1)
                        @unknown default: break
                        }
                    }
                }
                .padding()
            }

            Button {
                send(QuestionEventAnswerMeta(value: Int32(value)))
            } label: {
                Text("Valider")
            }
            .buttonStyle(.big)
            .padding()
        }
    }

    private var choices: [Int] {
        question.choices.map(\.intValue)
    }

    private var title: Text {
        switch question.kind {
        case .horizonDays: Text("Courses pour combien de jours\u{00A0}?")
        case .persons: Text("Combien de personnes cette semaine\u{00A0}?")
        case .guests: Text("Des invités\u{00A0}?")
        }
    }

    private var subtitle: Text {
        switch question.kind {
        case .horizonDays: Text("Jusqu'aux prochaines courses.")
        case .persons: Text("Vous compris, à la maison.")
        case .guests: Text("En plus du foyer, cette semaine.")
        }
    }

    private func valueLabel(_ value: Int) -> Text {
        switch question.kind {
        case .horizonDays: Text("\(value) jours")
        case .persons: Text("\(value) personnes")
        case .guests: value == 0 ? Text("Aucun") : Text("\(value) invités")
        }
    }

    private func step(_ delta: Int) {
        let next = min(max(value + delta, Int(question.min)), Int(question.max))
        withAnimation(.snappy) { value = next }
    }

    private func stepButton(increment: Bool) -> some View {
        let enabled = increment ? value < Int(question.max) : value > Int(question.min)
        return Button {
            step(increment ? 1 : -1)
        } label: {
            Image(systemName: increment ? "plus" : "minus")
                .font(.title2.weight(.semibold))
                .frame(width: 56, height: 56)
                .background(Color(uiColor: .tertiarySystemFill), in: .circle)
                .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.35)
    }
}
