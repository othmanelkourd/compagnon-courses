import OSLog
import SwiftUI
import UIKit

/// PostScript names of the embedded fonts (`Resources/Fonts`, registered with `UIAppFonts` in Info.plist).
enum FontName {
    static let figtreeRegular = "Figtree-Regular"
    static let figtreeMedium = "Figtree-Medium"
    static let figtreeSemiBold = "Figtree-SemiBold"
    static let figtreeBold = "Figtree-Bold"
    static let bricolageMedium = "BricolageGrotesque-36ptMedium"
    static let bricolageBold = "BricolageGrotesque-36ptBold"

    static let all = [figtreeRegular, figtreeMedium, figtreeSemiBold, figtreeBold, bricolageMedium, bricolageBold]

    /// Checks that every embedded font loads; a missing one falls back to the system font silently, so Debug builds
    /// stop on it. Called once at launch.
    static func verifyRegistered() {
        let logger = Logger(subsystem: "com.compagnon.courses", category: "fonts")
        let missing = all.filter { UIFont(name: $0, size: 17) == nil }
        if missing.isEmpty {
            logger.debug("Embedded fonts loaded: \(all.joined(separator: ", "), privacy: .public)")
        } else {
            logger.error("Embedded fonts missing: \(missing.joined(separator: ", "), privacy: .public)")
            assertionFailure("Embedded fonts missing: \(missing). Check UIAppFonts in Info.plist and Resources/Fonts.")
        }
    }
}

/// A style of the type scale (charter § 2): family and weight, size and line height in points at the default text
/// size, the Dynamic Type style it scales with, tracking and case.
struct TextStyle: Sendable {
    let fontName: String
    let size: CGFloat
    let lineHeight: CGFloat
    let relativeTo: Font.TextStyle
    var tracking: CGFloat = 0
    var uppercase = false

    var font: Font {
        .custom(fontName, size: size, relativeTo: relativeTo)
    }
}

