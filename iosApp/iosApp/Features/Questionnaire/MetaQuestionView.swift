import Shared
import SwiftUI

/// « Courses pour combien de jours ? », « Combien de personnes ? », « Des invités ? » (mockup `Meta`): one tap on a
/// large choice answers; the boxed stepper picks another value, then « Continuer avec … ». « Passer » keeps the
/// pre-filled value.
struct MetaQuestionView: View {
    let question: MetaQuestionUi
    let canUndo: Bool
    let send: (any QuestionEvent) -> Void

    @State private var value: Int

    init(question: MetaQuestionUi, canUndo: Bool, send: @escaping (any QuestionEvent) -> Void) {
        self.question = question
        self.canUndo = canUndo
        self.send = send
        _value = State(initialValue: Int(question.value))
    }

    var body: some View {
        BigChoiceQuestionView(
            overline: Text("Pour bien doser"),
            title: title,
            subtitle: subtitle,
            choices: choices,
            columns: columnCount,
            isSelected: { $0 == value },
            accessibilityLabel: valueLabel,
            onSelect: { choice in
                value = choice
                send(QuestionEventAnswerMeta(value: Int32(choice)))
            },
            tileLabel: choiceLabel,
            stepper: BoxedStepper(
                text: otherValueLabel,
                accessibilityLabel: title,
                accessibilityValue: valueLabel(value),
                canDecrement: value > Int(question.min),
                canIncrement: value < Int(question.max),
                onStep: { step($0 ? 1 : -1) }
            )
        ) {
            VStack(spacing: 0) {
                Text("Un toucher suffit\u{00A0}: la question suivante arrive.")
                    .textStyle(.help)
                    .foregroundStyle(Palette.inkSoft)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, 14)
                Button {
                    send(QuestionEventAnswerMeta(value: Int32(value)))
                } label: {
                    continueLabel
                }
                .buttonStyle(.primary)
                QuestionFooter(canUndo: canUndo, onUndo: { send(QuestionEventUndo.shared) }) {
                    Button("Passer") {
                        send(QuestionEventAnswerMeta(value: question.value))
                    }
                    .buttonStyle(.text)
                    .accessibilityHint("Garde la valeur proposée")
                }
                .padding(.top, 12)
            }
        }
    }

    private var choices: [Int] {
        question.choices.map(\.intValue)
    }

    private var columnCount: Int {
        // Up to four choices on one row; more on balanced rows of three (or four for 7 and 8). Same rule on Android.
        let count = choices.count
        return count <= 4 ? max(count, 1) : (count == 7 || count == 8 ? 4 : 3)
    }

    private var title: Text {
        switch question.kind {
        case .horizonDays: Text("Courses pour combien de jours\u{00A0}?")
        case .persons: Text("Combien de personnes à la maison\u{00A0}?")
        case .guests: Text("Des invités\u{00A0}?")
        }
    }

    private var subtitle: Text {
        switch question.kind {
        case .horizonDays: Text("Jusqu'à vos prochaines courses. On ajuste les quantités en conséquence.")
        case .persons: Text("Vous compris. On ajuste les quantités en conséquence.")
        case .guests: Text("Pour prévoir un peu plus cette fois.")
        }
    }

    /// A large choice: the number over its unit (« 7 » / « jours »); no guest is said in words (« Aucun »), without a
    /// unit, as on Android.
    private func choiceLabel(_ choice: Int) -> ChoiceTileLabel {
        let selected = choice == value
        if question.kind == .guests && choice == 0 {
            return ChoiceTileLabel(value: Text("Aucun"), large: true, selected: selected)
        }
        return ChoiceTileLabel(value: Text(verbatim: "\(choice)"), note: unitNote(choice), large: true, selected: selected)
    }

    /// « jours », « personnes », « invités » under the number of a large choice: the plural string « 7 jours »
    /// without its number, so that the word agrees with it in every language.
    private func unitNote(_ value: Int) -> Text {
        let full = switch question.kind {
        case .horizonDays: String(localized: "\(value) jours")
        case .persons: String(localized: "\(value) personnes")
        case .guests: String(localized: "\(value) invités")
        }
        let word = full.drop { $0.isNumber || $0.isWhitespace }
        return Text(verbatim: String(word))
    }

    private func valueLabel(_ value: Int) -> Text {
        switch question.kind {
        case .horizonDays: Text("\(value) jours")
        case .persons: Text("\(value) personnes")
        case .guests: value == 0 ? Text("Aucun") : Text("\(value) invités")
        }
    }

    /// « Autre durée : 7 jours ».
    private var otherValueLabel: Text {
        switch question.kind {
        case .horizonDays: Text("Autre durée\u{00A0}: \(valueLabel(value))")
        case .persons, .guests: Text("Autre nombre\u{00A0}: \(valueLabel(value))")
        }
    }

    /// « Continuer avec 7 jours ».
    private var continueLabel: Text {
        if question.kind == .guests && value == 0 {
            return Text("Continuer sans invités")
        }
        return Text("Continuer avec \(valueLabel(value))")
    }

    private func step(_ delta: Int) {
        let next = min(max(value + delta, Int(question.min)), Int(question.max))
        withAnimation(.snappy) { value = next }
    }
}
