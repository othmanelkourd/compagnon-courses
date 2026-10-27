import Shared
import SwiftUI

/// Step 4 « Ce que vous rachetez souvent » (mockup `Onboarding-Recurring`): each recurring product with its rhythm
/// (Semaine, 2 semaines, Mois) and a small [✕]; « Ajouter un produit » opens a search inside the block.
struct RecurringStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    @State private var query: String
    @FocusState private var searchFocused: Bool

    init(state: OnboardingUiStateReady, send: @escaping (any OnboardingEvent) -> Void) {
        self.state = state
        self.send = send
        _query = State(initialValue: state.recurring.query)
    }

    private var recurring: RecurringStepUi { state.recurring }

    var body: some View {
        OnboardingStepLayout(fade: true, horizontalPadding: 0) {
            VStack(alignment: .leading, spacing: 0) {
                OnboardingHeader(
                    overline: Text(verbatim: HouseholdTexts.sectionTitle(.rhythm)),
                    title: Text("Ce que vous rachetez souvent"),
                    subtitle: Text("Donnez le rythme\u{00A0}: on vous le proposera au bon moment, ni trop tôt ni trop tard."),
                    spacing: 8
                )
                .padding(.horizontal, Spacing.screen)
                .padding(.top, 24)

                OnboardingBlock {
                    if recurring.rows.isEmpty && !recurring.adding {
                        Text("Aucun produit récurrent pour l'instant.")
                            .textStyle(.help)
                            .foregroundStyle(Palette.inkSoft)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(16)
                        RowSeparator()
                    }
                    ForEach(recurring.rows, id: \.productId) { row in
                        RecurringRow(row: row, send: send)
                        RowSeparator()
                    }
                    if recurring.adding {
                        addSearch
                    } else {
                        Button {
                            send(OnboardingEventAddingRecurring(open: true))
                        } label: {
                            HStack(spacing: 10) {
                                IconView(.plus, size: 20, weight: .bold)
                                Text("Ajouter un produit")
                                    .textStyle(.textButton)
                            }
                            .foregroundStyle(Palette.basil)
                            .padding(.horizontal, 16)
                            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                            .contentShape(.rect)
                        }
                        .buttonStyle(RowPressStyle())
                    }
                }
                .padding(.horizontal, Spacing.block)
                .padding(.top, 18)
            }
        } bottom: {
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventNext.shared) }) {
                Text("Continuer")
            }
        }
        .onChange(of: query) { _, newValue in
            if recurring.adding {
                send(OnboardingEventSearchRecurring(query: newValue))
            }
        }
        .onChange(of: recurring.adding) { _, adding in
            if adding {
                searchFocused = true
            } else {
                query = ""
            }
        }
    }

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var addSearch: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                InputField(
                    icon: .search,
                    placeholder: Text("Chercher un produit"),
                    text: $query,
                    submitLabel: .search,
                    focus: $searchFocused
                )
                .accessibilityLabel("Chercher un produit")
                Button {
                    send(OnboardingEventAddingRecurring(open: false))
                } label: {
                    IconView(.close, size: 18, weight: .bold)
                        .foregroundStyle(Palette.inkSoft)
                        .frame(width: Target.minimum, height: Target.minimum)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Fermer")
            }
            .padding(.leading, 12)
            .padding(.trailing, 6)
            .padding(.vertical, 12)

            if !trimmedQuery.isEmpty {
                if recurring.results.isEmpty {
                    RowSeparator()
                    Text("Aucun produit pour «\u{00A0}\(trimmedQuery)\u{00A0}»")
                        .textStyle(.help)
                        .foregroundStyle(Palette.inkSoft)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                } else {
                    ForEach(recurring.results, id: \.productId) { result in
                        RowSeparator()
                        Button {
                            send(OnboardingEventAddRecurring(productId: result.productId))
                        } label: {
                            HStack(spacing: 12) {
                                IconView(result.checked ? .check : .plus, size: 20, weight: .bold)
                                    .foregroundStyle(Palette.basil)
                                Text(verbatim: result.name)
                                    .textStyle(.listRow)
                                    .foregroundStyle(result.checked ? Palette.inkMuted : Palette.ink)
                                    .multilineTextAlignment(.leading)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 16)
                            .frame(minHeight: 52)
                            .contentShape(.rect)
                        }
                        .buttonStyle(RowPressStyle())
                        .disabled(result.checked)
                        .accessibilityLabel(Text("Ajouter \(result.name)"))
                    }
                }
            }
        }
    }
}

