import Shared
import SwiftUI

/// « Ma liste » (mockup `List`): sections in store order, check, swipe to delete with « Annuler », add by search
/// or free text, share, and « Courses terminées ».
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
    /// Height of the title and the add field, to place the empty list under them.
    @State private var headerHeight: CGFloat = 0
    @FocusState private var fieldFocused: Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(spacing: 0) {
            topBar
                .padding(.horizontal, Spacing.screen)
                .padding(.top, Spacing.top)
            content
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background {
            // The empty list has a halo behind its cart (mockup `List-Empty`).
            HaloBackground(spots: showsEmptyList ? [HaloSpot(alignment: .top, x: 0, y: 250, size: 520, opacity: 1)] : [])
        }
        .toolbar(.hidden, for: .navigationBar)
        .onChange(of: query) { _, newValue in
            send(ShoppingListEventSearch(query: newValue))
        }
        .alert("Courses terminées\u{00A0}?", isPresented: $confirmsCompletion) {
            Button(role: .cancel) {} label: { Text(LocalizedStringResource("dialog.cancel", defaultValue: "Annuler", comment: "Dismisses a confirmation; « Annuler » elsewhere undoes.")) }
            Button("Terminer") {
                send(ShoppingListEventCompleteList.shared)
            }
        } message: {
            Text("Les articles cochés sont enregistrés comme achetés. Les autres restent sur votre prochaine liste.")
        }
    }

    /// [‹] back to « Accueil » and [partager].
    private var topBar: some View {
        HStack {
            IconButton(icon: .chevronLeft, label: Text("Retour à l'accueil")) { dismiss() }
            Spacer()
            // Only what is left to buy is shared: nothing to share once everything is checked (as on Android).
            if let loaded = state as? ShoppingListUiStateLoaded, loaded.remainingCount > 0 {
                ShareLink(item: ShoppingListShareText.text(for: loaded)) {
                    IconView(.share, size: 20)
                }
                .buttonStyle(IconButtonStyle())
                .accessibilityLabel("Partager la liste")
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: state) {
        case .loading:
            VStack(spacing: 16) {
                ProgressView()
                    .controlSize(.large)
                    .tint(Palette.basil)
                Text("Chargement de la liste…")
                    .textStyle(.body)
                    .foregroundStyle(Palette.inkSoft)
            }
        case .error:
            ListMessageView(
                icon: .info,
                title: Text("Liste indisponible"),
                message: Text("La liste n'a pas pu être lue sur l'appareil.")
            ) {
                Button("Réessayer") { send(ShoppingListEventRetry.shared) }
                    .buttonStyle(.primary)
            }
        case .loaded(let loaded):
            loadedList(loaded)
        }
    }

    private func loadedList(_ loaded: ShoppingListUiStateLoaded) -> some View {
        let showsResults = !trimmedQuery.isEmpty
        return List {
            header(loaded)
                .plainListRow(top: 16)
            if loaded.error != nil {
                Text("La modification n'a pas pu être enregistrée")
                    .textStyle(.help)
                    .foregroundStyle(Palette.tomatoText)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Palette.tomatoLight, in: .rect(cornerRadius: Radius.hint))
                    .plainListRow(top: 14, horizontal: Spacing.block)
            }
            if showsResults {
                searchResults(loaded)
            } else if loaded.isEmpty {
                EmptyView()
            } else {
                ForEach(loaded.sections, id: \.categoryId) { section in
                    Overline(text: section.title.map { Text(verbatim: $0) } ?? Text("Autres"))
                        .padding(.horizontal, 6)
                        .padding(.bottom, 6)
                        .plainListRow(top: Spacing.betweenBlocks, horizontal: Spacing.block)
                    ForEach(Array(section.items.enumerated()), id: \.element.id) { index, item in
                        ListItemRow(item: item, send: send)
                            .blockListRow(index: index, count: section.items.count)
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .environment(\.defaultMinListRowHeight, 0)
        .scrollDismissesKeyboard(.interactively)
        // A checked line goes down its section (motion 7); it just takes its place with « Réduire les animations ».
        .animation(reduceMotion ? nil : Motion.standard(), value: loaded.sections)
        .scrollDisabled(loaded.isEmpty && !showsResults)
        .overlay {
            if loaded.isEmpty && !showsResults {
                // Between the add field and « Faire le point », a little higher than the middle.
                VStack(spacing: 0) {
                    Color.clear.frame(height: headerHeight + 16)
                    Spacer(minLength: 16)
                    EmptyListView()
                    Spacer(minLength: 16)
                    Color.clear.frame(height: 32)
                }
                .ignoresSafeArea(.keyboard)
            }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            bottomBar(loaded)
        }
    }

    /// « Ma liste », « n sur N restants », the progress bar and the add field.
    private func header(_ loaded: ShoppingListUiStateLoaded) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .lastTextBaseline) {
                    title
                    Spacer(minLength: 8)
                    remaining(loaded)
                }
                VStack(alignment: .leading, spacing: 4) {
                    title
                    remaining(loaded)
                }
            }
            if loaded.itemCount > 0 {
                ProgressBar(fraction: Double(loaded.itemCount - loaded.remainingCount) / Double(loaded.itemCount))
                    .padding(.top, 10)
                    .accessibilityHidden(true)
            }
            InputField(
                icon: .plus,
                iconColor: Palette.basil,
                placeholder: Text("Ajouter un article"),
                text: $query,
                submitLabel: .done,
                focus: $fieldFocused,
                onSubmit: addFromQuery
            )
            .padding(.top, 16)
        }
        .padding(.horizontal, Spacing.screen)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { headerHeight = $0 }
    }

    private var title: some View {
        Text("Ma liste")
            .textStyle(.screenTitleLarge)
            .foregroundStyle(Palette.ink)
            .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder
    private func remaining(_ loaded: ShoppingListUiStateLoaded) -> some View {
        Group {
            if loaded.isEmpty {
                EmptyView()
            } else if loaded.remainingCount == 0 {
                Text("Tout est coché")
            } else {
                Text("\(Int(loaded.remainingCount)) sur \(Int(loaded.itemCount)) restants")
                    .rollingNumber(Int(loaded.remainingCount))
            }
        }
        .textStyle(.secondaryStrong)
        .foregroundStyle(Palette.inkSoft)
    }

    @ViewBuilder
    private func searchResults(_ loaded: ShoppingListUiStateLoaded) -> some View {
        let rows = loaded.searchResults.map { SearchRow.product($0) } + [SearchRow.freeText(trimmedQuery)]
        Color.clear
            .frame(height: 16)
            .plainListRow()
        ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
            Group {
                switch row {
                case .product(let result):
                    Button {
                        addProduct(result.productId)
                    } label: {
                        AddRowLabel(imageKey: result.imageKey, title: Text(verbatim: result.name), detail: Text(verbatim: result.quantityLabel))
                    }
                    .accessibilityLabel(Text("Ajouter \(result.name)"))
                    .accessibilityValue(Text(verbatim: result.quantityLabel))
                case .freeText(let text):
                    Button(action: addFreeText) {
                        AddRowLabel(imageKey: ScreenIllustration.freeText, title: Text("Ajouter «\u{00A0}\(text)\u{00A0}»"), detail: nil)
                    }
                }
            }
            .buttonStyle(.plain)
            .blockListRow(index: index, count: rows.count)
        }
    }

    /// The snackbar (after a delete only) over « Courses terminées ».
    private func bottomBar(_ loaded: ShoppingListUiStateLoaded) -> some View {
        VStack(spacing: 12) {
            if let undo = loaded.undo, undo.kind == .deleted {
                DeletedSnackbar(
                    undo: undo,
                    onUndo: { send(ShoppingListEventUndo.shared) },
                    onDismiss: { send(ShoppingListEventDismissUndo.shared) }
                )
                .padding(.horizontal, Spacing.block - Spacing.screen)
                .transition(reduceMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity))
            }
            if !loaded.isEmpty {
                Button("Courses terminées") {
                    confirmsCompletion = true
                }
                .buttonStyle(.secondary(basil: true))
            } else if trimmedQuery.isEmpty {
                Button("Faire le point", action: onStartSession)
                    .buttonStyle(.primary)
                    .cascade(5, delay: 0.04)
            }
        }
        .padding(.horizontal, Spacing.screen)
        .padding(.top, 16)
        .padding(.bottom, 4)
        .background { BottomFade() }
        .animation(.snappy, value: loaded.undo)
    }

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var showsEmptyList: Bool {
        (state as? ShoppingListUiStateLoaded)?.isEmpty == true && trimmedQuery.isEmpty
    }

    private func addProduct(_ productId: String) {
        send(ShoppingListEventAddProduct(productId: productId))
        query = ""
    }

    private func addFreeText() {
        let text = trimmedQuery
        guard !text.isEmpty else { return }
        send(ShoppingListEventAddFreeText(text: text))
        query = ""
    }

    /// Return in the field: the first product found, otherwise the text itself.
    private func addFromQuery() {
        if let first = (state as? ShoppingListUiStateLoaded)?.searchResults.first {
            addProduct(first.productId)
        } else {
            addFreeText()
        }
    }
}

