import Shared
import SwiftUI

/// A full-width, tall button for one-thumb answers (at least 56 pt high, grows with Dynamic Type).
struct BigButtonStyle: ButtonStyle {
    enum Kind {
        /// Filled with the tint: « Oui », « Ajouter… ».
        case primary
        /// Tinted background: « Non », presets.
        case secondary
        /// Selected preset or choice.
        case selected
    }

    var kind: Kind = .primary
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.title3.weight(.semibold))
            .multilineTextAlignment(.center)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: 56)
            .foregroundStyle(foreground)
            .background(background, in: .rect(cornerRadius: 16))
            .overlay {
                if kind == .selected {
                    RoundedRectangle(cornerRadius: 16).strokeBorder(Color.accentColor, lineWidth: 2)
                }
            }
            .opacity(isEnabled ? 1 : 0.4)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.snappy(duration: 0.15), value: configuration.isPressed)
            .contentShape(.rect(cornerRadius: 16))
    }

    private var foreground: Color {
        switch kind {
        case .primary: .white
        case .secondary, .selected: .primary
        }
    }

    private var background: AnyShapeStyle {
        switch kind {
        case .primary: AnyShapeStyle(Color.accentColor)
        case .secondary: AnyShapeStyle(Color(uiColor: .tertiarySystemFill))
        case .selected: AnyShapeStyle(Color.accentColor.opacity(0.15))
        }
    }
}

extension ButtonStyle where Self == BigButtonStyle {
    static var big: BigButtonStyle { BigButtonStyle() }
    static func big(_ kind: BigButtonStyle.Kind) -> BigButtonStyle { BigButtonStyle(kind: kind) }
}

/// « − 2 bouteilles + »: a quantity stepper, one VoiceOver element adjusted by swiping up and down.
struct QuantityStepperView: View {
    let stepper: QuantityStepper
    var compact = false
    let onStep: (_ increment: Bool) -> Void

    var body: some View {
        HStack(spacing: compact ? 4 : 12) {
            stepButton(increment: false)
            Text(verbatim: stepper.selected.label)
                .font(compact ? .subheadline.weight(.medium) : .title3.weight(.semibold))
                .monospacedDigit()
                .multilineTextAlignment(.center)
                .lineLimit(compact ? 1 : 2)
                .minimumScaleFactor(0.8)
                .fixedSize(horizontal: compact, vertical: false)
                .frame(minWidth: compact ? 56 : 120)
                .contentTransition(.numericText())
            stepButton(increment: true)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Quantité")
        .accessibilityValue(Text(verbatim: stepper.selected.label))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: if stepper.canIncrement { onStep(true) }
            case .decrement: if stepper.canDecrement { onStep(false) }
            @unknown default: break
            }
        }
    }

    private func stepButton(increment: Bool) -> some View {
        Button {
            onStep(increment)
        } label: {
            Image(systemName: increment ? "plus" : "minus")
                .font(compact ? .body.weight(.semibold) : .title2.weight(.semibold))
                .frame(width: compact ? 36 : 52, height: compact ? 36 : 52)
                .background(Color(uiColor: .tertiarySystemFill), in: .circle)
                .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .disabled(increment ? !stepper.canIncrement : !stepper.canDecrement)
        .opacity((increment ? stepper.canIncrement : stepper.canDecrement) ? 1 : 0.35)
        .accessibilityLabel(increment ? "Plus" : "Moins")
    }
}

/// « 8 / 12 » with a thin bar, for the navigation bar.
struct QuestionProgressView: View {
    let progress: QuestionProgress

    var body: some View {
        let total = max(Int(progress.total), 1)
        let answered = min(Int(progress.answered), total)
        VStack(spacing: 4) {
            Text(verbatim: "\(answered) / \(total)")
                .font(.subheadline.weight(.semibold))
                .monospacedDigit()
                .contentTransition(.numericText())
            ProgressView(value: Double(answered), total: Double(total))
                .frame(width: 120)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Progression")
        .accessibilityValue(Text("\(answered) questions sur \(total)"))
    }
}

/// A centered spinner with a caption (loading, writing the list).
struct QuestionBusyView: View {
    let title: LocalizedStringKey

    var body: some View {
        ProgressView(title)
            .controlSize(.large)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// « Pourquoi cette question ? »: one short line built from `ReasonUi`.
enum ReasonText {
    static func text(for reason: ReasonUi) -> Text {
        switch reason.kind {
        case .due:
            if let days = reason.daysSinceLast?.intValue, let interval = reason.intervalDays?.intValue {
                return Text("Acheté il y a \(days) jours, d'habitude tous les \(interval) jours")
            }
            if let days = reason.daysSinceLast?.intValue {
                return Text("Acheté il y a \(days) jours")
            }
            return Text("Bientôt le moment d'en racheter")
        case .staple:
            return Text("Un essentiel que vous achetez souvent")
        case .seasonal:
            return Text("C'est la saison")
        case .event:
            return Text("Pour vos invités")
        case .radar:
            if let weeks = reason.weeksSinceLast?.intValue {
                return Text("Pas acheté depuis \(weeks) semaines")
            }
            return Text("Pas acheté depuis un moment")
        case .prior:
            if let size = reason.householdSize?.intValue {
                return Text("Souvent acheté par les foyers de \(size) personnes")
            }
            return Text("Souvent acheté par les foyers comme le vôtre")
        case .exploration:
            return Text("Une idée à essayer")
        case .littleLeft:
            return Text("Il en restait un peu la dernière fois")
        case .lastList:
            return Text("Sur votre dernière liste")
        }
    }
}

/// The reason line under a question, with an info icon; VoiceOver reads it as « Pourquoi cette question : … ».
struct ReasonLabel: View {
    let reason: ReasonUi

    var body: some View {
        Label {
            ReasonText.text(for: reason)
        } icon: {
            Image(systemName: "info.circle")
        }
        .font(.subheadline)
        .foregroundStyle(.secondary)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Pourquoi cette question")
        .accessibilityValue(ReasonText.text(for: reason))
    }
}
