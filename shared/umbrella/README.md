# :shared:umbrella

This module builds the `Shared` iOS framework (static, with SKIE) and gathers the Koin graph of the shared code.

- **Android**: `startKoin { androidContext(this@App); modules(sharedModules(CatalogScope.forDevice(Locale.getDefault().toLanguageTag()))) }`
  (`androidContext` is required: the SQLite database lives in the app's `databases/`), then
  `koinViewModel<CatalogStatusViewModel>()`, `koinViewModel<QuestionSessionViewModel>()`,
  `koinViewModel<ShoppingListViewModel>()`.
- **iOS**: Xcode runs `./gradlew :shared:umbrella:embedAndSignAppleFrameworkForXcode`. The framework lands in `build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)/Shared.framework`.
  The framework is static and uses SQLite: the app target links it (`-lsqlite3` in Other Linker Flags).

## Swift API

```swift
import Shared

// App.init: call it once (later calls do nothing). It also installs the catalog bundled in the app.
doInitKoin(deviceLanguage: Locale.current.identifier)

@MainActor @Observable
final class CatalogStatusModel {
    private(set) var state: CatalogStatusUiState
    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private let viewModel: CatalogStatusViewModel

    init() {
        viewModel = CatalogStatusViewModelProvider().create(owner: owner)
        state = viewModel.state.value                 // SkieSwiftStateFlow<CatalogStatusUiState>
    }

    func observe() async {                            // from `.task { await model.observe() }`
        for await newState in viewModel.state { state = newState }
    }

    isolated deinit { owner.clear() }                 // onCleared() + cancels viewModelScope
}

Text(model.state.ready ? "\(model.state.productCount) produits sur l'appareil" : "Préparation du catalogue…")
```

The question flow and the list follow the same pattern:

```swift
let questions = QuestionSessionViewModelProvider().create(owner: owner)   // resumes or starts a session
for await s in questions.state { … }                                      // any QuestionUiState
for await h in questions.haptics { … }                                    // QuestionHaptic
questions.onEvent(event: QuestionEventYes(quantity: nil))                 // data objects: QuestionEventNo.shared
switch onEnum(of: state) { case .meta(let m): … case .card(let c): … case .done(let d): … }

let list = ShoppingListViewModelProvider().create(owner: owner)
list.onEvent(event: ShoppingListEventToggleChecked(itemId: item.id))
```

The ViewModels resolved from Koin must be owned by a `ViewModelOwner`, either with `create(owner:)` or with `owner.own(viewModel:)`. Without an owner they are never cleared, so their coroutines are never cancelled.

Swift only sees ViewModels and UI state. The repositories, the Koin modules and the catalog DTOs are hidden with `@HiddenFromObjC`, so Koin and the DTOs stay out of `Shared.h`.
