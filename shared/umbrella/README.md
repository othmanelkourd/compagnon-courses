# :shared:umbrella

This module builds the `Shared` iOS framework (static, with SKIE) and gathers the Koin graph of the shared code.

- **Android**: `startKoin { androidContext(this@App); modules(sharedModules(NetworkConfig(baseUrl, "android", versionName), CatalogScope.forDevice(Locale.getDefault().toLanguageTag()))) }`
  (`androidContext` is required: the SQLite database lives in the app's `databases/`), then
  `koinViewModel<CatalogStatusViewModel> { parametersOf("FR") }`, `koinViewModel<QuestionSessionViewModel>()`,
  `koinViewModel<ShoppingListViewModel>()`.
- **iOS**: Xcode runs `./gradlew :shared:umbrella:embedAndSignAppleFrameworkForXcode`. The framework lands in `build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)/Shared.framework`.
  The framework is static and uses SQLite: the app target links it (`-lsqlite3` in Other Linker Flags).

## Swift API

```swift
import Shared

// App.init: call it once (later calls do nothing). HTTP logging is on in Debug frameworks only.
doInitKoin(baseUrl: "http://localhost:8080", appVersion: "0.1.0", deviceLanguage: Locale.current.identifier)
// (doInitKoin(baseUrl:appVersion:) keeps the French catalog.)

@MainActor @Observable
final class CatalogStatusModel {
    private(set) var state: any CatalogStatusUiState
    @ObservationIgnored private let owner = ViewModelOwner()
    @ObservationIgnored private let viewModel: CatalogStatusViewModel

    init(country: String) {
        viewModel = CatalogStatusViewModelProvider().create(country: country, owner: owner)
        state = viewModel.state.value                 // SkieSwiftStateFlow<any CatalogStatusUiState>
    }

    func observe() async {                            // from `.task { await model.observe() }`
        for await newState in viewModel.state { state = newState }
    }

    func refresh() { viewModel.onEvent(event: CatalogStatusEventRefresh.shared) }

    isolated deinit { owner.clear() }                 // onCleared() + cancels viewModelScope
}

switch onEnum(of: model.state) {
case .loading: ProgressView()
case .loaded(let s): Text("Catalogue \(s.country) · v\(s.version)")  // version: Int64, counts: Int32
case .error(let e): Text(e.message)                  // e.retryable: Bool
}
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

The ViewModels resolved from Koin must be owned by a `ViewModelOwner`, either with `create(country:owner:)` or with `owner.own(viewModel:)`. Without an owner they are never cleared, so their coroutines are never cancelled. Do not call `CatalogStatusViewModel(api:country:)` from Swift.

Swift only sees ViewModels and UI state. `CatalogApi`, the Koin modules and the contract DTOs are hidden with `@HiddenFromObjC`, so Ktor, Koin and the DTOs stay out of `Shared.h`.
