# :shared:umbrella

This module builds the `Shared` iOS framework (static, with SKIE) and gathers the Koin graph of the shared code.

- **Android**: `startKoin { androidContext(this@App); modules(sharedModules(NetworkConfig(baseUrl, "android", versionName))) }`, then `koinViewModel<CatalogStatusViewModel> { parametersOf("FR") }`.
- **iOS**: Xcode runs `./gradlew :shared:umbrella:embedAndSignAppleFrameworkForXcode`. The framework lands in `build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)/Shared.framework`.

## Swift API

```swift
import Shared

// App.init: call it once (later calls do nothing). HTTP logging is on in Debug frameworks only.
doInitKoin(baseUrl: "http://localhost:8080", appVersion: "0.1.0")   // or KoinIosKt.doInitKoin(...)

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

The ViewModels resolved from Koin must be owned by a `ViewModelOwner`, either with `create(country:owner:)` or with `owner.own(viewModel:)`. Without an owner they are never cleared, so their coroutines are never cancelled. Do not call `CatalogStatusViewModel(api:country:)` from Swift.

Swift only sees ViewModels and UI state. `CatalogApi`, the Koin modules and the contract DTOs are hidden with `@HiddenFromObjC`, so Ktor, Koin and the DTOs stay out of `Shared.h`.
