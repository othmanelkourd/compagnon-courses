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
                // The blocks come in one after the other (motion 2).
                VStack(alignment: .leading, spacing: 20) {
                    header
                        .cascade(0)
                    StartSessionCard(action: onStartSession)
                        .cascade(1)
                    MyListCard(state: listState, action: onOpenList)
                        .cascade(2)
                    HouseholdCard(summary: household, action: onOpenHousehold)
                        .cascade(3)
                    Spacer(minLength: 12)
                    CatalogStatusFooter(sync: catalogSync, busy: catalogBusy, onRefresh: onRefreshCatalog)
                        .cascade(4)
                }
                .padding(.horizontal, Spacing.screen)
                .padding(.top, Spacing.top + 8)
                .padding(.bottom, 12)
                .frame(minHeight: proxy.size.height, alignment: .top)
            }
        }
        .background {
            // Two halos: top right, and a fainter one bottom left (mockup `Main`).
            HaloBackground(spots: [
                HaloSpot(alignment: .topTrailing, x: 150, y: -170, size: 440),
                HaloSpot(alignment: .bottomLeading, x: -180, y: 200, size: 420, opacity: 0.7),
            ])
        }
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

/// The main action: a large Basilic card « Faire le point », with its leaf pattern, a translucent disc and a basket
/// of 3D products in the bottom right corner (mockup `Main`). The products step aside at accessibility text sizes.
private struct StartSessionCard: View {
    let action: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let large = dynamicTypeSize.isAccessibilitySize
        Button(action: action) {
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 6) {
                    // Clear of the arrow; the subtitle is clear of the products too.
                    Text("Faire le point")
                        .textStyle(.screenTitle)
                        .padding(.trailing, 40)
                    Text("Quelques questions, et votre liste est prête.")
                        .textStyle(.body)
                        .opacity(0.88)
                        .fixedSize(horizontal: false, vertical: true)
                        .frame(maxWidth: large ? .infinity : 200, alignment: .leading)
                }
                Spacer(minLength: 0)
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: 16) { facts }
                    VStack(alignment: .leading, spacing: 6) { facts }
                }
                .textStyle(.secondaryStrong)
            }
            // At least 212 high with its padding, the facts at the bottom, and no higher than its content needs.
            .frame(maxWidth: .infinity, minHeight: 212 - 48, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            .multilineTextAlignment(.leading)
            .foregroundStyle(Palette.surface)
            .padding(24)
            .background(alignment: .bottomTrailing) {
                if !large {
                    composition
                }
            }
            .background(alignment: .topTrailing) {
                LeafPattern.homeCard
            }
            .overlay(alignment: .topTrailing) {
                IconView(.arrowRight, size: 26)
                    .foregroundStyle(Palette.surface)
                    .padding(.top, 28)
                    .padding(.trailing, 24)
            }
            .background(Palette.basil)
            .clipShape(.rect(cornerRadius: Radius.card))
            .background {
                RoundedRectangle(cornerRadius: Radius.card)
                    .fill(Palette.basil)
                    .elevation(.accentCard)
            }
            .contentShape(.rect(cornerRadius: Radius.card))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityElement(children: .combine)
        .accessibilityHint("Démarre une série de questions pour préparer la liste")
    }

    /// The translucent disc and the basket with a baguette, an apple and a salad (positions of the mockup, from the
    /// bottom right corner); each pops in after the card.
    private var composition: some View {
        ZStack(alignment: .bottomTrailing) {
            Circle()
                .fill(Palette.surface.opacity(0.10))
                .frame(width: 150, height: 150)
                .offset(x: 18, y: 14)
            Illustration(ScreenIllustration.homeSession, size: 126, delay: 0.24)
                .rotationEffect(.degrees(-6))
                .offset(x: 6, y: 4)
            Illustration(ScreenIllustration.decorBaguette, size: 62, delay: 0.32)
                .rotationEffect(.degrees(-24))
                .offset(x: -70, y: -74)
            Illustration(ScreenIllustration.decorApple, size: 50, delay: 0.32)
                .offset(x: -30, y: -92)
            Illustration(ScreenIllustration.decorLeafy, size: 46, delay: 0.32)
                .rotationEffect(.degrees(-12))
                .offset(x: -104, y: -44)
        }
        .accessibilityHidden(true)
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

/// « Ma liste »: its notepad, item count, checked count and a progress bar that fills when the screen appears.
private struct MyListCard: View {
    let state: any ShoppingListUiState
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Illustration(ScreenIllustration.homeList, size: 40, delay: 0.24)
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
                    .rollingNumber(checked)
            }
        } else if state is ShoppingListUiStateError {
            Text("Liste indisponible")
        } else {
            Text("Chargement…")
        }
    }
}

/// « Mon foyer »: its house, the household in a few words (« 4 personnes · Halal · 14 essentiels »), opens « Mon foyer ».
private struct HouseholdCard: View {
    let summary: HouseholdSummaryUi
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Illustration(ScreenIllustration.homeHousehold, size: 40, delay: 0.32)
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
