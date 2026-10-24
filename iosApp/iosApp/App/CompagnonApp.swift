import Shared
import SwiftUI

@main
struct CompagnonApp: App {
    init() {
        let configuration = AppConfiguration.load()
        // Starts the shared Koin graph (HTTP client, CatalogApi, ViewModels) before any view is created.
        doInitKoin(baseUrl: configuration.apiBaseURL, appVersion: configuration.appVersion)
    }

    var body: some Scene {
        WindowGroup {
            CatalogStatusView()
        }
    }
}