private struct RecurringRow: View {
    let row: RecurringRowUi
    let send: (any OnboardingEvent) -> Void

    private static let cadences: [CadenceChoice] = [.week, .twoWeeks, .month]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text(verbatim: row.name)
                    .font(.custom(FontName.figtreeSemiBold, size: 17, relativeTo: .body))
                    .foregroundStyle(Palette.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    send(OnboardingEventRemoveRecurring(productId: row.productId))
                } label: {
                    IconView(.close, size: 16, weight: .bold)
                        .foregroundStyle(Palette.inkMuted)
                        .frame(width: Target.minimum, height: Target.minimum)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .padding(.vertical, -12)
                .padding(.trailing, -10)
                .accessibilityLabel(Text("Retirer \(row.name)"))
            }
            HStack(spacing: 4) {
                ForEach(Self.cadences, id: \.self) { cadence in
                    let selected = row.cadence == cadence
                    Button {
                        send(OnboardingEventSetCadence(productId: row.productId, cadence: cadence))
                    } label: {
                        Text(verbatim: HouseholdTexts.cadenceLabel(cadence))
                            .font(.custom(selected ? FontName.figtreeBold : FontName.figtreeSemiBold, size: 14, relativeTo: .subheadline))
                            .foregroundStyle(selected ? Palette.ink : Palette.inkSoft)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity, minHeight: 40)
                            .background {
                                if selected {
                                    RoundedRectangle(cornerRadius: 11)
                                        .fill(Palette.surface)
                                        .shadow(color: Palette.ink.opacity(0.12), radius: 1.5, x: 0, y: 1)
                                }
                            }
                            .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(4)
            .background(Palette.surfaceSoft, in: .rect(cornerRadius: Radius.stepperButton))
            .accessibilityElement(children: .contain)
            .accessibilityLabel(Text("Rythme\u{00A0}: \(row.name)"))
            .animation(.snappy(duration: 0.2), value: row.cadence)
        }
        .padding(.leading, 16)
        .padding(.trailing, 14)
        .padding(.vertical, 14)
    }
}

/// Step 4 « Vous faites les courses… » (mockup `Onboarding-Rhythm`): three large tiles (7, 14, 30 days) and
/// « Autre durée », like the meta questions.
struct RhythmStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    private static let tiles: [RhythmChoice] = [.week, .twoWeeks, .month]

    var body: some View {
        let rhythm = state.rhythm
        let days = Int(rhythm.days)
        BigChoiceQuestionView(
            overline: Text(verbatim: HouseholdTexts.sectionTitle(.rhythm)),
            title: Text("Vous faites les courses…"),
            subtitle: Text("C'est la durée proposée à chaque «\u{00A0}faire le point\u{00A0}». Vous pourrez toujours la changer."),
            metrics: .onboarding,
            choices: Self.tiles,
            columns: 3,
            isSelected: { rhythm.choice == $0 },
            accessibilityLabel: { choice in
                Text(verbatim: "\(HouseholdTexts.rhythmLabel(choice)), \(HouseholdTexts.days(Self.days(of: choice)))")
            },
            onSelect: { send(OnboardingEventSelectRhythm(choice: $0)) },
            tileLabel: { choice in
                let selected = rhythm.choice == choice
                VStack(spacing: 4) {
                    Text(verbatim: HouseholdTexts.rhythmLabel(choice))
                        .font(.custom(FontName.figtreeBold, size: 17, relativeTo: .body))
                        .lineLimit(2)
                        .minimumScaleFactor(0.8)
                    Text(verbatim: HouseholdTexts.days(Self.days(of: choice)))
                        .textStyle(.help)
                        .foregroundStyle(selected ? Palette.basilDark : Palette.inkSoft)
                }
            },
            stepper: BoxedStepper(
                text: Text("Autre durée\u{00A0}: \(HouseholdTexts.days(days))"),
                accessibilityLabel: Text("Vous faites les courses…"),
                accessibilityValue: Text(verbatim: HouseholdTexts.days(days)),
                canDecrement: days > 1,
                canIncrement: days < Int(rhythm.maxDays),
                decrementLabel: Text("Un jour de moins"),
                incrementLabel: Text("Un jour de plus"),
                onStep: { send(OnboardingEventStepRhythm(increment: $0)) }
            )
        ) {
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventNext.shared) }) {
                Text("Continuer avec \(HouseholdTexts.days(days))")
            }
        }
    }

    private static func days(of choice: RhythmChoice) -> Int {
        switch choice {
        case .week: 7
        case .twoWeeks: 14
        case .month: 30
        case .other: 0
        }
    }
}
