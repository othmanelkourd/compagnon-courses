import SwiftUI

/// The icons of the charter (§ 4): the SF Symbols of its table, turned when a direction is missing (left arrow,
/// back and down chevrons), and a few strokes of the mockup (`Options.dc.html`, `Onboarding-*.dc.html`,
/// `Household-Edit.dc.html`) drawn as shapes. No other icon is used.
enum Icon {
    case close, plus, minus, menu, undo, check
    case arrowRight, arrowLeft
    case chevronRight, chevronLeft, chevronDown
    case info, house, basket, clock, list, search, share, calendar, nosign
    /// « Il en reste un peu »: three shrinking lines (mockup path `M4 18h16M6 14h12M9 10h6`).
    case littleLeft
    /// « J'en ai pour une semaine »: the mockup's calendar stroke (`Options.dc.html`), drawn like `calendarMonth`.
    case calendarWeek
    /// « J'en ai pour un mois »: a calendar with a bar (mockup path, `Options.dc.html`).
    case calendarMonth
    /// « Vos réponses restent sur votre téléphone »: a padlock (mockup `Onboarding-Welcome`).
    case lock
    /// « Alimentation » in « Mon foyer »: a drop with a bar (mockup `Household-Edit`).
    case food

    fileprivate var symbol: (name: String, degrees: Double)? {
        switch self {
        case .close: ("xmark", 0)
        case .plus: ("plus", 0)
        case .minus: ("minus", 0)
        case .menu: ("ellipsis", 0)
        case .undo: ("arrow.uturn.backward", 0)
        case .check: ("checkmark", 0)
        case .arrowRight: ("arrow.right", 0)
        case .arrowLeft: ("arrow.right", 180)
        case .chevronRight: ("chevron.right", 0)
        case .chevronLeft: ("chevron.right", 180)
        case .chevronDown: ("chevron.right", 90)
        case .info: ("info.circle", 0)
        case .house: ("house", 0)
        case .basket: ("basket", 0)
        case .clock: ("clock", 0)
        case .list: ("list.bullet", 0)
        case .search: ("magnifyingglass", 0)
        case .share: ("square.and.arrow.up", 0)
        case .calendar: ("calendar", 0)
        case .nosign: ("nosign", 0)
        case .littleLeft, .calendarWeek, .calendarMonth, .lock, .food: nil
        }
    }
}

/// An icon of `size` points, stroked like the mockup (about 2 pt, round caps). Decorative: hidden from VoiceOver.
struct IconView: View {
    let icon: Icon
    var size: CGFloat = 20
    var weight: Font.Weight = .semibold

    init(_ icon: Icon, size: CGFloat = 20, weight: Font.Weight = .semibold) {
        self.icon = icon
        self.size = size
        self.weight = weight
    }

    var body: some View {
        Group {
            if let symbol = icon.symbol {
                Image(systemName: symbol.name)
                    .font(.system(size: size * 0.86, weight: weight))
                    .rotationEffect(.degrees(symbol.degrees))
            } else {
                MockupStroke(icon: icon)
                    .stroke(style: StrokeStyle(lineWidth: 2 * size / 24, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// The strokes of the mockup that have no SF Symbol in the charter, in a 24 × 24 grid.
private struct MockupStroke: Shape {
    let icon: Icon

    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / 24
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * scale, y: rect.minY + y * scale)
        }
        var path = Path()
        func line(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat) {
            path.move(to: point(x1, y1))
            path.addLine(to: point(x2, y2))
        }
        switch icon {
        case .littleLeft:
            line(4, 18, 20, 18)
            line(6, 14, 18, 14)
            line(9, 10, 15, 10)
        case .calendarWeek, .calendarMonth:
            line(8, 3, 8, 6)
            line(16, 3, 16, 6)
            line(4, 9, 20, 9)
            path.addRect(CGRect(origin: point(5, 6), size: CGSize(width: 14 * scale, height: 14 * scale)))
            if case .calendarMonth = icon {
                line(9, 14, 15, 14)
            }
        case .lock:
            // rect x5 y11 14 × 10 rx2, then M8 11V8a4 4 0 0 1 8 0v3.
            path.addRoundedRect(in: CGRect(origin: point(5, 11), size: CGSize(width: 14 * scale, height: 10 * scale)),
                                cornerSize: CGSize(width: 2 * scale, height: 2 * scale))
            path.move(to: point(8, 11))
            path.addLine(to: point(8, 8))
            path.addCurve(to: point(12, 4), control1: point(8, 5.79), control2: point(9.79, 4))
            path.addCurve(to: point(16, 8), control1: point(14.21, 4), control2: point(16, 5.79))
            path.addLine(to: point(16, 11))
        case .food:
            // M12 21c4-3 7-6.5 7-11a7 7 0 0 0-14 0c0 4.5 3 8 7 11zM12 7v6.
            path.move(to: point(12, 21))
            path.addCurve(to: point(19, 10), control1: point(16, 18), control2: point(19, 14.5))
            path.addCurve(to: point(12, 3), control1: point(19, 6.13), control2: point(15.87, 3))
            path.addCurve(to: point(5, 10), control1: point(8.13, 3), control2: point(5, 6.13))
            path.addCurve(to: point(12, 21), control1: point(5, 14.5), control2: point(8, 18))
            path.closeSubpath()
            line(12, 7, 12, 13)
        default:
            break
        }
        return path
    }
}

/// The Ocimio icon (`design/brand/ocimio-icon.svg`, mockup `Onboarding-Welcome`): a white leaf with a check on a
/// Basilic rounded square. Decorative: VoiceOver reads the name of the app.
struct OcimioLogo: View {
    var size: CGFloat = 88

    var body: some View {
        Canvas { context, canvasSize in
            let scale = canvasSize.width / 100
            func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * scale, y: y * scale) }
            context.fill(Path(roundedRect: CGRect(origin: .zero, size: canvasSize), cornerRadius: 24 * scale), with: .color(Palette.basil))
            var leaf = Path()
            leaf.move(to: point(50, 10))
            leaf.addCurve(to: point(72, 68), control1: point(70, 22), control2: point(84, 46))
            leaf.addCurve(to: point(50, 86), control1: point(66, 80), control2: point(58, 86))
            leaf.addCurve(to: point(28, 68), control1: point(42, 86), control2: point(34, 80))
            leaf.addCurve(to: point(50, 10), control1: point(16, 46), control2: point(30, 22))
            leaf.closeSubpath()
            context.fill(leaf, with: .color(Palette.surface))
            var stem = Path()
            stem.move(to: point(50, 86))
            stem.addLine(to: point(50, 93))
            context.stroke(stem, with: .color(Palette.surface), style: StrokeStyle(lineWidth: 4 * scale, lineCap: .round))
            var check = Path()
            check.move(to: point(36, 54))
            check.addLine(to: point(46, 64))
            check.addLine(to: point(65, 41))
            context.stroke(check, with: .color(Palette.basil), style: StrokeStyle(lineWidth: 6 * scale, lineCap: .round, lineJoin: .round))
        }
        .frame(width: size, height: size)
        .accessibilityElement()
        .accessibilityLabel(Text(verbatim: "Ocimio"))
        .accessibilityAddTraits(.isImage)
    }
}
