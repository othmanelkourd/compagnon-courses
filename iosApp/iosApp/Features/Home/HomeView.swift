import Shared
import SwiftUI

/// « Accueil »: start a question session, open the list, and the catalog status.
struct HomeView: View {
    let catalog: CatalogStatusModel
    let list: ShoppingListModel
    let onStartSession: () -> Void
    let onOpenList: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                StartSessionCard(action: onStartSession)
                MyListCard(state: list.state, action: onOpenList)
                CatalogStatusCard(state: catalog.state, sync: catalog.sync)
            }
            .padding()
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Compagnon de courses")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Synchroniser le catalogue", systemImage: "arrow.clockwise") {
                    catalog.refresh()
                }
                .disabled(catalog.isBusy)
            }
        }
        .refreshable {
            await catalog.refreshAndWait()
        }
    }
}

/// The main action: a big, tinted card.
private struct StartSessionCard: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: "checklist")
                    .font(.largeTitle)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Faire le point")
                        .font(.title2.bold())
                    Text("Quelques questions, et votre liste est prête.")
                        .font(.subheadline)
                        .opacity(0.9)
                }
                .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.headline)
                    .accessibilityHidden(true)
            }
            .foregroundStyle(.white)
            .padding(20)
            .frame(maxWidth: .infinity, minHeight: 96)
            .background(Color.accentColor.gradient, in: .rect(cornerRadius: 20))
            .contentShape(.rect(cornerRadius: 20))
        }
        .buttonStyle(.plain)
        .accessibilityHint("Démarre une série de questions pour préparer la liste")
    }
}

private struct MyListCard: View {
    let state: any ShoppingListUiState
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: "cart")
                    .font(.title2)
                    .foregroundStyle(.tint)
                    .frame(width: 36)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Ma liste")
                        .font(.headline)
                    summary
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
            .padding()
            .frame(maxWidth: .infinity, minHeight: 72)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: .rect(cornerRadius: 16))
            .contentShape(.rect(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var summary: some View {
        if let loaded = state as? ShoppingListUiStateLoaded {
            if loaded.itemCount == 0 {
                Text("Vide pour l'instant")
            } else if loaded.remainingCount == 0 {
                Text("\(Int(loaded.itemCount)) articles, tous cochés")
            } else {
                Text("\(Int(loaded.remainingCount)) articles à acheter")
            }
        } else if state is ShoppingListUiStateError {
            Text("Liste indisponible")
        } else {
            Text("Chargement…")
        }
    }
}
