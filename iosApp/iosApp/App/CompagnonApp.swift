import Shared
import SwiftUI

@main
struct CompagnonApp: App {
    init() {
        let configuration = AppConfiguration.load()
        // Starts the shared Koin graph (database, HTTP client in Debug, repositories, ViewModels) before any view exists.
        // The device language picks the catalog locale (French, otherwise English).
        doInitKoin(
            baseUrl: configuration.apiBaseURL,
            appVersion: configuration.appVersion,
            deviceLanguage: Locale.current.identifier
        )
        FontName.verifyRegistered()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                #if DEBUG
                    // `-DemoScreen card` (etc.) shows one screen with a fake state, for screenshots and design checks.
                    if let demo = DemoScreen.fromLaunchArguments() {
                        DemoScreenView(screen: demo)
                    } else {
                        RootView()
                    }
                #else
                    RootView()
                #endif
            }
            // The charter has no dark palette yet (CHARTE.md § 1): the app stays light.
            .preferredColorScheme(.light)
            .tint(Palette.basil)
        }
    }
}
