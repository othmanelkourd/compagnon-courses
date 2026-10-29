import Shared
import SwiftUI

// MARK: - Text pieces

/// Surtitre: 13 / 700 capitals, +0.8.
struct Overline: View {
    let text: Text
    var color: Color = Palette.inkSoft

    var body: some View {
        text
            .textStyle(.overline)
            .foregroundStyle(color)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Puce: radius 999, 12 / 700. `basil`: « Essentiel »; `tomato`: « À surveiller ».
struct Chip: View {
    enum Tone { case basil, tomato }

    let text: Text
    var tone: Tone = .basil

    var body: some View {
        text
            .textStyle(.chip)
            .foregroundStyle(tone == .basil ? Palette.basilDark : Palette.tomatoText)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(tone == .basil ? Palette.basilLight : Palette.tomatoLight, in: .capsule)
    }
}

/// Indice: Surface douce, radius 12, info icon and Aide text, full width.
struct Hint: View {
    let text: Text

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            IconView(.info, size: 16)
                .alignmentGuide(.firstTextBaseline) { $0[VerticalAlignment.center] + 4 }
            text
                .textStyle(.help)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .foregroundStyle(Palette.inkSoft)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.surfaceSoft, in: .rect(cornerRadius: Radius.hint))
    }
}

// MARK: - Checkboxes

/// Case à cocher: 26, « ronde » or « carrée » (radius 8, Ma liste). Checked: Basilic with a white check; empty: 2 pt
/// Contour case border. The box fills (150 ms) and its check draws itself (motion 4). The caller gives it a 48 touch
/// area.
struct Checkbox: View {
    enum Shape { case round, square }

    let checked: Bool
    var shape: Shape = .round
    /// Fill of a checked box: Basilic, or Contour case for a box that cannot be changed (« Déjà exclu »).
    var fill: Color = Palette.basil

    var body: some View {
        let radius: CGFloat = shape == .round ? 13 : 8
        ZStack {
            RoundedRectangle(cornerRadius: radius)
                .strokeBorder(Palette.checkboxBorder, lineWidth: 2)
            if checked {
                RoundedRectangle(cornerRadius: radius)
                    .fill(fill)
                    .transition(.opacity)
                DrawnCheck(size: 16, lineWidth: 2)
            }
        }
        .frame(width: 26, height: 26)
        .frame(width: Target.checkbox, height: Target.checkbox)
        .contentShape(.rect)
        .animation(Motion.standard(Motion.Duration.quick), value: checked)
    }
}

// MARK: - Steppers

/// Pas-à-pas compact (suggestion rows): Surface douce, radius 12, « − value + ». One adjustable VoiceOver element.
struct CompactStepper: View {
    let stepper: QuantityStepper
    var dimmed = false
    let onStep: (_ increment: Bool) -> Void

    var body: some View {
        HStack(spacing: 2) {
            button(increment: false)
            // At most 112 wide: a very long quantity goes on two lines rather than squeezing the name.
            CappedWidth(maxWidth: 112) {
                quantityText
            }
            button(increment: true)
        }
        .padding(2)
        .background(Palette.surfaceSoft, in: .rect(cornerRadius: Radius.stepper))
        .opacity(dimmed ? 0.5 : 1)
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

    private var quantityText: some View {
        Text(verbatim: stepper.selected.label)
            .textStyle(.secondary)
            .fontWeight(.bold)
            .foregroundStyle(Palette.ink)
            .monospacedDigit()
            .multilineTextAlignment(.center)
            // A quantity is never cut: a very long one goes on two lines.
            .lineLimit(2)
            .fixedSize(horizontal: false, vertical: true)
            .frame(minWidth: 64)
            .contentTransition(.numericText())
    }

    private func button(increment: Bool) -> some View {
        let enabled = increment ? stepper.canIncrement : stepper.canDecrement
        return Button {
            onStep(increment)
        } label: {
            IconView(increment ? .plus : .minus, size: 16, weight: .bold)
                .foregroundStyle(Palette.ink)
                .frame(width: 32, height: 40)
                // 44 × 48 touch area around the 32 × 40 button.
                .contentShape(.rect.inset(by: -6))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.3)
    }
}

/// Proposes at most `maxWidth` to its content, even for its ideal size, so that a long text wraps instead of
/// growing past it.
struct CappedWidth: Layout {
    let maxWidth: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        guard let subview = subviews.first else { return .zero }
        return subview.sizeThatFits(ProposedViewSize(width: min(proposal.width ?? .infinity, maxWidth), height: proposal.height))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: CGPoint(x: bounds.midX, y: bounds.midY), anchor: .center,
                              proposal: ProposedViewSize(width: bounds.width, height: bounds.height))
    }
}

