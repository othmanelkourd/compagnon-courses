import Shared
import SwiftUI

/// Recap before writing the list, with « Autre chose ? » (catalog search). « Ajouter à ma liste » writes it.
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
        List {
            VStack(alignment: .leading, spacing: 6) {
                Text("Récapitulatif")
                    .font(.largeTitle.bold())
                    .accessibilityAddTraits(.isHeader)
                Text("\(review.items.count) articles à ajouter à votre liste")
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 8, leading: 4, bottom: 0, trailing: 4))
            Section {
                if review.items.isEmpty {
                    Text("Aucun article pour l'instant.")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(review.items, id: \.productId) { item in
                        HStack {
                            Text(verbatim: item.name)
                            Spacer(minLength: 8)
                            Text(verbatim: item.quantity.label)
                                .foregroundStyle(.secondary)
                                .monospacedDigit()
                        }
                        .frame(minHeight: 36)
                        .accessibilityElement(children: .combine)
                    }
                }
            }

            Section("Autre chose\u{00A0}?") {
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                    TextField("Rechercher un produit", text: $query)
                        .focused($searchFocused)
                        .submitLabel(.search)
                        .autocorrectionDisabled()
                        .frame(minHeight: 36)
                    if !query.isEmpty {
                        Button("Effacer", systemImage: "xmark.circle.fill") {
                            query = ""
                        }
                        .labelStyle(.iconOnly)
                        .foregroundStyle(.secondary)
                        .buttonStyle(.plain)
                    }
                }
                ForEach(review.searchResults, id: \.productId) { result in
                    Button {
                        send(QuestionEventAddOther(productId: result.productId))
                        query = ""
                    } label: {
                        HStack {
                            Image(systemName: "plus.circle.fill")
                                .font(.title3)
                                .foregroundStyle(.tint)
                                .accessibilityHidden(true)
                            Text(verbatim: result.name)
                                .foregroundStyle(.primary)
                            Spacer(minLength: 8)
                            Text(verbatim: result.quantity.label)
                                .foregroundStyle(.secondary)
                        }
                        .frame(minHeight: 36)
                        .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text("Ajouter \(result.name), \(result.quantity.label)"))
                }
                if !query.trimmingCharacters(in: .whitespaces).isEmpty && review.searchResults.isEmpty {
                    Text("Aucun produit trouvé. Vous pourrez l'ajouter en texte libre dans la liste.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: query) { _, newValue in
            send(QuestionEventSearch(query: newValue))
        }
        .safeAreaInset(edge: .bottom) {
            Button {
                send(QuestionEventConfirm.shared)
            } label: {
                if review.items.isEmpty {
                    Text("Terminer sans rien ajouter")
                } else {
                    Label("Ajouter à ma liste", systemImage: "cart.badge.plus")
                }
            }
            .buttonStyle(.big(review.items.isEmpty ? .secondary : .primary))
            .padding()
            .background(.bar)
        }
    }
}

/// The end of a session: what happened, then the list or the home screen.
struct SessionDoneView: View {
    let done: QuestionUiStateDone
    let onShowList: () -> Void
    let onClose: () -> Void

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: done.abandoned ? "xmark.circle" : "checkmark.circle.fill")
                .font(.system(size: 80))
                .foregroundStyle(done.abandoned ? Color.secondary : Color.green)
                .symbolEffect(.bounce, value: done.itemsAdded)
                .accessibilityHidden(true)
            message
                .font(.title.bold())
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            if !done.abandoned && done.itemsAdded > 0 {
                Text("Cochez-les pendant vos courses.")
                    .font(.title3)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
            VStack(spacing: 12) {
                Button(action: onShowList) {
                    Label("Voir ma liste", systemImage: "cart")
                }
                .buttonStyle(.big)
                Button(action: onClose) {
                    Text("Retour à l'accueil")
                }
                .buttonStyle(.big(.secondary))
            }
        }
        .padding()
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
}

/// A session error, with « Réessayer » when it may work and « Recommencer » always.
struct SessionErrorView: View {
    let error: QuestionUiStateError
    let send: (any QuestionEvent) -> Void

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: icon)
        } description: {
            description
        } actions: {
            VStack(spacing: 12) {
                if error.retryable {
                    Button("Réessayer") { send(QuestionEventRetry.shared) }
                        .buttonStyle(.big)
                }
                Button("Recommencer") { send(QuestionEventStartOver.shared) }
                    .buttonStyle(.big(.secondary))
            }
            .padding(.horizontal)
        }
    }

    private var title: LocalizedStringKey {
        switch error.kind {
        case .noCatalog: "Catalogue indisponible"
        case .planningFailed: "Questions indisponibles"
        case .storageFailed: "Enregistrement impossible"
        }
    }

    private var icon: String {
        switch error.kind {
        case .noCatalog: "wifi.slash"
        case .planningFailed, .storageFailed: "exclamationmark.triangle"
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
