#if DEBUG
    import Shared
    import SwiftUI

    /// Fake states for previews and for the Debug demo screens (`-DemoScreen <name>` launch argument), which
    /// render one screen without Koin, a server or a session: handy for screenshots and design checks. The data
    /// follows the mockups of `design/maquette/`.
    enum DemoScreen: String, CaseIterable {
        case home, meta, suggested, card, options, gate, gateItems, review, done, list, listEmpty

        static func fromLaunchArguments(_ defaults: UserDefaults = .standard) -> DemoScreen? {
            defaults.string(forKey: "DemoScreen").flatMap(DemoScreen.init(rawValue:))
        }
    }

    struct DemoScreenView: View {
        let screen: DemoScreen

        var body: some View {
            NavigationStack {
                switch screen {
                case .home:
                    HomeContent(
                        listState: DemoData.homeList,
                        catalogSync: DemoData.catalogSync,
                        onStartSession: {},
                        onOpenList: {}
                    )
                case .list:
                    ShoppingListContent(state: DemoData.list, send: { _ in })
                case .listEmpty:
                    ShoppingListContent(state: DemoData.emptyList, send: { _ in })
                case .options:
                    QuestionContent(state: DemoData.question(.card), send: { _ in }, showsCardOptions: true)
                default:
                    QuestionContent(state: DemoData.question(screen), send: { _ in })
                }
            }
        }
    }

    enum DemoData {
        static func quantity(_ milli: Int64, _ unit: String, _ label: String) -> QuantityChoice {
            QuantityChoice(value: QuantityValue(milli: milli, unit: unit), label: label)
        }

        static func stepper(_ choice: QuantityChoice) -> QuantityStepper {
            QuantityStepper(selected: choice, canDecrement: true, canIncrement: true)
        }

        static func reason(_ kind: ReasonKind, daysSinceLast: Int32? = nil, intervalDays: Int32? = nil) -> ReasonUi {
            ReasonUi(
                kind: kind,
                daysSinceLast: daysSinceLast.map { KotlinInt(value: $0) },
                intervalDays: intervalDays.map { KotlinInt(value: $0) },
                usualQuantity: nil, weeksSinceLast: nil, householdSize: nil
            )
        }

        // The data mirrors a real session against the local server (same catalog as the Android screenshots), so
        // that `docs/images/design-{android,ios}-*.png` can be compared pair by pair.
        static let tomatoesCard = ProductCardUi(
            productId: "tomate-de-saison",
            name: "Tomate de saison",
            needPhrase: "des tomates de saison",
            categoryName: "Fruits et légumes",
            kind: .standard,
            presets: [
                quantity(500, "kilogram", "0,5\u{00A0}kg"),
                quantity(1000, "kilogram", "1\u{00A0}kg"),
                quantity(1500, "kilogram", "1,5\u{00A0}kg"),
            ],
            quantity: QuantityStepper(selected: quantity(500, "kilogram", "0,5\u{00A0}kg"), canDecrement: false, canIncrement: true),
            units: [
                UnitOptionUi(unit: "kilogram", label: "kg", selected: true),
                UnitOptionUi(unit: "piece", label: "tomates de saison", selected: false),
            ],
            isSameAsLastTime: false,
            reason: reason(.staple)
        )

        static func suggestedItem(
            _ id: String, _ name: String, _ label: String, checked: Bool, reason: ReasonUi?, canDecrement: Bool = true
        ) -> SuggestedItemUi {
            let quantity = QuantityStepper(selected: quantity(1000, "piece", label), canDecrement: canDecrement, canIncrement: true)
            return SuggestedItemUi(productId: id, name: name, checked: checked, quantity: quantity, reason: reason)
        }

        /// « Probablement nécessaire »: the staples of the catalog, « Banane » unchecked.
        static let staples: [(String, String)] = [
            ("Oignon", "0,5\u{00A0}kg"), ("Banane", "4\u{00A0}bananes"), ("Œuf", "6\u{00A0}œufs"),
            ("Beurre doux", "1\u{00A0}plaquette"), ("Sel fin", "1\u{00A0}paquet"), ("Poivre noir", "1\u{00A0}pot"),
            ("Pâtes courtes", "1\u{00A0}paquet"), ("Moutarde", "1\u{00A0}pot"), ("Dentifrice", "1\u{00A0}tube"),
            ("Déodorant", "1\u{00A0}déodorant"), ("Liquide vaisselle", "1\u{00A0}flacon"), ("Éponge", "1\u{00A0}pack"),
            ("Papier toilette", "1\u{00A0}pack"), ("Sac poubelle", "1\u{00A0}rouleau"), ("Essuie-tout", "1\u{00A0}pack"),
        ]

        static let suggestedItems = staples.map { name, label in
            // As in the real session, only « Banane » and « Œuf » are above their smallest quantity.
            suggestedItem(name, name, label, checked: name != "Banane", reason: reason(.staple), canDecrement: ["Banane", "Œuf"].contains(name))
        }

        static let gate = CategoryGateUi(
            categoryId: "fruits-legumes", name: "Fruits et légumes",
            question: "Besoin de quelque chose en fruits et légumes\u{00A0}?", productCount: 38
        )

        static func question(_ screen: DemoScreen) -> any QuestionUiState {
            switch screen {
            case .meta:
                QuestionUiStateMeta(
                    question: MetaQuestionUi(
                        kind: .horizonDays, value: 7, min: 1, max: 31,
                        choices: [3, 7, 14].map { KotlinInt(value: $0) }
                    ),
                    progress: QuestionProgress(answered: 0, total: 12),
                    canUndo: false
                )
            case .suggested:
                QuestionUiStateSuggested(items: suggestedItems, checkedCount: 14, progress: QuestionProgress(answered: 3, total: 12), canUndo: true)
            case .gate:
                QuestionUiStateGate(gate: gate, progress: QuestionProgress(answered: 10, total: 12), canUndo: true)
            case .gateItems:
                QuestionUiStateGateItems(
                    gate: gate,
                    items: [
                        suggestedItem("sponges", "Éponges", "1 paquet", checked: false, reason: nil),
                        suggestedItem("dish", "Liquide vaisselle", "1 flacon", checked: true, reason: nil),
                        suggestedItem("bags", "Sacs poubelle", "1 rouleau", checked: false, reason: nil),
                        suggestedItem("tablets", "Pastilles lave-vaisselle", "30 pastilles lave-vaisselle", checked: false, reason: nil),
                    ],
                    checkedCount: 1,
                    progress: QuestionProgress(answered: 11, total: 12),
                    canUndo: true
                )
            case .review:
                QuestionUiStateReview(
                    items: staples.filter { $0.0 != "Banane" }.map { name, label in
                        ReviewItemUi(productId: name, name: name, quantity: quantity(1000, "piece", label), origin: .suggested)
                    } + [
                        ReviewItemUi(productId: "tomate-de-saison", name: "Tomate de saison", quantity: quantity(1000, "kilogram", "1\u{00A0}kg"), origin: .card),
                    ],
                    searchQuery: "",
                    searchResults: [],
                    progress: QuestionProgress(answered: 12, total: 12),
                    canUndo: true
                )
            case .done:
                QuestionUiStateDone(itemsAdded: 15, abandoned: false)
            default:
                QuestionUiStateCard(card: tomatoesCard, progress: QuestionProgress(answered: 4, total: 12), canUndo: true)
            }
        }

        static func item(_ id: String, _ name: String, _ quantity: String?, checked: Bool = false) -> ListItemUi {
            ListItemUi(id: id, name: name, quantityLabel: quantity, checked: checked, productId: id, isFreeText: quantity == nil)
        }

        static let list = ShoppingListUiStateLoaded(
            sections: [
                ListSectionUi(categoryId: "fruits-legumes", title: "Fruits et légumes", items: [
                    item("oignon", "Oignon", "0,5\u{00A0}kg"),
                    item("tomate", "Tomate de saison", "1\u{00A0}kg"),
                ]),
                ListSectionUi(categoryId: "cremerie", title: "Crèmerie", items: [
                    item("beurre", "Beurre doux", "1\u{00A0}plaquette"),
                    item("oeuf", "Œuf", "6\u{00A0}œufs", checked: true),
                ]),
                ListSectionUi(categoryId: "epicerie-salee", title: "Épicerie salée", items: [
                    item("sel", "Sel fin", "1\u{00A0}paquet"),
                    item("poivre", "Poivre noir", "1\u{00A0}pot"),
                    item("pates", "Pâtes courtes", "1\u{00A0}paquet"),
                ]),
                ListSectionUi(categoryId: "hygiene", title: "Hygiène et beauté", items: [
                    item("dentifrice", "Dentifrice", "1\u{00A0}tube"),
                    item("deodorant", "Déodorant", "1\u{00A0}déodorant"),
                ]),
                ListSectionUi(categoryId: "entretien", title: "Entretien de la maison", items: [
                    item("vaisselle", "Liquide vaisselle", "1\u{00A0}flacon"),
                    item("eponge", "Éponge", "1\u{00A0}pack"),
                ]),
                ListSectionUi(categoryId: "papier", title: "Papier et emballages", items: [
                    item("papier", "Papier toilette", "1\u{00A0}pack"),
                    item("sacs", "Sac poubelle", "1\u{00A0}rouleau"),
                    item("essuie", "Essuie-tout", "1\u{00A0}pack"),
                ]),
            ],
            itemCount: 14,
            remainingCount: 13,
            searchQuery: "",
            searchResults: [],
            undo: UndoUi(kind: .deleted, itemName: "Moutarde"),
            error: nil
        )

        static let emptyList = ShoppingListUiStateLoaded(
            sections: [], itemCount: 0, remainingCount: 0, searchQuery: "", searchResults: [], undo: nil, error: nil
        )

        static let homeList = ShoppingListUiStateLoaded(
            sections: [], itemCount: 14, remainingCount: 13, searchQuery: "", searchResults: [], undo: nil, error: nil
        )

        static let catalogSync = CatalogSyncUiState(
            syncing: false, localVersion: KotlinLong(value: 3), lastSyncedAt: "2026-10-24T08:30:00Z",
            productCount: 312, categoryCount: 18, lastOutcome: .upToDate
        )
    }

    #Preview("Accueil") {
        DemoScreenView(screen: .home)
    }

    #Preview("Question méta") {
        DemoScreenView(screen: .meta)
    }

    #Preview("Probablement nécessaire") {
        DemoScreenView(screen: .suggested)
    }

    #Preview("Carte produit") {
        DemoScreenView(screen: .card)
    }

    #Preview("Carte produit, grand texte") {
        DemoScreenView(screen: .card)
            .dynamicTypeSize(.accessibility2)
    }

    #Preview("Plus d'options") {
        DemoScreenView(screen: .options)
    }

    #Preview("Porte de rayon") {
        DemoScreenView(screen: .gate)
    }

    #Preview("Produits du rayon") {
        DemoScreenView(screen: .gateItems)
    }

    #Preview("Récapitulatif") {
        DemoScreenView(screen: .review)
    }

    #Preview("Terminé") {
        DemoScreenView(screen: .done)
    }

    #Preview("Liste") {
        DemoScreenView(screen: .list)
    }

    #Preview("Liste vide") {
        DemoScreenView(screen: .listEmpty)
    }
#endif
