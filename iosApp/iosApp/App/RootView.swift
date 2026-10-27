import Shared
import SwiftUI

/// The screens pushed on top of « Accueil ».
enum AppRoute: Hashable {
    case questionnaire
    case list
    /// « Mon foyer »: the onboarding in edit mode, one model per visit.
    case household
}

/// The root: a plain background while the household is read, the first-run onboarding on the first launch, then the
/// navigation stack « Accueil » → « Questionnaire », « Liste » and « Mon foyer » (`AppGateModel`).
///
/// The catalog and list models live as long as the app: « Accueil » shows the item count of the list, and the
/// list screen opens with its content already loaded. Their observation tasks hang off the stack itself, so they
/// keep running while another screen is pushed (and only start once the app is shown: during the first-run
/// onboarding, the onboarding downloads the catalog itself). The questionnaire model belongs to its screen (one
/// session per visit; leaving keeps the session open and the next visit resumes it).
struct RootView: View {
    @State private var gate = AppGateModel()
    @State private var path: [AppRoute] = []
    @State private var catalog = CatalogStatusModel(country: "FR")
    @State private var list = ShoppingListModel()

    var body: some View {
        ZStack {
            switch onEnum(of: gate.state) {
            case .loading:
                Palette.canvas
                    .ignoresSafeArea()
            case .needsOnboarding:
                OnboardingScreen(mode: .firstRun) { exit in
                    if exit == .startSession {
                        path = [.questionnaire]
                    }
                    gate.send(AppGateEventOnboardingClosed.shared)
                }
                .transition(.opacity)
            case .ready(let ready):
                app(household: ready.summary)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: phase)
        .task {
            await gate.observe()
        }
    }

    /// The gate state as a value, for the cross-fade between the onboarding and the app.
    private var phase: Int {
        switch onEnum(of: gate.state) {
        case .loading: 0
        case .needsOnboarding: 1
        case .ready: 2
        }
    }

    private func app(household: HouseholdSummaryUi) -> some View {
        NavigationStack(path: $path) {
            HomeView(
                catalog: catalog,
                list: list,
                household: household,
                onStartSession: { path.append(.questionnaire) },
                onOpenList: { path.append(.list) },
                onOpenHousehold: { path.append(.household) }
            )
            .navigationDestination(for: AppRoute.self) { route in
                switch route {
                case .questionnaire:
                    QuestionnaireScreen(onShowList: { path = [.list] }, onClose: { path.removeAll() })
                case .list:
                    ShoppingListScreen(model: list, onStartSession: { path = [.questionnaire] })
                case .household:
                    OnboardingScreen(mode: .edit) { exit in
                        switch exit {
                        case .startSession:
                            path = [.questionnaire]
                        case .home:
                            if path.last == .household {
                                path.removeLast()
                            }
                        }
                    }
                }
            }
        }
        .task {
            await catalog.observe()
        }
        .task {
            await list.observe()
        }
    }
}
