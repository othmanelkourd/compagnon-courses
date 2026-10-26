import SwiftUI

/// The colors of the charter (`design/CHARTE.md` § 1), light mode only: the app forces `.preferredColorScheme(.light)`
/// at its root until a dark palette is designed. The accent color of the asset catalog is `basil`.
enum Palette {
    /// Basilic: primary action, selection, progress.
    static let basil = Color(hex: 0x1E6B45)
    /// Basilic foncé: pressed primary button, text of a selected choice.
    static let basilDark = Color(hex: 0x15533A)
    /// Basilic clair: selected choice, positive chips, tiles.
    static let basilLight = Color(hex: 0xE3F0E7)
    /// Encre: main text, snackbar background.
    static let ink = Color(hex: 0x17211B)
    /// Encre douce: secondary text, reasons, overlines.
    static let inkSoft = Color(hex: 0x4A554E)
    /// Encre discrète: disabled text, checked item, « Retirer » icon.
    static let inkMuted = Color(hex: 0x6B756E)
    /// Fond: screen background, option groups of the sheet.
    static let canvas = Color(hex: 0xF5F6F1)
    /// Surface: cards, lists, sheets, fields.
    static let surface = Color(hex: 0xFFFFFF)
    /// Surface douce: steppers, unit picker, hint, « soft » icon button.
    static let surfaceSoft = Color(hex: 0xF1F3EE)
    /// Trait: borders of cards, fields and list blocks.
    static let line = Color(hex: 0xE2E5DE)
    /// Trait léger: separators between the rows of a block.
    static let lineLight = Color(hex: 0xEDEFEA)
    /// Trait fort: border of the secondary button, sheet handle.
    static let lineStrong = Color(hex: 0xCBD1C7)
    /// Segment vide: progress segments not reached yet.
    static let segmentEmpty = Color(hex: 0xDCE1D8)
    /// Contour case: border of an empty checkbox.
    static let checkboxBorder = Color(hex: 0xAEB6AC)
    /// Carte sombre: the category gate card.
    static let darkCard = Color(hex: 0x1F3A2C)
    /// Tomate: rare, « à surveiller » dot.
    static let tomato = Color(hex: 0xD9482B)
    /// Tomate texte: text and icon of « Je n'en achète plus », « À surveiller » chip.
    static let tomatoText = Color(hex: 0xB8361C)
    /// Tomate clair: tile of « Je n'en achète plus », « À surveiller » chip.
    static let tomatoLight = Color(hex: 0xFBE7E2)
    /// Libellé « Annuler » of the snackbar.
    static let snackbarAction = Color(hex: 0x9BD8B3)
    /// Tile of the « Pause » answers of the options sheet (`#EEF0EA`, charter § 4).
    static let pause = Color(hex: 0xEEF0EA)
}

extension Color {
    /// An opaque sRGB color from `0xRRGGBB`.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1
        )
    }
}