/// The 48 square buttons of the boxed and free steppers (Surface douce, radius 14).
private struct StepperSquareButton: View {
    let increment: Bool
    let height: CGFloat
    let enabled: Bool
    let label: Text
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            IconView(increment ? .plus : .minus, size: 20, weight: .bold)
                .foregroundStyle(Palette.ink)
                .frame(width: 48, height: height)
                .background(Palette.surfaceSoft, in: .rect(cornerRadius: Radius.stepperButton))
                .contentShape(.rect(cornerRadius: Radius.stepperButton))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.35)
        .accessibilityLabel(label)
    }
}

/// Pas-à-pas encadré (meta questions): Surface, Trait border, radius 20, 48 buttons, « Autre durée : 7 jours ».
struct BoxedStepper: View {
    let text: Text
    let accessibilityLabel: Text
    let accessibilityValue: Text
    let canDecrement: Bool
    let canIncrement: Bool
    var decrementLabel = Text("Moins")
    var incrementLabel = Text("Plus")
    let onStep: (_ increment: Bool) -> Void

    var body: some View {
        HStack(spacing: 8) {
            StepperSquareButton(increment: false, height: 48, enabled: canDecrement, label: decrementLabel) { onStep(false) }
            text
                .textStyle(.listRow)
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .contentTransition(.numericText())
            StepperSquareButton(increment: true, height: 48, enabled: canIncrement, label: incrementLabel) { onStep(true) }
        }
        .padding(10)
        .background(Palette.surface, in: .rect(cornerRadius: Radius.block))
        .overlay { RoundedRectangle(cornerRadius: Radius.block).strokeBorder(Palette.line, lineWidth: 1) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityValue(accessibilityValue)
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: if canIncrement { onStep(true) }
            case .decrement: if canDecrement { onStep(false) }
            @unknown default: break
            }
        }
    }
}

/// Pas-à-pas libre (product card): « Autre quantité : **1 kg** » between 48 × 44 buttons.
struct FreeStepper: View {
    let stepper: QuantityStepper
    /// Adds « · habituel » after the quantity (the usual one, not shown by a preset).
    var usual = false
    let onStep: (_ increment: Bool) -> Void

    var body: some View {
        let quantity = usual ? "\(stepper.selected.label) · \(String(localized: "habituel"))" : stepper.selected.label
        let value = Text(verbatim: quantity)
            .font(.custom(FontName.figtreeBold, size: 15, relativeTo: .subheadline))
            .foregroundColor(Palette.ink)
        HStack(spacing: 8) {
            StepperSquareButton(increment: false, height: 44, enabled: stepper.canDecrement, label: Text("Diminuer")) { onStep(false) }
            Text("Autre quantité\u{00A0}: \(value)")
                .textStyle(.secondary)
                .foregroundStyle(Palette.inkSoft)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
            StepperSquareButton(increment: true, height: 44, enabled: stepper.canIncrement, label: Text("Augmenter")) { onStep(true) }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Autre quantité")
        .accessibilityValue(Text(verbatim: stepper.selected.label))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: if stepper.canIncrement { onStep(true) }
            case .decrement: if stepper.canDecrement { onStep(false) }
            @unknown default: break
            }
        }
    }
}

// MARK: - Choices

/// Choix de quantité: Surface tile, Trait border, radius 16. Selected: Basilic clair with a 2 pt Basilic border.
struct ChoiceTileStyle: ButtonStyle {
    let selected: Bool
    var height: CGFloat = 64
    var radius: CGFloat = Radius.tile

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(selected ? Palette.basilDark : Palette.ink)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 6)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(selected ? Palette.basilLight : (configuration.isPressed ? Palette.surfaceSoft : Palette.surface),
                        in: .rect(cornerRadius: radius))
            .overlay {
                RoundedRectangle(cornerRadius: radius)
                    .strokeBorder(selected ? Palette.basil : Palette.line, lineWidth: selected ? 2 : 1)
            }
            .contentShape(.rect(cornerRadius: radius))
            .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

