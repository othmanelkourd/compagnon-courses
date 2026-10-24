#if DEBUG
    import Shared
    import SwiftUI

    /// Fake states for previews and for the Debug demo screens (`-DemoScreen <name>` launch argument), which
    /// render one screen without Koin, a server or a session: handy for screenshots and design checks.
    enum DemoScreen: String, CaseIterable {
        case meta, suggested, card, gate, gateItems, review, done, list, listEmpty

        static func fromLaunchArguments(_ defaults: UserDefaults = .standard) -> DemoScreen? {
            defaults.string(forKey: "DemoScreen").flatMap(DemoScreen.init(rawValue:))
        }
    }

    struct DemoScreenView: View {
        let screen: DemoScreen

        var body: some View {
            NavigationStack {
                switch screen {
                case .list:
                    ShoppingListContent(state: DemoData.list, send: { _ in })
                case .listEmpty:
                    ShoppingListContent(state: DemoData.emptyList, send: { _ in })
                default:
                    QuestionContent(state: DemoData.question(screen), send: { _ in })
                }
            }
        }
    }

    enum DemoData {
        static let progress = QuestionProgress(answered: 8, total: 12)

        static func quantity(_ milli: Int64, _ unit: String, _ label: String) -> QuantityChoice {
            QuantityChoice(value: QuantityValue(milli: milli, unit: unit), label: label)
        }

        static func stepper(_ choice: QuantityChoice) -> QuantityStepper {
            QuantityStepper(selected: choice, canDecrement: true, canIncrement: true)
        }

        static let milkCard = ProductCardUi(
            productId: "milk",
            name: "Lait demi-écrémé",
            needPhrase: "du lait demi-écrémé",
            categoryName: "Crèmerie",
            kind: .standard,
            presets: [
                quantity(1000, "bottle", "1 bouteille"),
                quantity(2000, "bottle", "2 bouteilles"),
                quantity(4000, "bottle", "4 bouteilles"),
                quantity(6000, "bottle", "6 bouteilles"),
            ],
            quantity: stepper(quantity(2000, "bottle", "2 bouteilles")),
            units: [
                UnitOptionUi(unit: "bottle", label: "Bouteille", selected: true),
                UnitOptionUi(unit: "pack", label: "Pack de 6", selected: false),
            ],
            isSameAsLastTime: true,
            reason: ReasonUi(
                kind: .due, daysSinceLast: KotlinInt(value: 9), intervalDays: KotlinInt(value: 7),
                usualQuantity: "2 bouteilles", weeksSinceLast: nil, householdSize: nil
            )
        )

        static func suggestedItem(_ id: String, _ name: String, _ label: String, checked: Bool, reason: ReasonKind?) -> SuggestedItemUi {
            SuggestedItemUi(
                productId: id,
                name: name,
                checked: checked,
                quantity: stepper(quantity(1000, "piece", label)),
                reason: reason.map {
                    ReasonUi(kind: $0, daysSinceLast: nil, intervalDays: nil, usualQuantity: nil, weeksSinceLast: nil, householdSize: nil)
                }
            )
        }

        static let suggestedItems = [
            suggestedItem("bread", "Baguette", "2 baguettes", checked: true, reason: .staple),
            suggestedItem("eggs", "Œufs", "6 œufs", checked: true, reason: .lastList),
            suggestedItem("bananas", "Bananes", "1 kg", checked: true, reason: .staple),
            suggestedItem("butter", "Beurre doux", "250 g", checked: false, reason: .littleLeft),
            suggestedItem("yogurt", "Yaourts nature", "8 pots", checked: true, reason: nil),
        ]

        static let gate = CategoryGateUi(categoryId: "cleaning", name: "Entretien", question: nil, productCount: 14)

        static func question(_ screen: DemoScreen) -> any QuestionUiState {
            switch screen {
            case .meta:
                QuestionUiStateMeta(
                    question: MetaQuestionUi(
                        kind: .persons, value: 2, min: 1, max: 12,
                        choices: [1, 2, 3, 4].map { KotlinInt(value: $0) }
                    ),
                    progress: QuestionProgress(answered: 1, total: 12),
                    canUndo: true
                )
            case .suggested:
                QuestionUiStateSuggested(items: suggestedItems, checkedCount: 4, progress: QuestionProgress(answered: 3, total: 12), canUndo: true)
            case .gate:
                QuestionUiStateGate(gate: gate, progress: QuestionProgress(answered: 10, total: 12), canUndo: true)
            case .gateItems:
                QuestionUiStateGateItems(
                    gate: gate,
                    items: [
                        suggestedItem("sponges", "Éponges", "1 paquet", checked: false, reason: nil),
                        suggestedItem("dish", "Liquide vaisselle", "1 flacon", checked: true, reason: nil),
                        suggestedItem("bags", "Sacs poubelle", "1 rouleau", checked: false, reason: nil),
                    ],
                    checkedCount: 1,
                    progress: QuestionProgress(answered: 11, total: 12),
                    canUndo: true
                )
            case .review:
                QuestionUiStateReview(
                    items: [
                        ReviewItemUi(productId: "bread", name: "Baguette", quantity: quantity(2000, "piece", "2 baguettes"), origin: .suggested),
                        ReviewItemUi(productId: "milk", name: "Lait demi-écrémé", quantity: quantity(2000, "bottle", "2 bouteilles"), origin: .card),
                        ReviewItemUi(productId: "dish", name: "Liquide vaisselle", quantity: quantity(1000, "piece", "1 flacon"), origin: .gate),
                    ],
                    searchQuery: "",
                    searchResults: [],
                    progress: QuestionProgress(answered: 12, total: 12),
                    canUndo: true
                )
            case .done:
                QuestionUiStateDone(itemsAdded: 7, abandoned: false)
            default:
                QuestionUiStateCard(card: milkCard, progress: progress, canUndo: true)
            }
        }

        static func item(_ id: String, _ name: String, _ quantity: String?, checked: Bool = false) -> ListItemUi {
            ListItemUi(id: id, name: name, quantityLabel: quantity, checked: checked, productId: id, isFreeText: quantity == nil)
        }

        static let list = ShoppingListUiStateLoaded(
            sections: [
                ListSectionUi(categoryId: "fruits", title: "Fruits et légumes", items: [
                    item("bananas", "Bananes", "1 kg"),
                    item("tomatoes", "Tomates", "500 g"),
                    item("apples", "Pommes", "6 pommes", checked: true),
                ]),
                ListSectionUi(categoryId: "dairy", title: "Crèmerie", items: [
                    item("milk", "Lait demi-écrémé", "2 bouteilles"),
                    item("eggs", "Œufs", "6 œufs"),
                ]),
                ListSectionUi(categoryId: nil, title: nil, items: [
                    item("gift", "Cadeau pour Léa", nil),
                ]),
            ],
            itemCount: 6,
            remainingCount: 5,
            searchQuery: "",
            searchResults: [],
            undo: UndoUi(kind: .checked, itemName: "Pommes"),
            error: nil
        )

        static let emptyList = ShoppingListUiStateLoaded(
            sections: [], itemCount: 0, remainingCount: 0, searchQuery: "", searchResults: [], undo: nil, error: nil
        )
    }

    #Preview("Carte produit") {
        DemoScreenView(screen: .card)
    }

    #Preview("Carte produit, grand texte") {
        DemoScreenView(screen: .card)
            .dynamicTypeSize(.accessibility2)
    }

    #Preview("Carte produit, sombre") {
        DemoScreenView(screen: .card)
            .preferredColorScheme(.dark)
    }

    #Preview("Probablement nécessaire") {
        DemoScreenView(screen: .suggested)
    }

    #Preview("Porte de rayon") {
        DemoScreenView(screen: .gate)
    }

    #Preview("Question méta") {
        DemoScreenView(screen: .meta)
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
