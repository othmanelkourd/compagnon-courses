import Shared
import SwiftUI

/// Recap before writing the list (mockup `Review`), with « Autre chose ? » (catalog search). « Ajouter à ma liste »
/// writes it; « Revenir aux questions » undoes the last answer. Items are grouped by where they come from (the
/// state gives no category). Lines have no « Retirer » button: there is no event to remove a draft item.
struct ReviewView: View {
    let review: QuestionUiStateReview
    let send: (any QuestionEvent) -> Void

    @State private var query: String
    @FocusState private var searchFocused: Bool

    init(review: QuestionUiStateReview, send: @escaping (any QuestionEvent) -> Void) {
        self.review = review
        self.send = send
        _query = State(initialValue: review.searchQuery)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 6) {
                    if review.progress.answered > 0 {
                        HStack(spacing: 8) {
                            IconView(.check, size: 18, weight: .bold)
                            Text("\(Int(review.progress.answered)) questions")
                        }
                        .textStyle(.counter)
                        .foregroundStyle(Palette.basil)
                    }
                    title
                        .textStyle(.screenTitle)
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                }
                .padding(.horizontal, Spacing.screen)
                .padding(.top, 16)

                InputField(
                    icon: .search,
                    placeholder: Text("Autre chose\u{00A0}? Ex.\u{00A0}: piles, persil…"),
                    text: $query,
                    submitLabel: .search,
                    focus: $searchFocused
                )
                .accessibilityLabel("Autre chose\u{00A0}?")
                .padding(.horizontal, Spacing.screen)
                .padding(.top, 18)

                VStack(alignment: .leading, spacing: Spacing.betweenBlocks) {
                    if !trimmedQuery.isEmpty {
                        searchResults
                    }
                    ForEach(groups, id: \.origin) { group in
                        BlockSection(title: originTitle(group.origin)) {
                            ListBlock(data: group.items, id: \.productId) { item in
                                ReviewRow(item: item)
                            }
                        }
                    }
                }
                .padding(.horizontal, Spacing.block)
                .padding(.top, 18)
            }
            .padding(.bottom, 16)
        }
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: query) { _, newValue in
            send(QuestionEventSearch(query: newValue))
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 10) {
                Button {
                    send(QuestionEventConfirm.shared)
                } label: {
                    if review.items.isEmpty {
                        Text("Terminer sans rien ajouter")
                    } else {
                        Text("Ajouter à ma liste")
                    }
                }
                .buttonStyle(.primary)

                Button {
                    send(QuestionEventUndo.shared)
                } label: {
                    ButtonLabel("Revenir aux questions", icon: .undo, iconSize: 18)
                }
                .buttonStyle(.text)
                .disabled(!review.canUndo)
                .accessibilityHint("Revient à la question précédente")
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.top, 24)
            .padding(.bottom, 4)
            .background { BottomFade() }
        }
    }

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var title: Text {
        if review.items.isEmpty {
            return Text("Rien à ajouter pour l'instant")
        }
        return Text("\(review.items.count) articles à ajouter")
    }

    @ViewBuilder
    private var searchResults: some View {
        if review.searchResults.isEmpty {
            Text("Aucun produit pour «\u{00A0}\(trimmedQuery)\u{00A0}»")
                .textStyle(.help)
                .foregroundStyle(Palette.inkSoft)
                .padding(.horizontal, 6)
        } else {
            ListBlock(data: review.searchResults, id: \.productId) { result in
                Button {
                    send(QuestionEventAddOther(productId: result.productId))
                    query = ""
                } label: {
                    HStack(spacing: 12) {
                        IconView(.plus, size: 20, weight: .bold)
                            .foregroundStyle(Palette.basil)
                        Text(verbatim: result.name)
                            .textStyle(.listRow)
                            .foregroundStyle(Palette.ink)
                            .multilineTextAlignment(.leading)
                        Spacer(minLength: 8)
                        Text(verbatim: result.quantity.label)
                            .textStyle(.secondary)
                            .foregroundStyle(Palette.inkSoft)
                    }
                    .padding(.horizontal, 16)
                    .frame(minHeight: 56)
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Ajouter \(result.name)"))
                .accessibilityValue(Text(verbatim: result.quantity.label))
            }
        }
    }

    private struct OriginGroup {
        let origin: ItemOrigin
        let items: [ReviewItemUi]
    }

    private var groups: [OriginGroup] {
        let order: [ItemOrigin] = [.suggested, .card, .gate, .other]
        return order.compactMap { origin in
            let items = review.items.filter { $0.origin == origin }
            return items.isEmpty ? nil : OriginGroup(origin: origin, items: items)
        }
    }

    private func originTitle(_ origin: ItemOrigin) -> Text {
        switch origin {
        case .suggested: Text("Probablement nécessaire")
        case .card: Text("Question")
        case .gate: Text("Rayon")
        case .other: Text("Ajouté à la main")
        }
    }
}

