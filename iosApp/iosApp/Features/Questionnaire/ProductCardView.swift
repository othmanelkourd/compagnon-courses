import Shared
import SwiftUI

/// One product question (mockup `Card`): swipe right = « Oui » (with the stepper's quantity), left = « Non »,
/// duplicated by the buttons below the card. Presets answer « Oui » with their quantity in one tap.
struct ProductCardScreen: View {
    let card: ProductCardUi
    let canUndo: Bool
    let send: (any QuestionEvent) -> Void
    var showsOptionsInitially = false

    @State private var command: SwipeCommand?
    @State private var showsOptions = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let scrolls = dynamicTypeSize.isAccessibilitySize
        VStack(spacing: 0) {
            Group {
                if scrolls {
                    ScrollView { cardStack(dragEnabled: false, fills: false) }
                } else {
                    cardStack(dragEnabled: true, fills: true)
                    swipeHints
                        .padding(.top, 4)
                }
            }
            .padding(.top, 20)

            RatioHStack {
                Button {
                    command = SwipeCommand(direction: .no) { answer(.no) }
                } label: {
                    ButtonLabel("Non", icon: .close)
                }
                .buttonStyle(.secondary(height: 60))
                .accessibilityHint("Passe au produit suivant")

                Button {
                    command = SwipeCommand(direction: .yes) { answer(.yes) }
                } label: {
                    ButtonLabel(Text("Oui · \(card.quantity.selected.label)"), icon: .check)
                }
                .buttonStyle(.primary(height: 60))
                .accessibilityLabel(Text("Oui, \(card.quantity.selected.label)"))
                .accessibilityHint("Ajoute ce produit à la liste")
            }
            .padding(.top, 16)

            QuestionFooter(canUndo: canUndo, onUndo: { send(QuestionEventUndo.shared) }) {
                Button {
                    showsOptions = true
                } label: {
                    ButtonLabel("Plus d'options", icon: .chevronDown, iconSize: 16, trailingIcon: true)
                }
                .buttonStyle(.text)
            }
            .padding(.top, 10)
        }
        .padding(.horizontal, Spacing.screen)
        .padding(.bottom, 4)
        .sheet(isPresented: $showsOptions) {
            NuancedAnswersSheet(productName: card.name) { answer in
                showsOptions = false
                send(QuestionEventNuanced(answer: answer))
            }
        }
        .onAppear {
            if showsOptionsInitially { showsOptions = true }
        }
    }

    /// The card over the next one (70 % opacity, slightly smaller and lower): the pile of questions.
    private func cardStack(dragEnabled: Bool, fills: Bool) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: Radius.card)
                .fill(Palette.surface)
                .overlay { RoundedRectangle(cornerRadius: Radius.card).strokeBorder(Palette.line, lineWidth: 1) }
                .padding(.horizontal, 14)
                .scaleEffect(0.96)
                .offset(y: 14)
                .opacity(0.7)
                .accessibilityHidden(true)
            SwipeableCard(command: $command, dragEnabled: dragEnabled, onSwipe: answer) {
                ProductCardContent(card: card, fills: fills, send: send, onPreset: { preset in
                    command = SwipeCommand(direction: .yes) {
                        send(QuestionEventYes(quantity: preset.value))
                    }
                })
            }
            .accessibilityElement(children: .contain)
            .accessibilityAction(named: Text("Oui, \(card.quantity.selected.label)")) { answer(.yes) }
            .accessibilityAction(named: Text("Non")) { answer(.no) }
            .accessibilityAction(named: Text("Plus d'options")) { showsOptions = true }
        }
        .padding(.bottom, 18)
    }

    /// « ← glisser : non » … « glisser : oui → ».
    private var swipeHints: some View {
        HStack {
            HStack(spacing: 4) {
                IconView(.arrowLeft, size: 14)
                Text("glisser\u{00A0}: non")
            }
            Spacer()
            HStack(spacing: 4) {
                Text("glisser\u{00A0}: oui")
                IconView(.arrowRight, size: 14)
            }
        }
        .textStyle(.captionStrong)
        .foregroundStyle(Palette.inkSoft)
        .padding(.horizontal, 6)
        .accessibilityHidden(true)
    }

    private func answer(_ direction: SwipeDirection) {
        switch direction {
        case .yes: send(QuestionEventYes(quantity: nil))
        case .no: send(QuestionEventNo.shared)
        }
    }
}

