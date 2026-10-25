# iosApp

SwiftUI app « Compagnon » (bundle id `com.compagnon.courses`, iOS 17+, Swift 6). The UI is native; the state and logic come
from the shared Kotlin code through the static `Shared` framework (SKIE), see `shared/umbrella/README.md`.

## Build and run

Open `iosApp.xcodeproj`, pick the `iosApp` scheme and a simulator, then run. Start the server first
(`./gradlew :server:app:run`): in Debug the app calls `http://localhost:8080`, which the simulator shares with the Mac.
The app works offline once the catalog has been synced (local SQLite database of the shared code).

The **Compile Kotlin Framework** build phase runs `./gradlew :shared:umbrella:embedAndSignAppleFrameworkForXcode` from
the repository root, before the Swift sources compile. It writes `Shared.framework` to
`shared/umbrella/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`, which is the target's `FRAMEWORK_SEARCH_PATHS`.
The phase needs `ENABLE_USER_SCRIPT_SANDBOXING = NO`. The first build downloads the Kotlin/Native toolchain and takes
several minutes.

Command line (same as the CI):

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath build/ios-derived build
```

## Configuration

| Build setting | Debug | Release | Read by |
|---|---|---|---|
| `API_BASE_URL` | `http://localhost:8080` | `https://api.compagnon.invalid` (placeholder until hosting is decided, ADR-007) | Info.plist key `API_BASE_URL`, then `AppConfiguration` |
| `MARKETING_VERSION` | `0.1.0` | `0.1.0` | sent as `X-App-Version` |

**Debug override of the base URL**: the user default `API_BASE_URL` wins over the build setting in Debug builds
(ignored in Release). Pass it as a launch argument, either in the scheme (« Arguments Passed On Launch »:
`-API_BASE_URL http://localhost:8091`) or from the command line:

```sh
xcrun simctl launch booted com.compagnon.courses -API_BASE_URL http://localhost:8091
```

**Debug demo screens**: `-DemoScreen <name>` shows one screen with a fake state, without Koin, a server or a session
(`meta`, `suggested`, `card`, `gate`, `gateItems`, `review`, `done`, `list`, `listEmpty`; see `Debug/DemoScreens.swift`,
which also holds the SwiftUI previews). Handy for screenshots in dark mode, English or large text sizes.

`NSAppTransportSecurity › NSAllowsLocalNetworking` allows plain HTTP to local hosts only (localhost, `.local` names,
IP literals). Signing is automatic without a team, which is enough for the simulator; set your own
`DEVELOPMENT_TEAM` locally to run on a device.

## Layout

The `iosApp/` source folder is a synchronized folder (Xcode 16+ project format): files added there join the target
without editing `project.pbxproj`. `Info.plist` is excluded from the target membership because it is the
`INFOPLIST_FILE`, merged with the generated keys.

- `App/`: `@main` entry point (`doInitKoin` in `init`, with the device language), `AppConfiguration`, and `RootView`
  (`NavigationStack` with the three screens; the catalog and list models live as long as the app).
- `Features/Home/`: « Accueil » (start a session, « Ma liste » with its count, catalog card).
- `Features/Catalog/`: `CatalogStatusModel` (server status and local sync) and `CatalogStatusCard`.
- `Features/Questionnaire/`: `QuestionSessionModel` (one per screen) and the stateless `QuestionContent`, which
  renders each `QuestionUiState` (meta questions, T1 list, product cards, gates, gate products, review, done, error).
  `SwipeableCard`: right = « Oui », left = « Non », duplicated by buttons and VoiceOver actions.
- `Features/List/`: `ShoppingListModel` and the « Liste » screen (sections, check, swipe to delete, undo banner,
  search and « Autre chose ? » free text).
- `Debug/`: fake states for previews and demo screens (Debug only).
- `Resources/`: asset catalog (green accent with a dark variant) and `Localizable.xcstrings` (French source, English
  translation, plural variations).

Each model wraps one shared ViewModel: it is resolved from Koin on the first `observe()` (from `.task`), owned by a
`ViewModelOwner` and cleared in `isolated deinit`. States are switched with SKIE's `onEnum(of:)`.

### Strings

Text in views uses French literals as keys. After adding strings, build, then update the catalog from the
extracted `.stringsdata` files (Xcode does this itself when building in the IDE):

```sh
xcrun xcstringstool sync iosApp/iosApp/Resources/Localizable.xcstrings \
  $(find build/ios-derived -name '*.stringsdata' | sed 's/^/--stringsdata /')
```

and add the English translation of every new key. French typography uses no-break spaces (`\u{00A0}`) before
`?`, `!`, `:` and inside « ».