/// The value of a choice and its optional sub-line (« habituel », « jours »).
struct ChoiceTileLabel: View {
    let value: Text
    var note: Text?
    var large = false
    var selected = false

    var body: some View {
        VStack(spacing: large ? 2 : 0) {
            // A large tile without a sub-line holds a word (« Aucun »): it keeps the regular value size (same on Android).
            value
                .textStyle(large && note != nil ? .valueLarge : .value)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
            if let note {
                note
                    .textStyle(large ? .help : .choiceNote)
                    .foregroundStyle(selected ? Palette.basilDark : Palette.inkSoft)
            }
        }
    }
}

// MARK: - Fields

/// Champ de saisie: Surface, Trait border, radius 16, 50 to 52 high, icon on the left.
struct InputField: View {
    let icon: Icon
    var iconColor: Color = Palette.inkSoft
    let placeholder: Text
    @Binding var text: String
    var submitLabel: SubmitLabel = .done
    var focus: FocusState<Bool>.Binding
    var onSubmit: () -> Void = {}

    var body: some View {
        HStack(spacing: 10) {
            IconView(icon, size: 20, weight: icon == .plus ? .bold : .medium)
                .foregroundStyle(iconColor)
            TextField(text: $text, prompt: placeholder.foregroundColor(Palette.inkMuted)) {
                placeholder
            }
            .textStyle(.body)
            .foregroundStyle(Palette.ink)
            .tint(Palette.basil)
            .focused(focus)
            .submitLabel(submitLabel)
            .autocorrectionDisabled()
            .onSubmit(onSubmit)
            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    IconView(.close, size: 16, weight: .bold)
                        .foregroundStyle(Palette.inkMuted)
                        .frame(width: Target.minimum, height: Target.minimum)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Effacer")
                .padding(.trailing, -10)
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 52)
        .background(Palette.surface, in: .rect(cornerRadius: Radius.field))
        .overlay { RoundedRectangle(cornerRadius: Radius.field).strokeBorder(Palette.line, lineWidth: 1) }
        .contentShape(.rect)
        .onTapGesture { focus.wrappedValue = true }
    }
}

// MARK: - Blocks

/// Bloc de liste: Surface, Trait border, radius 20, Trait léger separators between the rows.
struct ListBlock<Data: RandomAccessCollection, ID: Hashable, Row: View>: View {
    let data: Data
    let id: KeyPath<Data.Element, ID>
    var separatorInset: CGFloat = 0
    /// 20, or 24 for the blocks of the onboarding (charter § 3: 20 to 24).
    var radius: CGFloat = Radius.block
    @ViewBuilder let row: (Data.Element) -> Row

    var body: some View {
        let lastID = data.last?[keyPath: id]
        VStack(spacing: 0) {
            ForEach(data, id: id) { element in
                row(element)
                if element[keyPath: id] != lastID {
                    Rectangle()
                        .fill(Palette.lineLight)
                        .frame(height: 1)
                        .padding(.leading, separatorInset)
                }
            }
        }
        .background(Palette.surface, in: .rect(cornerRadius: radius))
        .clipShape(.rect(cornerRadius: radius))
        .overlay { RoundedRectangle(cornerRadius: radius).strokeBorder(Palette.line, lineWidth: 1) }
    }
}

/// A block with the overline of its section above it.
struct BlockSection<Content: View>: View {
    let title: Text?
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let title {
                Overline(text: title)
                    .padding(.horizontal, 6)
            }
            content
        }
    }
}

// MARK: - Progress

/// The segments of the questionnaire: 5 high, 3 apart, radius 3; Basilic up to `filled`, Segment vide after. A segment
/// reached fills from its left end (motion 6, 400 ms); it fades in with « Réduire les animations ».
struct ProgressSegments: View {
    let total: Int
    let filled: Int
    /// 3 between the 12 segments of the questionnaire, 4 between the 4 of the onboarding.
    var spacing: CGFloat = 3

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: spacing) {
            ForEach(0..<max(total, 1), id: \.self) { index in
                let on = index < filled
                RoundedRectangle(cornerRadius: 3)
                    .fill(Palette.segmentEmpty)
                    .overlay {
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Palette.basil)
                            .scaleEffect(x: on || reduceMotion ? 1 : 0, anchor: .leading)
                            .opacity(on ? 1 : 0)
                    }
                    .clipShape(.rect(cornerRadius: 3))
                    .frame(height: 5)
            }
        }
        .animation(reduceMotion ? Motion.fade : Motion.standard(Motion.Duration.ample), value: filled)
    }
}

