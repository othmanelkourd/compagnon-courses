import Shared
import SwiftUI

/// The catalog line at the bottom of « Accueil » (mockup `Main`): « 312 produits sur l'appareil », or « Préparation du
/// catalogue… » on the first launch. A plain line, not a button. Same states and texts as Android's `CatalogFooter`.
/// Stateless, for previews.
struct CatalogStatusFooter: View {
    let catalog: CatalogStatusUiState

    var body: some View {
        HStack(spacing: 8) {
            if catalog.ready {
                IconView(.check, size: 16, weight: .bold)
                    .foregroundStyle(Palette.basil)
                Text("\(Int(catalog.productCount)) produits sur l'appareil")
            } else {
                // The bundled catalog is being written (first launch): a moment, never the network.
                ProgressView()
                    .controlSize(.mini)
                    .tint(Palette.basil)
                Text("Préparation du catalogue…")
            }
        }
        .foregroundStyle(Palette.inkSoft)
        .textStyle(.caption)
        .multilineTextAlignment(.center)
        .padding(.horizontal, 12)
        .frame(maxWidth: .infinity, minHeight: Target.minimum)
        .accessibilityElement(children: .combine)
    }
}

#Preview("Installé") {
    CatalogStatusFooter(
        catalog: CatalogStatusUiState(ready: true, version: KotlinLong(value: 1), productCount: 312, categoryCount: 19)
    )
    .padding()
    .background(Palette.canvas)
}

#Preview("Premier lancement") {
    CatalogStatusFooter(catalog: CatalogStatusUiState(ready: false, version: nil, productCount: 0, categoryCount: 0))
        .padding()
        .background(Palette.canvas)
}
