import Shared
import SwiftUI

/// « Liste »: sections in store order, check, swipe to delete with « Annuler », add by search or free text.
struct ShoppingListScreen: View {
    let model: ShoppingListModel
    let onStartSession: () -> Void

    var body: some View {
        ShoppingListContent(state: model.state, send: model.send, onStartSession: onStartSession)
    }
}

/// Stateless rendering of a `ShoppingListUiState` (previews and Debug demo screens use fake states).
struct ShoppingListContent: View {
    let state: any ShoppingListUiState
    let send: (any ShoppingListEvent) -> Void
    var onStartSession: () -> Void = {}

    @State private var query = ""
    @State private var confirmsCompletion = false

    var body: some View {
        content
            .navigationTitle("Ma liste")
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: Text("Ajouter un produit"))
            .autocorrectionDisabled()
            .onSubmit(of: .search, addFromQuery)
            .onChange(of: query) { _, newValue in
                send(ShoppingListEventSearch(query: newValue))
            }
            .toolbar {
                if let loaded = state as? ShoppingListUiStateLoaded, !loaded.isEmpty {
                    ToolbarItem(placement: .primaryAction) {
                        Button("Courses terminées", systemImage: "checkmark.seal") {
                            confirmsCompletion = true
                        }
                    }
                }
            }
            .confirmationDialog("Courses terminées\u{00A0}?", isPresented: $confirmsCompletion, titleVisibility: .visible) {
                Button("Terminer les courses") {
                    send(ShoppingListEventCompleteList.shared)
                }
            } message: {
                Text("Les articles cochés sont comptés comme achetés. Les autres restent sur la liste suivante.")
            }
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            ProgressView("Chargement de la liste…")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .error:
            ContentUnavailableView {
                Label("Liste indisponible", systemImage: "exclamationmark.triangle")
            } description: {
                Text("La liste n'a pas pu être lue sur l'appareil.")
            } actions: {
                Button("Réessayer") { send(ShoppingListEventRetry.shared) }
                    .buttonStyle(.borderedProminent)
            }
        case .loaded(let loaded):
            LoadedListView(loaded: loaded, query: query, send: send, onAddFreeText: addFreeText, onAddProduct: addProduct, onStartSession: onStartSession)
        }
    }

    private func addProduct(_ productId: String) {
        send(ShoppingListEventAddProduct(productId: productId))
        query = ""
    }

    private func addFreeText() {
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        send(ShoppingListEventAddFreeText(text: text))
        query = ""
    }

    /// Return in the search field: the first product found, otherwise the text itself.
    private func addFromQuery() {
        if let first = (state as? ShoppingListUiStateLoaded)?.searchResults.first {
            addProduct(first.productId)
        } else {
            addFreeText()
        }
    }
}

private struct LoadedListView: View {
    let loaded: ShoppingListUiStateLoaded
    let query: String
    let send: (any ShoppingListEvent) -> Void
    let onAddFreeText: () -> Void
    let onAddProduct: (String) -> Void
    let onStartSession: () -> Void

    @Environment(\.isSearching) private var isSearching

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        Group {
            if !trimmedQuery.isEmpty {
                searchResults
            } else if loaded.isEmpty {
                ContentUnavailableView {
                    Label("Votre liste est vide", systemImage: "cart")
                } description: {
                    Text("Répondez à quelques questions, ou ajoutez un produit avec la recherche.")
                } actions: {
                    Button("Faire le point", action: onStartSession)
                        .buttonStyle(.borderedProminent)
                        .controlSize(.large)
                }
            } else {
                sections
            }
        }
        .overlay(alignment: .bottom) {
            if let undo = loaded.undo {
                UndoBanner(undo: undo, onUndo: { send(ShoppingListEventUndo.shared) }, onDismiss: { send(ShoppingListEventDismissUndo.shared) })
                    .padding()
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: loaded.undo)
    }