extension TextStyle {
    /// Titre produit: « Tomates » on the card.
    static let productTitle = TextStyle(fontName: FontName.bricolageBold, size: 44, lineHeight: 44, relativeTo: .largeTitle, tracking: -0.8)
    /// Titre d'écran large: « Ma liste », category gate question.
    static let screenTitleLarge = TextStyle(fontName: FontName.bricolageBold, size: 34, lineHeight: 36, relativeTo: .largeTitle, tracking: -0.4)
    /// Titre d'écran: meta question, « 16 articles à ajouter ».
    static let screenTitle = TextStyle(fontName: FontName.bricolageBold, size: 32, lineHeight: 35, relativeTo: .title, tracking: -0.4)
    /// Titre de feuille: title of the options sheet.
    static let sheetTitle = TextStyle(fontName: FontName.bricolageBold, size: 24, lineHeight: 28, relativeTo: .title2)
    /// Question: « Il vous faut des tomates ? ».
    static let question = TextStyle(fontName: FontName.figtreeRegular, size: 20, lineHeight: 26, relativeTo: .title3)
    /// Valeur: value of a quantity choice.
    static let value = TextStyle(fontName: FontName.figtreeBold, size: 20, lineHeight: 24, relativeTo: .title3)
    /// Value of a large choice (meta questions, mockup `Meta`).
    static let valueLarge = TextStyle(fontName: FontName.figtreeBold, size: 28, lineHeight: 32, relativeTo: .title)
    /// Bouton principal: « Oui · 1 kg ».
    static let primaryButton = TextStyle(fontName: FontName.figtreeBold, size: 18, lineHeight: 22, relativeTo: .body)
    /// Title of the « Ma liste » card of the home screen (mockup `Main`).
    static let cardTitle = TextStyle(fontName: FontName.figtreeBold, size: 19, lineHeight: 24, relativeTo: .body)
    /// Ligne de liste: item name.
    static let listRow = TextStyle(fontName: FontName.figtreeSemiBold, size: 17, lineHeight: 22, relativeTo: .body)
    /// Corps: subtitles.
    static let body = TextStyle(fontName: FontName.figtreeRegular, size: 17, lineHeight: 23, relativeTo: .body)
    /// Corps compact: explanations.
    static let bodyCompact = TextStyle(fontName: FontName.figtreeRegular, size: 16, lineHeight: 22, relativeTo: .callout)
    /// Bouton texte: « Annuler », « Plus d'options ».
    static let textButton = TextStyle(fontName: FontName.figtreeSemiBold, size: 16, lineHeight: 20, relativeTo: .callout)
    /// Secondaire: quantity of a row, snackbar message.
    static let secondary = TextStyle(fontName: FontName.figtreeRegular, size: 15, lineHeight: 20, relativeTo: .subheadline)
    /// Secondary text in semibold (« 12 sur 16 restants », unit picker, stats of the home card).
    static let secondaryStrong = TextStyle(fontName: FontName.figtreeSemiBold, size: 15, lineHeight: 20, relativeTo: .subheadline)
    /// Aide: reason, hint.
    static let help = TextStyle(fontName: FontName.figtreeRegular, size: 14, lineHeight: 20, relativeTo: .subheadline)
    /// Compteur: « 5 / 12 ».
    static let counter = TextStyle(fontName: FontName.figtreeBold, size: 14, lineHeight: 18, relativeTo: .subheadline)
    /// Légende: line under an option.
    static let caption = TextStyle(fontName: FontName.figtreeRegular, size: 13, lineHeight: 17, relativeTo: .footnote)
    /// Swipe hints under the card (« glisser : non »).
    static let captionStrong = TextStyle(fontName: FontName.figtreeSemiBold, size: 13, lineHeight: 17, relativeTo: .footnote)
    /// Surtitre: capitals, +0.8: « FRUITS ET LÉGUMES ».
    static let overline = TextStyle(fontName: FontName.figtreeBold, size: 13, lineHeight: 16, relativeTo: .footnote, tracking: 0.8, uppercase: true)
    /// Chip: « Essentiel ».
    static let chip = TextStyle(fontName: FontName.figtreeBold, size: 12, lineHeight: 16, relativeTo: .caption)
    /// « habituel » under a selected choice.
    static let choiceNote = TextStyle(fontName: FontName.figtreeSemiBold, size: 12, lineHeight: 16, relativeTo: .caption)
}

private struct TextStyleModifier: ViewModifier {
    let style: TextStyle
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    func body(content: Content) -> some View {
        content
            .font(style.font)
            .tracking(style.tracking)
            .lineSpacing(lineSpacing)
            .textCase(style.uppercase ? .uppercase : nil)
    }

    /// The extra spacing that brings the font's natural line height to the style's, both scaled for the current
    /// text size.
    private var lineSpacing: CGFloat {
        let traits = UITraitCollection(preferredContentSizeCategory: UIContentSizeCategory(dynamicTypeSize))
        let metrics = UIFontMetrics(forTextStyle: style.relativeTo.uiTextStyle)
        let scaledSize = metrics.scaledValue(for: style.size, compatibleWith: traits)
        guard let font = UIFont(name: style.fontName, size: scaledSize) else { return 0 }
        let target = style.lineHeight * scaledSize / style.size
        return max(0, target - font.lineHeight)
    }
}

extension View {
    /// Applies a style of the type scale (font that follows Dynamic Type, line height, tracking, case).
    func textStyle(_ style: TextStyle) -> some View {
        modifier(TextStyleModifier(style: style))
    }
}

extension Font.TextStyle {
    var uiTextStyle: UIFont.TextStyle {
        switch self {
        case .largeTitle: .largeTitle
        case .title: .title1
        case .title2: .title2
        case .title3: .title3
        case .headline: .headline
        case .subheadline: .subheadline
        case .body: .body
        case .callout: .callout
        case .footnote: .footnote
        case .caption: .caption1
        case .caption2: .caption2
        @unknown default: .body
        }
    }
}