/// A 6 high bar (list progress), Basilic over Trait. It fills from empty when it appears (after 300 ms, as in the
/// mockups), then follows `fraction` (motion 6); no movement with « Réduire les animations ».
struct ProgressBar: View {
    let fraction: Double

    @State private var appeared = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        let shown = appeared || reduceMotion ? min(max(fraction, 0), 1) : 0
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(Palette.line)
                Capsule()
                    .fill(Palette.basil)
                    .frame(width: proxy.size.width * shown)
            }
        }
        .frame(height: 6)
        .animation(reduceMotion ? nil : Motion.standard(Motion.Duration.ample), value: fraction)
        .onAppear {
            guard !appeared else { return }
            withAnimation(reduceMotion ? nil : Motion.standard(Motion.Duration.ample).delay(0.3)) {
                appeared = true
            }
        }
    }
}

/// Barre du haut du questionnaire: [Fermer], « n / 12 » over the segments, [⋯] with its menu.
///
/// n is the current question: `answered + 1`, capped at `total`. Without `progress` (review, loading, error) the
/// middle stays empty; without `showsMenu` there is no [⋯].
struct QuestionTopBar<MenuContent: View>: View {
    let progress: QuestionProgress?
    var showsMenu = true
    let onClose: () -> Void
    @ViewBuilder let menu: MenuContent

    var body: some View {
        HStack(spacing: 12) {
            IconButton(icon: .close, label: Text("Fermer le questionnaire"), action: onClose)
                .accessibilityHint("Quitte le questionnaire, qui reprendra au même endroit")
            if let progress {
                let total = max(Int(progress.total), 1)
                let current = min(Int(progress.answered) + 1, total)
                VStack(spacing: 6) {
                    Text(verbatim: "\(current) / \(total)")
                        .textStyle(.counter)
                        .foregroundStyle(Palette.ink)
                        .monospacedDigit()
                        .rollingNumber(current)
                    ProgressSegments(total: total, filled: current)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("Question \(current) sur \(total)"))
                .accessibilityAddTraits(.updatesFrequently)
            } else {
                Spacer(minLength: 0)
            }
            if showsMenu {
                Menu {
                    menu
                } label: {
                    IconView(.menu, size: 20)
                        .foregroundStyle(Palette.ink)
                        .frame(width: Target.minimum, height: Target.minimum)
                        .background(Palette.surface, in: .circle)
                        .overlay { Circle().strokeBorder(Palette.line, lineWidth: 1) }
                        .contentShape(.circle)
                }
                .accessibilityLabel("Plus d'actions")
            }
        }
    }
}

// MARK: - Snackbar

/// Snackbar: Encre, radius 16, white 15 message, « Annuler » in Snackbar action 16 / 700.
struct Snackbar: View {
    let message: Text
    let actionTitle: Text
    let action: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            message
                .textStyle(.secondary)
                .foregroundStyle(Palette.surface)
                .lineLimit(2)
            Spacer(minLength: 8)
            Button(action: action) {
                actionTitle
                    .font(.custom(FontName.figtreeBold, size: 16, relativeTo: .callout))
                    .foregroundStyle(Palette.snackbarAction)
                    .padding(.horizontal, 14)
                    .frame(minHeight: Target.minimum)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, 16)
        .padding([.vertical, .trailing], 6)
        .cardBackground(Palette.ink, radius: Radius.snackbar, elevation: .snackbar)
    }
}

// MARK: - Screen helpers

/// The bottom of the screens with a scrolling list: a fade from transparent to Fond (at 28 %) under the buttons.
struct BottomFade: View {
    var body: some View {
        LinearGradient(
            stops: [
                .init(color: Palette.canvas.opacity(0), location: 0),
                .init(color: Palette.canvas, location: 0.28)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }
}
