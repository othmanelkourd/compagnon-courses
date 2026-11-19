#if DEBUG
    import Shared
    import SwiftUI

    /// Fake states for previews and for the Debug demo screens (`-DemoScreen <name>` launch argument), which
    /// render one screen without Koin, a server or a session: handy for screenshots and design checks. The data
    /// follows the mockups of `design/maquette/`.
    enum DemoScreen: String, CaseIterable {
        case home, meta, suggested, card, options, gate, gateItems, review, done, list, listEmpty
        case onboardingWelcome = "onboarding-welcome"
        case onboardingHousehold = "onboarding-household"
        case onboardingDiet = "onboarding-diet"
        case onboardingAvoid = "onboarding-avoid"
        case onboardingEssentials = "onboarding-essentials"
        case onboardingRecurring = "onboarding-recurring"
        case onboardingRhythm = "onboarding-rhythm"
        case onboardingRecap = "onboarding-recap"
        /// « Mon foyer » (`Household-Edit`).
        case household

        /// The onboarding step this demo shows, if it is one.
        var onboardingStep: OnboardingStep? {
            switch self {
            case .onboardingWelcome: .welcome
            case .onboardingHousehold: .household
            case .onboardingDiet: .diet
            case .onboardingAvoid: .avoid
            case .onboardingEssentials: .essentials
            case .onboardingRecurring: .recurring
            case .onboardingRhythm: .rhythm
            case .onboardingRecap: .recap
            case .household: .overview
            default: nil
            }
        }

        static func fromLaunchArguments(_ defaults: UserDefaults = .standard) -> DemoScreen? {
            // « list-empty » and « gate-items » are accepted too, the names of the mockups.
            let aliases = ["list-empty": "listEmpty", "gate-items": "gateItems"]
            return defaults.string(forKey: "DemoScreen").flatMap { DemoScreen(rawValue: aliases[$0] ?? $0) }
        }
    }

    struct DemoScreenView: View {
        let screen: DemoScreen

        var body: some View {
            NavigationStack {
                if let step = screen.onboardingStep {
                    OnboardingContent(state: DemoData.onboarding(step), send: { _ in })
                } else {
                    content
                }
            }
        }

        @ViewBuilder
        private var content: some View {
                switch screen {
                case .home:
                    HomeContent(
                        listState: DemoData.homeList,
                        household: DemoData.householdSummary,
                        catalogStatus: DemoData.catalogStatus,
                        onStartSession: {},
                        onOpenList: {},
                        onOpenHousehold: {}
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
                quantity(1500, "kilogram", "1,5\u{00A0}kg")
            ],
            quantity: QuantityStepper(selected: quantity(500, "kilogram", "0,5\u{00A0}kg"), canDecrement: false, canIncrement: true),
            units: [
                UnitOptionUi(unit: "kilogram", label: "Au poids", selected: true),
                UnitOptionUi(unit: "piece", label: "À la pièce", selected: false)
            ],
            isSameAsLastTime: false,
            reason: reason(.staple),
            imageKey: "tomato"
        )

        static func suggestedItem(
            _ id: String, _ name: String, _ label: String, checked: Bool, reason: ReasonUi?, imageKey: String, canDecrement: Bool = true
        ) -> SuggestedItemUi {
            let quantity = QuantityStepper(selected: quantity(1000, "piece", label), canDecrement: canDecrement, canIncrement: true)
            return SuggestedItemUi(productId: id, name: name, checked: checked, quantity: quantity, reason: reason, imageKey: imageKey)
        }

        /// « Probablement nécessaire »: the staples of the catalog, « Banane » unchecked, with the images the shared
        /// `Illustrations` gives them.
        static let staples: [(name: String, label: String, imageKey: String)] = [
            ("Oignon", "0,5\u{00A0}kg", "onion"), ("Banane", "4\u{00A0}bananes", "banana"), ("Œuf", "6\u{00A0}œufs", "egg"),
            ("Beurre doux", "1\u{00A0}plaquette", "butter"), ("Sel fin", "1\u{00A0}paquet", "salt"), ("Poivre noir", "1\u{00A0}pot", "salt"),
            ("Pâtes courtes", "1\u{00A0}paquet", "spaghetti"), ("Moutarde", "1\u{00A0}pot", "jar"), ("Dentifrice", "1\u{00A0}tube", "toothbrush"),
            ("Déodorant", "1\u{00A0}déodorant", "lotion_bottle"), ("Liquide vaisselle", "1\u{00A0}flacon", "bubbles"),
            ("Éponge", "1\u{00A0}pack", "sponge"), ("Papier toilette", "1\u{00A0}pack", "roll_of_paper"),
            ("Sac poubelle", "1\u{00A0}rouleau", "wastebasket"), ("Essuie-tout", "1\u{00A0}pack", "roll_of_paper")
        ]

        static let suggestedItems = staples.map { staple in
            // As in the real session, only « Banane » and « Œuf » are above their smallest quantity.
            suggestedItem(
                staple.name, staple.name, staple.label, checked: staple.name != "Banane", reason: reason(.staple),
                imageKey: staple.imageKey, canDecrement: ["Banane", "Œuf"].contains(staple.name)
            )
        }

        static let gate = CategoryGateUi(
            categoryId: "fruits-legumes", name: "Fruits et légumes",
            question: "Besoin de quelque chose en fruits et légumes\u{00A0}?", productCount: 38, imageKey: "green_apple"
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
                        suggestedItem("sponges", "Éponges", "1 paquet", checked: false, reason: nil, imageKey: "sponge"),
                        suggestedItem("dish", "Liquide vaisselle", "1 flacon", checked: true, reason: nil, imageKey: "bubbles"),
                        suggestedItem("bags", "Sacs poubelle", "1 rouleau", checked: false, reason: nil, imageKey: "wastebasket"),
                        suggestedItem("tablets", "Pastilles lave-vaisselle", "30 pastilles lave-vaisselle", checked: false, reason: nil, imageKey: "broom")
                    ],
                    checkedCount: 1,
                    progress: QuestionProgress(answered: 11, total: 12),
                    canUndo: true
                )
            case .review:
                QuestionUiStateReview(
                    items: staples.filter { $0.name != "Banane" }.map { staple in
                        ReviewItemUi(
                            productId: staple.name, name: staple.name, quantity: quantity(1000, "piece", staple.label), origin: .suggested,
                            imageKey: staple.imageKey
                        )
                    } + [
                        ReviewItemUi(
                            productId: "tomate-de-saison", name: "Tomate de saison", quantity: quantity(1000, "kilogram", "1\u{00A0}kg"),
                            origin: .card, imageKey: "tomato"
                        )
                    ],
                    searchQuery: "",
                    searchResults: [],
                    progress: QuestionProgress(answered: 12, total: 12),
                    canUndo: true
                )
            case .done:
                QuestionUiStateDone(itemsAdded: 15, abandoned: false)
            default:
                // Carrots come next: the card behind shows them.
                QuestionUiStateCard(card: tomatoesCard, progress: QuestionProgress(answered: 4, total: 12), canUndo: true, nextImageKey: "carrot")
            }
        }

        static func item(_ id: String, _ name: String, _ quantity: String?, _ imageKey: String, checked: Bool = false) -> ListItemUi {
            ListItemUi(id: id, name: name, quantityLabel: quantity, checked: checked, productId: id, isFreeText: quantity == nil, imageKey: imageKey)
        }

        static let list = ShoppingListUiStateLoaded(
            sections: [
                ListSectionUi(categoryId: "fruits-legumes", title: "Fruits et légumes", items: [
                    item("oignon", "Oignon", "0,5\u{00A0}kg", "onion"),
                    item("tomate", "Tomate de saison", "1\u{00A0}kg", "tomato")
                ]),
                ListSectionUi(categoryId: "cremerie", title: "Crèmerie", items: [
                    item("beurre", "Beurre doux", "1\u{00A0}plaquette", "butter"),
                    item("oeuf", "Œuf", "6\u{00A0}œufs", "egg", checked: true)
                ]),
                ListSectionUi(categoryId: "epicerie-salee", title: "Épicerie salée", items: [
                    item("sel", "Sel fin", "1\u{00A0}paquet", "salt"),
                    item("poivre", "Poivre noir", "1\u{00A0}pot", "salt"),
                    item("pates", "Pâtes courtes", "1\u{00A0}paquet", "spaghetti")
                ]),
                ListSectionUi(categoryId: "hygiene", title: "Hygiène et beauté", items: [
                    item("dentifrice", "Dentifrice", "1\u{00A0}tube", "toothbrush"),
                    item("deodorant", "Déodorant", "1\u{00A0}déodorant", "lotion_bottle")
                ]),
                ListSectionUi(categoryId: "entretien", title: "Entretien de la maison", items: [
                    item("vaisselle", "Liquide vaisselle", "1\u{00A0}flacon", "bubbles"),
                    item("eponge", "Éponge", "1\u{00A0}pack", "sponge")
                ]),
                ListSectionUi(categoryId: "papier", title: "Papier et emballages", items: [
                    item("papier", "Papier toilette", "1\u{00A0}pack", "roll_of_paper"),
                    item("sacs", "Sac poubelle", "1\u{00A0}rouleau", "wastebasket"),
                    item("essuie", "Essuie-tout", "1\u{00A0}pack", "roll_of_paper")
                ])
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

        // MARK: Onboarding (mockups `Onboarding-*` and `Household-Edit`)

        static let householdSummary = HouseholdSummaryUi(
            adults: 2, babies: 1, children: 1, teens: 0, pets: [.cat], diet: .vegetarian, avoid: [.lactose],
            essentials: 14, recurring: 4, horizonDays: 7, onboarded: true
        )

        static func chip(_ name: String, _ checked: Bool, _ imageKey: String) -> ProductChipUi {
            ProductChipUi(productId: name, name: name, checked: checked, imageKey: imageKey)
        }

        static let essentialsAisles = [
            EssentialsAisleUi(categoryId: "fruits-legumes", title: "Fruits et légumes", items: [
                chip("Bananes", true, "banana"), chip("Pommes", true, "red_apple"), chip("Oignons", true, "onion"),
                chip("Pommes de terre", true, "potato"), chip("Tomates", false, "tomato"), chip("Carottes", false, "carrot")
            ], total: 44, expanded: false, imageKey: "green_apple"),
            EssentialsAisleUi(categoryId: "boulangerie", title: "Boulangerie", items: [
                chip("Baguette", true, "baguette_bread"), chip("Pain de mie", false, "bread"), chip("Brioche", false, "bread")
            ], total: 12, expanded: false, imageKey: "baguette_bread"),
            EssentialsAisleUi(categoryId: "epicerie-salee", title: "Épicerie salée", items: [
                chip("Riz", true, "cooked_rice"), chip("Pâtes", true, "spaghetti"), chip("Huile d'olive", true, "olive"),
                chip("Sel fin", false, "salt")
            ], total: 48, expanded: false, imageKey: "canned_food"),
            EssentialsAisleUi(categoryId: "bebe", title: "Bébé", items: [
                chip("Couches", true, "safety_pin"), chip("Lait infantile", true, "baby_bottle"), chip("Petits pots", false, "jar")
            ], total: 9, expanded: false, imageKey: "baby_bottle"),
            EssentialsAisleUi(categoryId: "animaux", title: "Animaux", items: [
                chip("Croquettes chat", true, "cat"), chip("Litière", true, "cat")
            ], total: 8, expanded: false, imageKey: "paw_prints")
        ]

        static func onboarding(_ step: OnboardingStep) -> OnboardingUiStateReady {
            let numbers: [OnboardingStep: Int32] = [.household: 1, .diet: 2, .avoid: 2, .essentials: 3, .recurring: 4, .rhythm: 4]
            let number = numbers[step]
            return OnboardingUiStateReady(
                mode: step == .overview ? .edit : .firstRun,
                step: step,
                stepNumber: number.map { KotlinInt(value: $0) },
                stepCount: 4,
                canSkip: number != nil && step != .household,
                closeInsteadOfBack: false,
                household: HouseholdStepUi(adults: 2, babies: 1, children: 1, teens: 0, pets: [.cat], maxMembers: 12),
                diet: .vegetarian,
                avoid: [
                    AvoidOptionUi(choice: .gluten, checked: false, locked: false),
                    AvoidOptionUi(choice: .lactose, checked: true, locked: false),
                    AvoidOptionUi(choice: .nuts, checked: false, locked: false),
                    AvoidOptionUi(choice: .eggs, checked: false, locked: false),
                    AvoidOptionUi(choice: .alcohol, checked: false, locked: false)
                ],
                essentials: EssentialsStepUi(
                    query: "", aisles: essentialsAisles, results: [], checkedCount: 14, catalogLoading: false, catalogMissing: false
                ),
                recurring: RecurringStepUi(
                    rows: [
                        RecurringRowUi(productId: "baguette", name: "Baguette", cadence: .week, imageKey: "baguette_bread"),
                        RecurringRowUi(productId: "couches", name: "Couches", cadence: .twoWeeks, imageKey: "safety_pin"),
                        RecurringRowUi(productId: "croquettes", name: "Croquettes chat", cadence: .month, imageKey: "cat")
                    ],
                    adding: false, query: "", results: []
                ),
                rhythm: RhythmStepUi(days: 7, choice: .week, maxDays: 31),
                summary: householdSummary,
                saving: false,
                error: nil,
                exit: nil
            )
        }

        static let catalogStatus = CatalogStatusUiState(
            ready: true, version: KotlinLong(value: 3), productCount: 312, categoryCount: 18
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

    #Preview("Bienvenue") {
        DemoScreenView(screen: .onboardingWelcome)
    }

    #Preview("Onboarding : foyer") {
        DemoScreenView(screen: .onboardingHousehold)
    }

    #Preview("Onboarding : essentiels") {
        DemoScreenView(screen: .onboardingEssentials)
    }

    #Preview("Onboarding : rythme") {
        DemoScreenView(screen: .onboardingRhythm)
    }

    #Preview("Mon foyer") {
        DemoScreenView(screen: .household)
    }
#endif