private enum SearchRow {
    case product(ProductSuggestionUi)
    case freeText(String)

    var id: String {
        switch self {
        case .product(let result): "product-\(result.productId)"
        case .freeText: "free-text"
        }
    }
}

/// A search result or the free text: its image on a 36 disc (a memo for the free text), name, quantity, « + » in
/// Basilic.
private struct AddRowLabel: View {
    let imageKey: String
    let title: Text
    let detail: Text?

    var body: some View {
        HStack(spacing: 12) {
            IllustrationPlate(key: imageKey, size: 36, imageSize: 26)
            title
                .textStyle(.listRow)
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.leading)
            Spacer(minLength: 8)
            if let detail {
                detail
                    .textStyle(.secondary)
                    .foregroundStyle(Palette.inkSoft)
            }
            IconView(.plus, size: 20, weight: .bold)
                .foregroundStyle(Palette.basil)
        }
        .padding(.leading, 10)
        .padding(.trailing, 16)
        .padding(.vertical, 8)
        .frame(minHeight: 56)
        .contentShape(.rect)
    }
}

/// Ligne produit: square checkbox, the product's image on a 36 disc (faded once checked), name (struck through in
/// Encre discrète when checked, the line drawn from left to right), quantity.
private struct ListItemRow: View {
    let item: ListItemUi
    let send: (any ShoppingListEvent) -> Void