    private var sections: some View {
        List {
            if loaded.error != nil {
                Label("La dernière action n'a pas pu être enregistrée.", systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
            }
            Section {
                EmptyView()
            } header: {
                remainingHeader
            }
            ForEach(loaded.sections, id: \.categoryId) { section in
                Section {
                    ForEach(section.items, id: \.id) { item in
                        ListItemRow(item: item, send: send)
                    }
                } header: {
                    if let title = section.title {
                        Text(verbatim: title)
                    } else {
                        Text("Autres")
                    }
                }
            }
            // Room for the undo banner over the last rows.
            Color.clear.frame(height: 72).listRowBackground(Color.clear)
        }
        .listStyle(.insetGrouped)
        .animation(.snappy, value: loaded.sections)
    }

    private var remainingHeader: some View {
        Group {
            if loaded.remainingCount == 0 {
                Text("Tout est coché. Bonnes courses\u{00A0}!")
            } else {
                Text("\(Int(loaded.remainingCount)) articles à acheter sur \(Int(loaded.itemCount))")
            }
        }
        .font(.subheadline)
        .textCase(nil)
    }

    private var searchResults: some View {
        List {
            if !loaded.searchResults.isEmpty {
                Section("Produits") {
                    ForEach(loaded.searchResults, id: \.productId) { result in
                        Button {
                            onAddProduct(result.productId)
                        } label: {
                            HStack {
                                Image(systemName: "plus.circle.fill")
                                    .font(.title3)
                                    .foregroundStyle(.tint)
                                    .accessibilityHidden(true)
                                Text(verbatim: result.name)
                                    .foregroundStyle(.primary)
                                Spacer(minLength: 8)
                                Text(verbatim: result.quantityLabel)
                                    .foregroundStyle(.secondary)
                            }
                            .frame(minHeight: 44)
                            .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text("Ajouter \(result.name), \(result.quantityLabel)"))
                    }
                }
            }
            Section("Autre chose\u{00A0}?") {
                Button(action: onAddFreeText) {
                    Label {
                        Text("Ajouter «\u{00A0}\(trimmedQuery)\u{00A0}»")
                            .foregroundStyle(.primary)
                    } icon: {
                        Image(systemName: "text.badge.plus")
                            .foregroundStyle(.tint)
                    }
                    .frame(minHeight: 44)
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
        .listStyle(.insetGrouped)
    }
}

private struct ListItemRow: View {
    let item: ListItemUi
    let send: (any ShoppingListEvent) -> Void

    var body: some View {
        Button {
            send(ShoppingListEventToggleChecked(itemId: item.id))
        } label: {
            HStack(spacing: 12) {
                Image(systemName: item.checked ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(item.checked ? Color.accentColor : Color.secondary)
                    .contentTransition(.symbolEffect(.replace))
                Text(verbatim: item.name)
                    .strikethrough(item.checked)
                    .foregroundStyle(item.checked ? .secondary : .primary)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 8)
                if let quantity = item.quantityLabel {
                    Text(verbatim: quantity)
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                }
            }
            .frame(minHeight: 44)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button("Supprimer", systemImage: "trash", role: .destructive) {
                send(ShoppingListEventDelete(itemId: item.id))
            }
        }
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button(item.checked ? "Décocher" : "Cocher", systemImage: item.checked ? "circle" : "checkmark.circle") {
                send(ShoppingListEventToggleChecked(itemId: item.id))
            }
            .tint(.accentColor)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityLabel)
        .accessibilityValue(item.checked ? Text("Coché") : Text("Non coché"))
        .accessibilityAddTraits(item.checked ? [.isButton, .isSelected] : .isButton)
        .accessibilityAction(named: Text("Supprimer")) {
            send(ShoppingListEventDelete(itemId: item.id))
        }
    }

    private var accessibilityLabel: Text {
        if let quantity = item.quantityLabel {
            return Text(verbatim: "\(item.name), \(quantity)")
        }
        return Text(verbatim: item.name)
    }
}

/// The snackbar of the last check or delete, with « Annuler ». It goes away after a few seconds, except with
/// VoiceOver on (the user decides when).
struct UndoBanner: View {
    let undo: UndoUi
    let onUndo: () -> Void
    let onDismiss: () -> Void

    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOverEnabled

    var body: some View {
        HStack(spacing: 12) {
            message
                .font(.subheadline.weight(.medium))
                .lineLimit(2)
            Spacer(minLength: 8)
            Button("Annuler", action: onUndo)
                .font(.subheadline.weight(.bold))
                .frame(minHeight: 44)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(.regularMaterial, in: .rect(cornerRadius: 14))
        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
        .accessibilityElement(children: .combine)
        .task(id: undo) {
            // A check already reads its new value; a delete makes the row vanish, so it is announced.
            if undo.kind == .deleted {
                AccessibilityNotification.Announcement(String(localized: "«\u{00A0}\(undo.itemName)\u{00A0}» supprimé")).post()
            }
            guard !voiceOverEnabled else { return }
            try? await Task.sleep(for: .seconds(8))
            guard !Task.isCancelled else { return }
            onDismiss()
        }
    }

    private var message: Text {
        switch undo.kind {
        case .deleted: Text("«\u{00A0}\(undo.itemName)\u{00A0}» supprimé")
        case .checked: Text("«\u{00A0}\(undo.itemName)\u{00A0}» coché")
        case .unchecked: Text("«\u{00A0}\(undo.itemName)\u{00A0}» décoché")
        }
    }

}
