import Shared
import SwiftUI

/// « Accueil » (mockup `Main`): start a question session, open the list and « Mon foyer », and the catalog status at
/// the bottom. Only data that exists is shown: no first name, no planned shopping day, no statistics tiles.
struct HomeView: View {
    let catalog: CatalogStatusModel
    let list: ShoppingListModel
    let household: HouseholdSummaryUi
    let onStartSession: () -> Void
    let onOpenList: () -> Void
    let onOpenHousehold: () -> Void

    var body: some View {
        HomeContent(
            listState: list.state,
            household: household,
            catalogSync: catalog.sync,
            catalogBusy: catalog.isBusy,
            onStartSession: onStartSession,
            onOpenList: onOpenList,
            onOpenHousehold: onOpenHousehold,
            onRefreshCatalog: catalog.refresh
        )
        .refreshable {
            await catalog.refreshAndWait()
        }
    }
}

/// Stateless rendering of the home screen (previews and Debug demo screens use fake states).
struct HomeContent: View {
    let listState: any ShoppingListUiState
    let household: HouseholdSummaryUi
    let catalogSync: CatalogSyncUiState
    var catalogBusy = false
    let onStartSession: () -> Void
    let onOpenList: () -> Void
    let onOpenHousehold: () -> Void
    var onRefreshCatalog: () -> Void = {}

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    header
                    StartSessionCard(action: onStartSession)
                    MyListCard(state: listState, action: onOpenList)
                    HouseholdCard(summary: household, action: onOpenHousehold)
                    Spacer(minLength: 12)
                    CatalogStatusFooter(sync: catalogSync, busy: catalogBusy, onRefresh: onRefreshCatalog)
                }
                .padding(.horizontal, Spacing.screen)
                .padding(.top, Spacing.top + 8)
                .padding(.bottom, 12)
                .frame(minHeight: proxy.size.height, alignment: .top)
            }
        }
        .background(Palette.canvas)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: Date.now.formatted(.dateTime.weekday(.wide).day().month(.wide)).capitalizedFirstLetter)
                .font(.custom(FontName.figtreeMedium, size: 15, relativeTo: .subheadline))
                .foregroundStyle(Palette.inkSoft)
            Text("Prêt pour les courses\u{00A0}?")
                .textStyle(.screenTitleLarge)
                .foregroundStyle(Palette.ink)
                .accessibilityAddTraits(.isHeader)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

extension String {
    /// « dimanche 25 octobre » → « Dimanche 25 octobre ».
    var capitalizedFirstLetter: String {
        prefix(1).uppercased(with: .current) + dropFirst()
    }
}

/// The main action: a large Basilic card « Faire le point ».
private struct StartSessionCard: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 18) {
                // The arrow sits on the title row, as on Android.
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .center, spacing: 12) {
                        Text("Faire le point")
                            .textStyle(.screenTitle)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        IconView(.arrowRight, size: 26)
                    }
                    Text("Quelques questions, et votre liste est prête.")
                        .textStyle(.body)
                        .opacity(0.88)
                        .fixedSize(horizontal: false, vertical: true)
                }
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 16) { facts }
                    VStack(alignment: .leading, spacing: 6) { facts }
                }
                .textStyle(.secondaryStrong)
            }
            .multilineTextAlignment(.leading)
            .foregroundStyle(Palette.surface)
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
            .cardBackground(Palette.basil, radius: Radius.card, elevation: .accentCard)
            .contentShape(.rect(cornerRadius: Radius.card))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityElement(children: .combine)
        .accessibilityHint("Démarre une série de questions pour préparer la liste")
    }

    @ViewBuilder
    private var facts: some View {
        HStack(spacing: 6) {
            IconView(.clock, size: 18)
            Text("≈ 2 min")
        }
        HStack(spacing: 6) {
            IconView(.list, size: 18)
            Text("12 questions")
        }
    }
}

/// A slight press feedback for the large cards.
private struct PressScaleStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.snappy(duration: 0.15), value: configuration.isPressed)
    }
}

/// « Ma liste »: item count, checked count and a progress bar.
private struct MyListCard: View {
    let state: any ShoppingListUiState
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                IconView(.basket, size: 26, weight: .medium)
                    .foregroundStyle(Palette.basil)
                    .frame(width: 52, height: 52)
                    .background(Palette.basilLight, in: .rect(cornerRadius: 16))
                VStack(alignment: .leading, spacing: 6) {
                    Text("Ma liste")
                        .textStyle(.cardTitle)
                        .foregroundStyle(Palette.ink)
                    summary
                        .textStyle(.secondary)
                        .foregroundStyle(Palette.inkSoft)
                    if let loaded, loaded.itemCount > 0 {
                        ProgressBar(fraction: Double(loaded.itemCount - loaded.remainingCount) / Double(loaded.itemCount))
                            .accessibilityHidden(true)
                    }
                }
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                IconView(.chevronRight, size: 20)
                    .foregroundStyle(Palette.inkMuted)
            }
            .padding(20)
            .background(Palette.surface, in: .rect(cornerRadius: Radius.homeListCard))
            .overlay { RoundedRectangle(cornerRadius: Radius.homeListCard).strokeBorder(Palette.line, lineWidth: 1) }
            .contentShape(.rect(cornerRadius: Radius.homeListCard))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityElement(children: .combine)
    }

    private var loaded: ShoppingListUiStateLoaded? {
        state as? ShoppingListUiStateLoaded
    }

    @ViewBuilder
    private var summary: some View {
        if let loaded {
            if loaded.itemCount == 0 {
                Text("Votre liste est vide")
            } else {
                let checked = Int(loaded.itemCount - loaded.remainingCount)
                let items = String(localized: "\(Int(loaded.itemCount)) articles")
                let checkedText = String(localized: "\(checked) cochés")
                Text(verbatim: "\(items) · \(checkedText)")
            }
        } else if state is ShoppingListUiStateError {
            Text("Liste indisponible")
        } else {
            Text("Chargement…")
        }
    }
}

/// « Mon foyer »: the household in a few words (« 4 personnes · Halal · 14 essentiels »), opens « Mon foyer ».
private struct HouseholdCard: View {
    let summary: HouseholdSummaryUi
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                IconView(.house, size: 24, weight: .medium)
                    .foregroundStyle(Palette.basil)
                    .frame(width: 52, height: 52)
                    .background(Palette.basilLight, in: .rect(cornerRadius: 16))
                VStack(alignment: .leading, spacing: 4) {
                    Text("Mon foyer")
                        .textStyle(.cardTitle)
                        .foregroundStyle(Palette.ink)
                    Text(verbatim: HouseholdTexts.card(summary))
                        .textStyle(.secondary)
                        .foregroundStyle(Palette.inkSoft)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                IconView(.chevronRight, size: 20)
                    .foregroundStyle(Palette.inkMuted)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
            .background(Palette.surface, in: .rect(cornerRadius: Radius.homeListCard))
            .overlay { RoundedRectangle(cornerRadius: Radius.homeListCard).strokeBorder(Palette.line, lineWidth: 1) }
            .contentShape(.rect(cornerRadius: Radius.homeListCard))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityElement(children: .combine)
    }
}