    var body: some View {
        Button {
            send(ShoppingListEventToggleChecked(itemId: item.id))
        } label: {
            HStack(spacing: 10) {
                Checkbox(checked: item.checked, shape: .square)
                IllustrationPlate(key: item.imageKey, size: 36, imageSize: 26, dimmed: item.checked)
                StruckText(text: item.name, struck: item.checked)
                Spacer(minLength: 8)
                if let quantity = item.quantityLabel {
                    Text(verbatim: quantity)
                        .textStyle(.secondary)
                        .foregroundStyle(Palette.inkSoft)
                        .monospacedDigit()
                }
            }
            .padding(.vertical, 4)
            .padding(.leading, 4)
            .padding(.trailing, 16)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
            Button("Supprimer", role: .destructive) {
                send(ShoppingListEventDelete(itemId: item.id))
            }
        }
        // Either way deletes, as on Android (« Annuler » in the snackbar brings the line back).
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            Button("Supprimer", role: .destructive) {
                send(ShoppingListEventDelete(itemId: item.id))
            }
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

/// The snackbar after a delete, with « Annuler ». It goes away after a few seconds, except with VoiceOver on (the
/// user decides when). Checking or unchecking shows none (charter § 4).
private struct DeletedSnackbar: View {
    let undo: UndoUi
    let onUndo: () -> Void
    let onDismiss: () -> Void

    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOverEnabled

    var body: some View {
        Snackbar(message: Text("«\u{00A0}\(undo.itemName)\u{00A0}» supprimé"), actionTitle: Text("Annuler"), action: onUndo)
            .accessibilityElement(children: .combine)
            .task(id: undo) {
                // The row vanishes, so the delete is announced.
                AccessibilityNotification.Announcement(String(localized: "«\u{00A0}\(undo.itemName)\u{00A0}» supprimé")).post()
                guard !voiceOverEnabled else { return }
                try? await Task.sleep(for: .seconds(8))
                guard !Task.isCancelled else { return }
                onDismiss()
            }
    }
}

/// A name crossed out by a line drawn from its leading edge (motion 7): the struck copy of the text is revealed by a
/// growing mask (250 ms), on every line of a long name at once. It fades in with « Réduire les animations ».
private struct StruckText: View {
    let text: String
    let struck: Bool

    @State private var progress: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(text: String, struck: Bool) {
        self.text = text
        self.struck = struck
        _progress = State(initialValue: struck ? 1 : 0)
    }

    var body: some View {
        Text(verbatim: text)
            .textStyle(.listRow)
            .foregroundStyle(struck ? Palette.inkMuted : Palette.ink)
            .multilineTextAlignment(.leading)
            .overlay {
                Text(verbatim: text)
                    .textStyle(.listRow)
                    .strikethrough(true, color: Palette.inkMuted)
                    .foregroundStyle(.clear)
                    .multilineTextAlignment(.leading)
                    .mask(alignment: .leading) {
                        GeometryReader { proxy in
                            Rectangle().frame(width: proxy.size.width * (reduceMotion ? 1 : progress))
                        }
                    }
                    .opacity(reduceMotion ? progress : 1)
                    .accessibilityHidden(true)
            }
            .animation(Motion.standard(), value: struck)
            .onChange(of: struck) { _, struck in
                withAnimation(reduceMotion ? Motion.fade : Motion.standard()) {
                    progress = struck ? 1 : 0
                }
            }
    }
}

/// The empty list (mockup `List-Empty`): the cart on a large Basilic clair disc, the title and the message; « Faire
/// le point » waits at the bottom of the screen.
private struct EmptyListView: View {
    var body: some View {
        VStack(spacing: 0) {
            IllustrationPlate(key: ScreenIllustration.listEmpty, size: 136, imageSize: 92, shadow: true)
                .popIn(delay: 0.04)
                .accessibilityHidden(true)
            VStack(spacing: 10) {
                Text("Votre liste est vide")
                    .textStyle(.screenTitle)
                    .foregroundStyle(Palette.ink)
                    .accessibilityAddTraits(.isHeader)
                    .cascade(3)
                Text("Répondez à quelques questions, ou ajoutez un article ci-dessus.")
                    .textStyle(.body)
                    .foregroundStyle(Palette.inkSoft)
                    .cascade(4)
            }
            .multilineTextAlignment(.center)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.top, 26)
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity)
        .allowsHitTesting(false)
    }
}

/// An empty or failed list: icon, Bricolage title, message and an action.
private struct ListMessageView<Action: View>: View {
    let icon: Icon
    let title: Text
    let message: Text
    @ViewBuilder let action: Action