/// The card itself: category and chip, name, question, reason, unit, presets and the free stepper.
struct ProductCardContent: View {
    let card: ProductCardUi
    /// Fills the available height (the presets go to the bottom); off when the card scrolls.
    var fills = true
    let send: (any QuestionEvent) -> Void
    let onPreset: (QuantityChoice) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header

            Text(verbatim: card.name)
                .textStyle(.productTitle)
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 18)
            Text("Il vous faut \(card.needPhrase)\u{00A0}?")
                .textStyle(.question)
                .foregroundStyle(Palette.inkSoft)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 10)
            if let reason = card.reason {
                ReasonHint(reason: reason)
                    .padding(.top, 14)
            }

            if fills {
                Spacer(minLength: 20)
            }

            if card.units.count > 1 {
                UnitPicker(units: card.units) { send(QuestionEventSelectUnit(unit: $0)) }
                    .padding(.top, fills ? 0 : 20)
            }

            if !card.presets.isEmpty {
                LazyVGrid(columns: presetColumns, spacing: 10) {
                    ForEach(card.presets, id: \.label) { preset in
                        let selected = preset.value == card.quantity.selected.value
                        Button {
                            onPreset(preset)
                        } label: {
                            ChoiceTileLabel(
                                value: Text(verbatim: preset.label),
                                note: selected && card.isSameAsLastTime ? Text("habituel") : nil,
                                selected: selected
                            )
                        }
                        .buttonStyle(ChoiceTileStyle(selected: selected))
                        .accessibilityLabel(Text("Oui, \(preset.label)"))
                    }
                }
                .padding(.top, 12)
            }

            // « habituel » follows the quantity here when no preset shows it (as on Android).
            FreeStepper(
                stepper: card.quantity,
                usual: card.isSameAsLastTime && !card.presets.contains { $0.value == card.quantity.selected.value }
            ) { increment in
                send(QuestionEventStepQuantity(increment: increment))
            }
            .padding(.top, 10)
        }
        .padding(EdgeInsets(top: 24, leading: 22, bottom: 22, trailing: 22))
        .frame(maxWidth: .infinity, maxHeight: fills ? .infinity : nil, alignment: .topLeading)
        .cardBackground(Palette.surface, radius: Radius.card, border: Palette.line, elevation: .card)
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 8) {
            if let category = card.categoryName {
                Overline(text: Text(verbatim: category))
            }
            Spacer(minLength: 0)
            chip
        }
        .frame(minHeight: 26)
    }

    /// `WATCH` → « À surveiller », `RADAR` → « Radar », `EXPLORATION` → « À découvrir », otherwise « Essentiel » for a
    /// staple (same chips as Android).
    @ViewBuilder
    private var chip: some View {
        switch card.kind {
        case .watch:
            Chip(text: Text("À surveiller"), tone: .tomato)
        case .radar:
            Chip(text: Text("Radar"))
        case .exploration:
            Chip(text: Text("À découvrir"))
        case .standard:
            if card.reason?.kind == .staple {
                Chip(text: Text("Essentiel"))
            }
        }
    }

    private var presetColumns: [GridItem] {
        // Three presets on one row as in the mockup (a long label scales down or wraps); four on two rows.
        let presets = card.presets.count
        let count = dynamicTypeSize.isAccessibilitySize ? 1 : (presets == 4 ? 2 : min(presets, 3))
        return Array(repeating: GridItem(.flexible(), spacing: 10), count: count)
    }
}

/// Sélecteur d'unité: Surface douce track, the selected unit on a white pill.
private struct UnitPicker: View {
    let units: [UnitOptionUi]
    let onSelect: (String) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(units, id: \.unit) { option in
                Button {
                    if !option.selected { onSelect(option.unit) }
                } label: {
                    Text(verbatim: option.label)
                        .font(.custom(option.selected ? FontName.figtreeBold : FontName.figtreeSemiBold, size: 15, relativeTo: .subheadline))
                        .foregroundStyle(option.selected ? Palette.ink : Palette.inkSoft)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .padding(.horizontal, 6)
                        .frame(maxWidth: .infinity, minHeight: 40)
                        .background {
                            if option.selected {
                                RoundedRectangle(cornerRadius: 11)
                                    .fill(Palette.surface)
                                    .shadow(color: Palette.ink.opacity(0.12), radius: 1.5, y: 1)
                            }
                        }
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(option.selected ? .isSelected : [])
            }
        }
        .padding(4)
        .background(Palette.surfaceSoft, in: .rect(cornerRadius: 14))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Unité")
        .animation(.snappy(duration: 0.2), value: units.first(where: \.selected)?.unit)
    }
}

