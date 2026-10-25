import Shared
import SwiftUI

/// One product question, full screen: swipe right = « Oui » (with the stepper's quantity), left = « Non »,
/// duplicated by the buttons below the card. Presets answer « Oui » with their quantity in one tap.
struct ProductCardScreen: View {
    let card: ProductCardUi
    let send: (any QuestionEvent) -> Void

    @State private var command: SwipeCommand?
    @State private var showsOptions = false
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let scrolls = dynamicTypeSize.isAccessibilitySize
        VStack(spacing: 0) {
            Group {
                if scrolls {
                    ScrollView { cardView(dragEnabled: false) }
                } else {
                    cardView(dragEnabled: true)
                        .frame(maxHeight: .infinity, alignment: .center)
                }
            }
            .padding(.horizontal)
            .padding(.top, 8)

            answerBar
        }
        .sheet(isPresented: $showsOptions) {
            NuancedAnswersSheet(productName: card.name) { answer in
                showsOptions = false
                send(QuestionEventNuanced(answer: answer))
            }
        }
    }

    private func cardView(dragEnabled: Bool) -> some View {
        SwipeableCard(command: $command, dragEnabled: dragEnabled, onSwipe: answer) {
            ProductCardContent(card: card, send: send, onPreset: { preset in
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

    private func answer(_ direction: SwipeDirection) {
        switch direction {
        case .yes: send(QuestionEventYes(quantity: nil))
        case .no: send(QuestionEventNo.shared)
        }
    }

    /// « Non » and « Oui » at thumb height, « Plus d'options » under them.
    private var answerBar: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                Button {
                    command = SwipeCommand(direction: .no) { answer(.no) }
                } label: {
                    Label("Non", systemImage: "xmark")
                }
                .buttonStyle(.big(.secondary))
                .accessibilityHint("Passe au produit suivant")

                Button {
                    command = SwipeCommand(direction: .yes) { answer(.yes) }
                } label: {
                    Label("Oui", systemImage: "checkmark")
                }
                .buttonStyle(.big)
                .accessibilityLabel(Text("Oui, \(card.quantity.selected.label)"))
                .accessibilityHint("Ajoute ce produit à la liste")
            }
            Button("Plus d'options") {
                showsOptions = true
            }
            .font(.body.weight(.medium))
            .frame(minHeight: 44)
        }
        .padding(.horizontal)
        .padding(.top, 12)
        .padding(.bottom, 4)
    }
}

/// The card itself: what, why, how much.
struct ProductCardContent: View {
    let card: ProductCardUi
    let send: (any QuestionEvent) -> Void
    let onPreset: (QuantityChoice) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header

            VStack(alignment: .leading, spacing: 8) {
                Text(verbatim: card.name)
                    .font(.largeTitle.bold())
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityAddTraits(.isHeader)
                Text("Il vous faut \(card.needPhrase)\u{00A0}?")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                if let reason = card.reason {
                    ReasonLabel(reason: reason)
                }
            }

            if card.units.count > 1 {
                Picker("Unité", selection: unitSelection) {
                    ForEach(card.units, id: \.unit) { option in
                        Text(verbatim: option.label).tag(option.unit)
                    }
                }
                .pickerStyle(.segmented)
            }

            if !card.presets.isEmpty {
                LazyVGrid(columns: presetColumns, spacing: 10) {
                    ForEach(card.presets, id: \.label) { preset in
                        Button {
                            onPreset(preset)
                        } label: {
                            Text(verbatim: preset.label)
                        }
                        .buttonStyle(.big(preset.value == card.quantity.selected.value ? .selected : .secondary))
                        .accessibilityLabel(Text("Oui, \(preset.label)"))
                    }
                }
            }

            VStack(spacing: 6) {
                QuantityStepperView(stepper: card.quantity) { increment in
                    send(QuestionEventStepQuantity(increment: increment))
                }
                if card.isSameAsLastTime {
                    Text("Comme la dernière fois")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: .rect(cornerRadius: 28))
        .shadow(color: .black.opacity(0.08), radius: 16, y: 6)
    }

    @ViewBuilder
    private var header: some View {
        HStack(spacing: 8) {
            if let category = card.categoryName {
                Text(verbatim: category.uppercased(with: .current))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            if let badge = kindBadge {
                badge
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.accentColor.opacity(0.15), in: .capsule)
                    .foregroundStyle(.tint)
            }
        }
    }

    private var kindBadge: Label<Text, Image>? {
        switch card.kind {
        case .standard: nil
        case .watch: Label("Il en restait un peu", systemImage: "drop.halffull")
        case .radar: Label("Ça fait un moment", systemImage: "clock.arrow.circlepath")
        case .exploration: Label("Suggestion", systemImage: "sparkles")
        }
    }

    private var presetColumns: [GridItem] {
        // Three short presets fit on one row (« 0,5 kg », « 1 kg », « 2 kg »); longer labels get two columns.
        let short = card.presets.allSatisfy { $0.label.count <= 6 }
        let count = dynamicTypeSize.isAccessibilitySize ? 1 : (card.presets.count == 3 && short ? 3 : 2)
        return Array(repeating: GridItem(.flexible(), spacing: 10), count: count)
    }

    private var unitSelection: Binding<String> {
        Binding(
            get: { card.units.first(where: \.selected)?.unit ?? card.units.first?.unit ?? "" },
            set: { send(QuestionEventSelectUnit(unit: $0)) }
        )
    }
}

/// « Plus d'options »: the nuanced answers of a card.
struct NuancedAnswersSheet: View {
    let productName: String
    let onAnswer: (NuancedAnswer) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 10) {
                    option(.littleLeft, "Il en reste un peu", "drop.halffull", detail: "Je le reposerai en priorité la prochaine fois")
                    option(.enoughForAWeek, "J'en ai encore pour ~1 semaine", "calendar", detail: nil)
                    option(.enoughForAMonth, "J'en ai encore pour ~1 mois", "calendar.badge.clock", detail: nil)
                    option(.later, "Plus tard", "arrow.uturn.down", detail: "Je reposerai la question à la fin")
                    option(.notThisTime, "Pas cette fois", "forward", detail: nil)
                    option(.never, "Je n'en achète plus", "nosign", detail: "Je ne poserai plus la question")
                }
                .padding()
            }
            .navigationTitle(Text(verbatim: productName))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fermer") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func option(_ answer: NuancedAnswer, _ title: LocalizedStringKey, _ icon: String, detail: LocalizedStringKey?) -> some View {
        Button {
            onAnswer(answer)
        } label: {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.title3)
                    .frame(width: 32)
                    .foregroundStyle(answer == .never ? Color.red : Color.accentColor)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.body.weight(.semibold))
                    if let detail {
                        Text(detail)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
                .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: .rect(cornerRadius: 14))
            .contentShape(.rect(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }
}