/// Ligne récapitulatif: name and quantity.
private struct ReviewRow: View {
    let item: ReviewItemUi

    var body: some View {
        HStack(spacing: 12) {
            Text(verbatim: item.name)
                .textStyle(.listRow)
                .foregroundStyle(Palette.ink)
            Spacer(minLength: 8)
            Text(verbatim: item.quantity.label)
                .textStyle(.secondary)
                .foregroundStyle(Palette.inkSoft)
                .monospacedDigit()
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 56)
        .accessibilityElement(children: .combine)
    }
}

/// The end of a session: what happened, then the list or the home screen.
struct SessionDoneView: View {
    let done: QuestionUiStateDone
    let onShowList: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            ZStack {
                Circle().fill(done.abandoned ? Palette.pause : Palette.basilLight)
                IconView(done.abandoned ? .close : .check, size: 40, weight: .bold)
                    .foregroundStyle(done.abandoned ? Palette.inkSoft : Palette.basil)
            }
            .frame(width: 88, height: 88)
            .symbolEffect(.bounce, value: done.itemsAdded)
            .accessibilityHidden(true)
            message
                .textStyle(.screenTitle)
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 20)
            detail
                .textStyle(.body)
                .foregroundStyle(Palette.inkSoft)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            Spacer()
            VStack(spacing: 10) {
                Button(action: onShowList) {
                    ButtonLabel("Voir ma liste", icon: .basket)
                }
                .buttonStyle(.primary)
                Button(action: onClose) {
                    Text("Retour à l'accueil")
                }
                .buttonStyle(.secondary)
            }
        }
        .padding(.horizontal, Spacing.screen)
        .padding(.top, Spacing.top)
        .padding(.bottom, 4)
    }

    private var message: Text {
        if done.abandoned {
            return Text("Séance abandonnée")
        }
        if done.itemsAdded == 0 {
            return Text("Rien à ajouter cette fois")
        }
        return Text("\(Int(done.itemsAdded)) articles ajoutés à votre liste")
    }

    private var detail: Text {
        if done.abandoned {
            return Text("Rien n'a été ajouté à votre liste.")
        }
        if done.itemsAdded == 0 {
            return Text("Vos réponses sont enregistrées pour les prochaines séances.")
        }
        return Text("Cochez-les pendant vos courses.")
    }
}

/// A session error, with « Réessayer » when it may work and « Recommencer » always.
struct SessionErrorView: View {
    let error: QuestionUiStateError
    let send: (any QuestionEvent) -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            IconView(.info, size: 40, weight: .medium)
                .foregroundStyle(Palette.tomatoText)
                .frame(width: 88, height: 88)
                .background(Palette.tomatoLight, in: .circle)
            Text(title)
                .textStyle(.screenTitle)
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 20)
            description
                .textStyle(.body)
                .foregroundStyle(Palette.inkSoft)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            Spacer()
            VStack(spacing: 10) {
                if error.retryable {
                    Button("Réessayer") { send(QuestionEventRetry.shared) }
                        .buttonStyle(.primary)
                }
                Button("Recommencer") { send(QuestionEventStartOver.shared) }
                    .buttonStyle(.secondary)
            }
        }
        .padding(.horizontal, Spacing.screen)
        .padding(.bottom, 4)
    }

    private var title: LocalizedStringKey {
        switch error.kind {
        case .noCatalog: "Catalogue indisponible"
        case .planningFailed: "Questions indisponibles"
        case .storageFailed: "Enregistrement impossible"
        }
    }

    private var description: Text {
        switch error.kind {
        case .noCatalog: Text("Le catalogue n'est pas encore sur l'appareil. Connectez-vous à internet pour le télécharger.")
        case .planningFailed: Text("Les questions n'ont pas pu être préparées.")
        case .storageFailed: Text("La séance n'a pas pu être enregistrée sur l'appareil.")
        }
    }
}
