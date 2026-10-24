# iosApp

SwiftUI app « Compagnon » (bundle id `com.compagnon.courses`, iOS 17+, Swift 6). The UI is native; the state and logic come
from the shared Kotlin code through the static `Shared` framework (SKIE), see `shared/umbrella/README.md`.

## Build and run

Open `iosApp.xcodeproj`, pick the `iosApp` scheme and a simulator, then run. Start the server first
(`./gradlew :server:app:run`): in Debug the app calls `http://localhost:8080`, which the simulator shares with the Mac.

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

`NSAppTransportSecurity › NSAllowsLocalNetworking` allows plain HTTP to local hosts only (localhost, `.local` names,
IP literals). Signing is automatic without a team, which is enough for the simulator; set your own
`DEVELOPMENT_TEAM` locally to run on a device.

## Layout

The `iosApp/` source folder is a synchronized folder (Xcode 16+ project format): files added there join the target
without editing `project.pbxproj`. `Info.plist` is excluded from the target membership because it is the
`INFOPLIST_FILE`, merged with the generated keys.

- `App/`: `@main` entry point (`doInitKoin` in `init`) and `AppConfiguration`.
- `Features/Catalog/`: `CatalogStatusModel` (`@Observable` wrapper that owns the shared `CatalogStatusViewModel` and
  clears it on deinit) and `CatalogStatusView`.
- `Resources/`: asset catalog and `Localizable.xcstrings` (French source, English translation).
