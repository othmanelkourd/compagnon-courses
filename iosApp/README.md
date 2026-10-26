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
(`home`, `meta`, `suggested`, `card`, `options`, `gate`, `gateItems`, `review`, `done`, `list`, `listEmpty`; see
`Debug/DemoScreens.swift`, which also holds the SwiftUI previews and follows the data of the mockups). Handy for
screenshots in English (`-AppleLanguages "(en)"`) or at large text sizes
(`xcrun simctl ui booted content_size extra-extra-large`).

`NSAppTransportSecurity › NSAllowsLocalNetworking` allows plain HTTP to local hosts only (localhost, `.local` names,
IP literals). Signing is automatic without a team, which is enough for the simulator; set your own
`DEVELOPMENT_TEAM` locally to run on a device.

## Layout

The `iosApp/` source folder is a synchronized folder (Xcode 16+ project format): files added there join the target
without editing `project.pbxproj`. `Info.plist` is excluded from the target membership because it is the
`INFOPLIST_FILE`, merged with the generated keys.

- `App/`: `@main` entry point (`doInitKoin` in `init`, with the device language; checks the embedded fonts in Debug),
  `AppConfiguration`, `RootView` (`NavigationStack` with the three screens; the catalog and list models live as long
  as the app), and `SwipeBackGesture` (the screens hide the navigation bar, so the edge swipe back is re-enabled).
- `DesignSystem/`: the common charter (`design/CHARTE.md`, mockups in `design/maquette/`), shared with Android:
  `Palette` (colors, light only), `Typography` (type scale with `Font.custom(_:size:relativeTo:)`, so it follows
  Dynamic Type), `Metrics` (spacing, radii, shadows), `Icons` (the SF Symbols of the charter and three mockup
  strokes), `Buttons` (primary, secondary, text, icon) and `Components` (top bar with « n / 12 », choice tiles,
  chips, checkboxes, the three steppers, hint, field, list blocks, snackbar).
- `Features/Home/`: « Accueil » (« Faire le point », « Ma liste » with its counts, catalog line at the bottom).
- `Features/Catalog/`: `CatalogStatusModel` (server status and local sync) and `CatalogStatusFooter`.
- `Features/Questionnaire/`: `QuestionSessionModel` (one per screen) and the stateless `QuestionContent`, which
  renders each `QuestionUiState` (meta questions, T1 list, product cards, gates, gate products, review, done, error).
  `SwipeableCard`: right = « Oui », left = « Non », duplicated by buttons and VoiceOver actions.
- `Features/List/`: `ShoppingListModel` and the « Ma liste » screen (sections as blocks, check, swipe to delete with
  a snackbar, add by search or free text, share, « Courses terminées »).
- `Debug/`: fake states for previews and demo screens (Debug only).
- `Resources/`: asset catalog (Basilic accent), `Localizable.xcstrings` (French source, English translation, plural
  variations) and `Fonts/` (Figtree and Bricolage Grotesque, static TTFs registered with `UIAppFonts` in Info.plist,
  with their OFL licenses).

The app stays in light mode (`.preferredColorScheme(.light)` at the root) until the charter has a dark palette.

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