    var body: some View {
        VStack(spacing: 0) {
            IconView(icon, size: 36, weight: .medium)
                .foregroundStyle(icon == .info ? Palette.tomatoText : Palette.basil)
                .frame(width: 80, height: 80)
                .background(icon == .info ? Palette.tomatoLight : Palette.basilLight, in: .circle)
            title
                .textStyle(.screenTitle)
                .foregroundStyle(Palette.ink)
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 20)
            message
                .textStyle(.body)
                .foregroundStyle(Palette.inkSoft)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
            action
                .padding(.top, 24)
        }
        .padding(.horizontal, Spacing.screen)
    }
}

/// The plain text shared by [partager]: the items left to buy, by section.
enum ShoppingListShareText {
    static func text(for loaded: ShoppingListUiStateLoaded) -> String {
        var lines = [String(localized: "Ma liste")]
        for section in loaded.sections {
            let items = section.items.filter { !$0.checked }
            guard !items.isEmpty else { continue }
            lines.append("")
            lines.append(section.title ?? String(localized: "Autres"))
            for item in items {
                if let quantity = item.quantityLabel {
                    lines.append("• \(item.name) (\(quantity))")
                } else {
                    lines.append("• \(item.name)")
                }
            }
        }
        return lines.joined(separator: "\n")
    }
}

// MARK: - List rows drawn as the charter's blocks

private enum BlockPosition {
    case single, first, middle, last

    init(index: Int, count: Int) {
        switch (index, count) {
        case (_, 1): self = .single
        case (0, _): self = .first
        case (count - 1, _): self = .last
        default: self = .middle
        }
    }

    var roundsTop: Bool { self == .single || self == .first }
    var roundsBottom: Bool { self == .single || self == .last }
}

/// One row's share of a list block: Surface, Trait border, radius 20 on the block's outer corners, and a Trait léger
/// separator under every row but the last. The border of the hidden edges is pushed outside and clipped.
private struct BlockRowBackground: View {
    let position: BlockPosition

    var body: some View {
        let radius = Radius.block
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: position.roundsTop ? radius : 0,
            bottomLeadingRadius: position.roundsBottom ? radius : 0,
            bottomTrailingRadius: position.roundsBottom ? radius : 0,
            topTrailingRadius: position.roundsTop ? radius : 0
        )
        shape
            .fill(Palette.surface)
            .overlay { shape.strokeBorder(Palette.line, lineWidth: 1) }
            .padding(.top, position.roundsTop ? 0 : -2)
            .padding(.bottom, position.roundsBottom ? 0 : -2)
            .overlay(alignment: .bottom) {
                if !position.roundsBottom {
                    Rectangle().fill(Palette.lineLight).frame(height: 1).padding(.horizontal, 1)
                }
            }
            .clipped()
    }
}

private extension View {
    /// A row without background or separator, `horizontal` points from the screen edges.
    func plainListRow(top: CGFloat = 0, horizontal: CGFloat = 0) -> some View {
        listRowInsets(EdgeInsets(top: top, leading: horizontal, bottom: 0, trailing: horizontal))
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
    }

    /// A row of a list block, 16 points from the screen edges.
    func blockListRow(index: Int, count: Int) -> some View {
        listRowInsets(EdgeInsets(top: 0, leading: Spacing.block, bottom: 0, trailing: Spacing.block))
            .listRowSeparator(.hidden)
            .listRowBackground(
                BlockRowBackground(position: BlockPosition(index: index, count: count))
                    .padding(.horizontal, Spacing.block)
            )
    }
}
