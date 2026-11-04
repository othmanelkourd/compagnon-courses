import Shared
import SwiftUI

/// The catalog line at the bottom of « Accueil » (mockup `Main`): « Catalogue à jour · 312 produits », or the sync /
/// offline state, on one line. Tapping it syncs the catalog again (as does pulling the screen down), except in a build
/// without server. Same states and texts as Android's `CatalogFooter`. Stateless, for previews.
struct CatalogStatusFooter: View {
    let sync: CatalogSyncUiState
    var busy = false
    var onRefresh: () -> Void = {}

    private enum Tone { case ready, syncing, offline, problem }

    var body: some View {
        if sync.canCheck {
            Button(action: onRefresh) {
                label
            }
            .buttonStyle(.plain)
            .disabled(busy || sync.syncing)
            .accessibilityElement(children: .combine)
            .accessibilityHint("Mettre à jour le catalogue")
        } else {
            // A build without server: a plain line, not a dimmed button.
            label
                .accessibilityElement(children: .combine)
        }
    }

    private var label: some View {
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

    private var tone: Tone {
        if sync.syncing { return .syncing }
        if sync.localVersion == nil { return .syncing }
        if sync.lastOutcome == .failedStorage { return .problem }
        if !sync.canCheck { return .ready }
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
            // The bundled catalog is being written (first launch): a moment, never the network.
            return Text("Préparation du catalogue…")
        }
        if !sync.canCheck || sync.lastOutcome == .failedNetwork {
            // A build without server, or the server did not answer: the device's catalog is used as is.
            return Text("\(count) produits sur l'appareil")
        }
        return Text("Catalogue à jour · \(count) produits")
    }
}

#Preview("À jour") {
    CatalogStatusFooter(
        sync: CatalogSyncUiState(
            syncing: false, localVersion: KotlinLong(value: 3), lastSyncedAt: "2026-10-24T08:30:00Z",
            productCount: 312, categoryCount: 18, lastOutcome: .upToDate, canCheck: true
        )
    )
    .padding()
    .background(Palette.canvas)
}

#Preview("Serveur injoignable") {
    CatalogStatusFooter(
        sync: CatalogSyncUiState(
            syncing: false, localVersion: KotlinLong(value: 2), lastSyncedAt: "2026-10-20T08:30:00Z",
            productCount: 300, categoryCount: 18, lastOutcome: .failedNetwork, canCheck: true
        )
    )
    .padding()
    .background(Palette.canvas)
}

#Preview("Sans serveur") {
    CatalogStatusFooter(
        sync: CatalogSyncUiState(
            syncing: false, localVersion: KotlinLong(value: 1), lastSyncedAt: "2026-10-24T08:30:00Z",
            productCount: 312, categoryCount: 19, lastOutcome: nil, canCheck: false
        )
    )
    .padding()
    .background(Palette.canvas)
}
