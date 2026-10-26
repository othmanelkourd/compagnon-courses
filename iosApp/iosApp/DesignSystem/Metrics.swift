import SwiftUI

/// Spacing of the charter (§ 3): a 4-point grid.
enum Spacing {
    /// Side margin of a screen.
    static let screen: CGFloat = 20
    /// Side margin of the list blocks.
    static let block: CGFloat = 16
    /// Between two blocks.
    static let betweenBlocks: CGFloat = 14
    /// Inside a card.
    static let inCard: CGFloat = 12
    /// Under the safe area at the top of a screen.
    static let top: CGFloat = 8
}

/// Corner radii of the charter (§ 3).
enum Radius {
    static let card: CGFloat = 28
    static let sheet: CGFloat = 28
    static let block: CGFloat = 20
    static let homeListCard: CGFloat = 24
    static let button: CGFloat = 18
    static let tile: CGFloat = 16
    static let field: CGFloat = 16
    static let snackbar: CGFloat = 16
    static let stepper: CGFloat = 12
    static let stepperButton: CGFloat = 14
    static let optionTile: CGFloat = 12
    static let hint: CGFloat = 12
}

/// Minimum touch target (44 × 44 everywhere).
enum Target {
    static let minimum: CGFloat = 44
    /// Checkbox touch area.
    static let checkbox: CGFloat = 48
}

/// The shadows of the charter (§ 3). CSS `0 y blur rgba(…)` becomes `radius: blur / 2`.
enum Elevation {
    /// Product card: 0 8 24 rgba(23,33,27,0.08).
    case card
    /// « Faire le point » card: 0 10 24 rgba(30,107,69,0.25).
    case accentCard
    /// Category gate card: 0 12 28 rgba(23,33,27,0.18).
    case darkCard
    /// Snackbar: 0 10 24 rgba(23,33,27,0.25).
    case snackbar

    fileprivate var color: Color {
        switch self {
        case .card: Palette.ink.opacity(0.08)
        case .accentCard: Palette.basil.opacity(0.25)
        case .darkCard: Palette.ink.opacity(0.18)
        case .snackbar: Palette.ink.opacity(0.25)
        }
    }

    fileprivate var y: CGFloat {
        switch self {
        case .card: 8
        case .accentCard, .snackbar: 10
        case .darkCard: 12
        }
    }

    fileprivate var radius: CGFloat {
        switch self {
        case .card, .accentCard, .snackbar: 12
        case .darkCard: 14
        }
    }
}

extension View {
    func elevation(_ elevation: Elevation) -> some View {
        shadow(color: elevation.color, radius: elevation.radius, x: 0, y: elevation.y)
    }
}

extension View {
    /// A filled rounded background with an optional border, its shadow cast by the shape only (not by the content).
    func cardBackground(_ color: Color, radius: CGFloat, border: Color? = nil, elevation: Elevation? = nil) -> some View {
        background {
            let shape = RoundedRectangle(cornerRadius: radius)
            ZStack {
                if let elevation {
                    shape.fill(color).elevation(elevation)
                } else {
                    shape.fill(color)
                }
                if let border {
                    shape.strokeBorder(border, lineWidth: 1)
                }
            }
        }
    }
}