/// « Plus d'options » (mockup `Options`): the nuanced answers of a card, in two groups.
struct NuancedAnswersSheet: View {
    let productName: String
    let onAnswer: (NuancedAnswer) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(verbatim: productName)
                            .textStyle(.sheetTitle)
                            .foregroundStyle(Palette.ink)
                            .accessibilityAddTraits(.isHeader)
                        Text("Une réponse plus précise nous aide à mieux viser.")
                            .textStyle(.secondary)
                            .foregroundStyle(Palette.inkSoft)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                    IconButton(icon: .close, label: Text("Fermer"), variant: .soft) { dismiss() }
                }
                .padding(EdgeInsets(top: 14, leading: 6, bottom: 12, trailing: 6))

                group(Text("Il m'en reste"), [
                    (.littleLeft, .stock, .littleLeft, "Il en reste un peu", "On vous la repose la prochaine fois"),
                    (.enoughForAWeek, .stock, .calendarWeek, "J'en ai pour une semaine", "On ne demande plus avant 7 jours"),
                    (.enoughForAMonth, .stock, .calendarMonth, "J'en ai pour un mois", "On ne demande plus avant 30 jours"),
                ])
                group(Text("Pas maintenant"), [
                    (.later, .pause, .clock, "Plus tard", "Reposée à la fin du questionnaire"),
                    (.notThisTime, .pause, .minus, "Pas cette fois", "Sans changer vos habitudes"),
                    (.never, .stop, .nosign, "Je n'en achète plus", "Ne plus jamais demander"),
                ])
            }
            .padding(.horizontal, Spacing.block)
            .padding(.top, 14)
            .padding(.bottom, 16)
        }
        .background(Palette.surface)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(Radius.sheet)
        .presentationBackground(Palette.surface)
    }

    private func group(
        _ title: Text,
        _ options: [(NuancedAnswer, ResponseOption.Tone, Icon, LocalizedStringKey, LocalizedStringKey)]
    ) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Overline(text: title)
                .padding(.horizontal, 6)
                .padding(.top, 10)
            VStack(spacing: 0) {
                ForEach(Array(options.enumerated()), id: \.offset) { index, option in
                    ResponseOption(tone: option.1, icon: option.2, label: Text(option.3), hint: Text(option.4)) {
                        onAnswer(option.0)
                    }
                    if index < options.count - 1 {
                        Rectangle().fill(Palette.lineLight).frame(height: 1)
                    }
                }
            }
            .background(Palette.canvas, in: .rect(cornerRadius: Radius.block))
            .clipShape(.rect(cornerRadius: Radius.block))
        }
    }
}

/// Option de réponse: a 40 tile (radius 12) in the tone's colors, the label (17 / 600) and its hint (Légende).
struct ResponseOption: View {
    enum Tone {
        /// Basilic clair / Basilic: there is some left.
        case stock
        /// `#EEF0EA` / Encre douce: not now.
        case pause
        /// Tomate clair / Tomate texte: never again.
        case stop
    }

    let tone: Tone
    let icon: Icon
    let label: Text
    let hint: Text
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                IconView(icon, size: 20)
                    .foregroundStyle(ink)
                    .frame(width: 40, height: 40)
                    .background(tint, in: .rect(cornerRadius: Radius.optionTile))
                VStack(alignment: .leading, spacing: 2) {
                    label
                        .textStyle(.listRow)
                        .foregroundStyle(tone == .stop ? Palette.tomatoText : Palette.ink)
                    hint
                        .textStyle(.caption)
                        .foregroundStyle(Palette.inkSoft)
                }
                .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: Target.minimum, alignment: .leading)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }

    private var tint: Color {
        switch tone {
        case .stock: Palette.basilLight
        case .pause: Palette.pause
        case .stop: Palette.tomatoLight
        }
    }

    private var ink: Color {
        switch tone {
        case .stock: Palette.basil
        case .pause: Palette.inkSoft
        case .stop: Palette.tomatoText
        }
    }
}
