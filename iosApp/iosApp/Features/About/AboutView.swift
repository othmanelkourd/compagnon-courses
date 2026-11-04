import Shared
import SwiftUI

/// « À propos »: the app and its version, the catalog of the device, what happens to the data, links and licenses.
/// Same content as Android's `AboutScreen`. A link row is hidden while its address is empty (`AboutLinks`).
struct AboutView: View {
    let catalogSync: CatalogSyncUiState
    let onOpenLicenses: () -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    private var version: String {
        let info = Bundle.main.infoDictionary
        let marketing = info?["CFBundleShortVersionString"] as? String ?? "?"
        let build = info?["CFBundleVersion"] as? String ?? "?"
        return "\(marketing) (\(build))"
    }

    private var privacyPolicyURL: URL? {
        let address = AboutLinks.shared.PRIVACY_POLICY_URL
        return address.isEmpty ? nil : URL(string: address)
    }

    private var supportMailURL: URL? {
        let address = AboutLinks.shared.SUPPORT_EMAIL
        return address.isEmpty ? nil : URL(string: "mailto:\(address)")
    }

    var body: some View {
        AboutScaffold(title: Text("À propos"), onBack: { dismiss() }) {
            VStack(spacing: 6) {
                Image("OcimioLogo")
                    .resizable()
                    .frame(width: 72, height: 72)
                    .accessibilityHidden(true)
                Text(verbatim: "Ocimio")
                    .textStyle(.sheetTitle)
                    .foregroundStyle(Palette.ink)
                Text("Version \(version)")
                    .textStyle(.secondary)
                    .foregroundStyle(Palette.inkSoft)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)

            BlockSection(title: Text("Vos données")) {
                TextBlock(text: Text("""
                    Tout reste sur votre téléphone\u{00A0}: Ocimio n'a pas de compte, ne se connecte pas à internet et \
                    ne collecte aucune donnée. Vos listes et votre historique font partie de la sauvegarde de votre \
                    téléphone.
                    """))
            }

            if let localVersion = catalogSync.localVersion {
                BlockSection(title: Text("Catalogue")) {
                    TextBlock(text: Text("\(Int(catalogSync.productCount)) produits") + Text(verbatim: " · ")
                        + Text("version \(localVersion.int64Value)"))
                }
            }

            BlockSection(title: Text("Plus")) {
                VStack(spacing: 0) {
                    if let url = privacyPolicyURL {
                        LinkRow(text: Text("Politique de confidentialité")) { openURL(url) }
                        BlockDivider()
                    }
                    if let url = supportMailURL {
                        LinkRow(text: Text("Nous écrire")) { openURL(url) }
                        BlockDivider()
                    }
                    LinkRow(text: Text("Licences open source"), action: onOpenLicenses)
                }
                .blockStyle()
            }
        }
    }
}

/// The frame of « À propos » and « Licences »: back button, title, scrolling content.
struct AboutScaffold<Content: View>: View {
    let title: Text
    let onBack: () -> Void
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                IconButton(icon: .chevronLeft, label: Text("Retour"), action: onBack)
                title
                    .textStyle(.sheetTitle)
                    .foregroundStyle(Palette.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.top, Spacing.top)
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    content
                }
                .padding(.horizontal, Spacing.screen)
                .padding(.top, 8)
                .padding(.bottom, 28)
            }
        }
        .background(Palette.canvas)
        .toolbar(.hidden, for: .navigationBar)
    }
}

/// A paragraph in a list block.
private struct TextBlock: View {
    let text: Text

    var body: some View {
        text
            .textStyle(.bodyCompact)
            .foregroundStyle(Palette.ink)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .blockStyle()
    }
}

/// A row that opens something: its label and a chevron, 56 high.
private struct LinkRow: View {
    let text: Text
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                text
                    .textStyle(.listRow)
                    .foregroundStyle(Palette.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                IconView(.chevronRight, size: 16)
                    .foregroundStyle(Palette.inkSoft)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 56)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }
}

/// The separator between the rows of a block (Trait léger).
struct BlockDivider: View {
    var body: some View {
        Rectangle()
            .fill(Palette.lineLight)
            .frame(height: 1)
    }
}

extension View {
    /// Bloc de liste: Surface, Trait border, radius 20.
    func blockStyle() -> some View {
        background(Palette.surface, in: .rect(cornerRadius: Radius.block))
            .clipShape(.rect(cornerRadius: Radius.block))
            .overlay { RoundedRectangle(cornerRadius: Radius.block).strokeBorder(Palette.line, lineWidth: 1) }
    }
}
