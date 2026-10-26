import Shared
import SwiftUI

/// The catalog line at the bottom of « Accueil » (mockup `Main`): « Catalogue à jour · 312 produits », or the sync /
/// offline state, on one line. Tapping it syncs the catalog again (as does pulling the screen down). Same states and
/// texts as Android's `CatalogFooter`. Stateless, for previews.
struct CatalogStatusFooter: View {
    let sync: CatalogSyncUiState
    var busy = false
    var onRefresh: () -> Void = {}

    private enum Tone { case ready, syncing, offline, problem }

    var body: some View {
        Button(action: onRefresh) {
            HStack(spacing: 8) {
                switch tone {
                case .syncing:
                    ProgressView()
                        .controlSize(.mini)
                        .tint(Palette.basil)
                case .ready:
                    IconView(.check, size: 16, weight: .bold)
                        .foregroundStyle(Palette.basil)
                case .offline:
                    IconView(.info, size: 16, weight: .bold)
                        .foregroundStyle(Palette.inkSoft)
                case .problem:
                    IconView(.info, size: 16, weight: .bold)
                        .foregroundStyle(Palette.tomatoText)
                }
                statusText
                    .foregroundStyle(tone == .problem ? Palette.tomatoText : Palette.inkSoft)
            }
            .textStyle(.caption)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity, minHeight: Target.minimum)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .disabled(busy || sync.syncing)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Mettre à jour le catalogue")
    }

    private var tone: Tone {
        if sync.syncing { return .syncing }
        if sync.lastOutcome == .failedStorage || sync.localVersion == nil { return .problem }
        if sync.lastOutcome == .failedNetwork { return .offline }
        return .ready
    }

    private var statusText: Text {
        let count = Int(sync.productCount)
        if sync.syncing {
            return Text("Mise à jour du catalogue…")
        }
        if sync.lastOutcome == .failedStorage {
            return Text("Le catalogue n'a pas pu être enregistré sur l'appareil")
        }
        if sync.localVersion == nil {
            if sync.lastOutcome == .failedNetwork {
                return Text("Hors ligne\u{00A0}: connectez-vous pour télécharger le catalogue")
            }
            return Text("Pas encore de catalogue sur l'appareil")
        }
        if sync.lastOutcome == .failedNetwork {
            return Text("Hors ligne · \(count) produits sur l'appareil")
        }
        return Text("Catalogue à jour · \(count) produits")
    }
}

#Preview("À jour") {
    CatalogStatusFooter(
        sync: CatalogSyncUiState(
            syncing: false, localVersion: KotlinLong(value: 3), lastSyncedAt: "2026-10-24T08:30:00Z",
            productCount: 312, categoryCount: 18, lastOutcome: .upToDate
        )
    )
    .padding()
    .background(Palette.canvas)
}

#Preview("Hors ligne") {
    CatalogStatusFooter(
        sync: CatalogSyncUiState(
            syncing: false, localVersion: KotlinLong(value: 2), lastSyncedAt: "2026-10-20T08:30:00Z",
            productCount: 300, categoryCount: 18, lastOutcome: .failedNetwork
        )
    )
    .padding()
    .background(Palette.canvas)
}
