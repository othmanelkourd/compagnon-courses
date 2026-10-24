import SwiftUI

/// The screens pushed on top of « Accueil ».
enum AppRoute: Hashable {
    case questionnaire
    case list
}

/// The navigation root: « Accueil », then « Questionnaire » and « Liste ».
///
/// The catalog and list models live as long as the app: « Accueil » shows the item count of the list, and the
/// list screen opens with its content already loaded. Their observation tasks hang off the stack itself, so they
/// keep running while another screen is pushed. The questionnaire model belongs to its screen (one session per
/// visit; leaving keeps the session open and the next visit resumes it).
struct RootView: View {
    @State private var path: [AppRoute] = []
    @State private var catalog = CatalogStatusModel(country: "FR")
    @State private var list = ShoppingListModel()

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(
                catalog: catalog,
                list: list,
                onStartSession: { path.append(.questionnaire) },
                onOpenList: { path.append(.list) }
            )
            .navigationDestination(for: AppRoute.self) { route in
                switch route {
                case .questionnaire:
                    QuestionnaireScreen(onShowList: { path = [.list] }, onClose: { path.removeAll() })
                case .list:
                    ShoppingListScreen(model: list, onStartSession: { path = [.questionnaire] })
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
