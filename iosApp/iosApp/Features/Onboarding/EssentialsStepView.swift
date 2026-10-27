import Shared
import SwiftUI

/// Step 3 « Vos essentiels » (mockup `Onboarding-Essentials`): product chips by aisle, the most common ones already
/// checked, « Voir tout (n) », and a search that replaces the aisles while it is filled.
struct EssentialsStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    @State private var query: String
    @FocusState private var searchFocused: Bool

    init(state: OnboardingUiStateReady, send: @escaping (any OnboardingEvent) -> Void) {
        self.state = state
        self.send = send
        _query = State(initialValue: state.essentials.query)
    }

    private var essentials: EssentialsStepUi { state.essentials }

    private var catalogReady: Bool {
        !essentials.catalogLoading && !essentials.catalogMissing
    }

    var body: some View {
        OnboardingStepLayout(fade: true) {
            VStack(alignment: .leading, spacing: 0) {
                OnboardingHeader(
                    title: Text(verbatim: HouseholdTexts.sectionTitle(.essentials)),
                    subtitle: Text("Ce qui ne doit jamais manquer chez vous. Les plus courants sont déjà cochés."),
                    spacing: 8
                )
                .padding(.top, 24)

                if catalogReady {
                    InputField(
                        icon: .search,
                        placeholder: Text("Chercher un produit"),
                        text: $query,
                        submitLabel: .search,
                        focus: $searchFocused
                    )
                    .accessibilityLabel("Chercher un produit")
                    .padding(.top, 16)
                }

                content
                    .padding(.top, 18)
            }
        } bottom: {
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventNext.shared) }) {
                if catalogReady {
                    Text("Continuer · \(Int(essentials.checkedCount)) essentiels")
                } else {
                    Text("Continuer")
                }
            }
        }
        .onChange(of: query) { _, newValue in
            send(OnboardingEventSearchEssentials(query: newValue))
        }
    }

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    @ViewBuilder
    private var content: some View {
        if essentials.catalogLoading {
            HStack(spacing: 12) {
                ProgressView()
                    .tint(Palette.basil)
                Text("Chargement du catalogue…")
                    .textStyle(.body)
                    .foregroundStyle(Palette.inkSoft)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 24)
            .accessibilityElement(children: .combine)
        } else if essentials.catalogMissing {
            VStack(spacing: 16) {
                Text("Le catalogue n'est pas encore sur l'appareil. Connectez-vous à internet, ou passez cette étape.")
                    .textStyle(.bodyCompact)
                    .foregroundStyle(Palette.inkSoft)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                Button("Réessayer") {
                    send(OnboardingEventRetry.shared)
                }
                .buttonStyle(.secondary(height: 48))
            }
            .padding(.top, 16)
        } else if !trimmedQuery.isEmpty {
            if essentials.results.isEmpty {
                Text("Aucun produit pour «\u{00A0}\(trimmedQuery)\u{00A0}»")
                    .textStyle(.help)
                    .foregroundStyle(Palette.inkSoft)
                    .padding(.horizontal, 6)
            } else {
                FlowLayout {
                    ForEach(essentials.results, id: \.productId) { chip in
                        ProductChip(name: chip.name, checked: chip.checked) {
                            send(OnboardingEventToggleEssential(productId: chip.productId))
                        }
                    }
                }
            }
        } else {
            VStack(alignment: .leading, spacing: 18) {
                ForEach(essentials.aisles, id: \.categoryId) { aisle in
                    AisleChips(aisle: aisle, send: send)
                }
            }
        }
    }
}

private struct AisleChips: View {
    let aisle: EssentialsAisleUi
    let send: (any OnboardingEvent) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 8) {
                GroupTitle(text: Text(verbatim: aisle.title))
                    .frame(maxWidth: .infinity, alignment: .leading)
                if aisle.expanded || Int(aisle.total) > aisle.items.count {
                    Button {
                        send(OnboardingEventExpandAisle(categoryId: aisle.categoryId))
                    } label: {
                        Group {
                            if aisle.expanded {
                                Text("Voir moins")
                            } else {
                                Text("Voir tout (\(Int(aisle.total)))")
                            }
                        }
                        .font(.custom(FontName.figtreeSemiBold, size: 14, relativeTo: .subheadline))
                        .foregroundStyle(Palette.basil)
                        .frame(minHeight: 32)
                        .padding(.leading, 8)
                        .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                }
            }
            FlowLayout {
                ForEach(aisle.items, id: \.productId) { chip in
                    ProductChip(name: chip.name, checked: chip.checked) {
                        send(OnboardingEventToggleEssential(productId: chip.productId))
                    }
                }
            }
        }
    }
}
