import SwiftUI

// MARK: - Halo

/// Halo: Basilic clair fading to transparent, a disc as wide as its frame (CSS `radial-gradient(closest-side, …)`).
struct Halo: View {
    var opacity: Double = 0.95

    var body: some View {
        EllipticalGradient(
            colors: [Palette.basilLight.opacity(opacity), Palette.basilLight.opacity(0)],
            center: .center,
            startRadiusFraction: 0,
            endRadiusFraction: 0.5
        )
        .accessibilityHidden(true)
    }
}

/// One halo of a screen, placed like the mockup: `alignment` is the corner (or edge) it hangs from and `x`, `y` move
/// it from there (CSS `top: -170px; right: -150px` is `.topTrailing`, x 150, y -170).
struct HaloSpot: Hashable {
    let alignment: HaloAlignment
    let x: CGFloat
    let y: CGFloat
    let size: CGFloat
    var opacity: Double = 0.95

    enum HaloAlignment: Hashable {
        case top, topLeading, topTrailing, bottomLeading

        var alignment: Alignment {
            switch self {
            case .top: .top
            case .topLeading: .topLeading
            case .topTrailing: .topTrailing
            case .bottomLeading: .bottomLeading
            }
        }
    }

    /// The halo of the onboarding steps and « Mon foyer » (top right).
    static let step = HaloSpot(alignment: .topTrailing, x: 170, y: -170, size: 440)
}

/// The Fond of a screen with its halos, under the safe areas, clipped to the screen (no halo over the previous
/// screen during a push).
struct HaloBackground: View {
    let spots: [HaloSpot]

    var body: some View {
        ZStack {
            Palette.canvas
            ForEach(spots, id: \.self) { spot in
                Halo(opacity: spot.opacity)
                    .frame(width: spot.size, height: spot.size)
                    .offset(x: spot.x, y: spot.y)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: spot.alignment.alignment)
            }
        }
        .clipped()
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

// MARK: - Leaf pattern

/// Motif feuilles: basil leaves drawn with a 1.6 stroke, as a watermark. The leaves are placed in the coordinates of
/// the mockup's SVG (`size`), each with its translation, rotation (degrees) and scale.
struct LeafPattern: View {
    struct Leaf {
        let x: CGFloat
        let y: CGFloat
        let rotation: Double
        var scale: CGFloat = 1
    }

    let size: CGSize
    let leaves: [Leaf]
    let color: Color

    var body: some View {
        LeafPatternShape(leaves: leaves)
            .fill(color)
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    /// « Faire le point » (230 × 212, from the top right corner), white at 12 %.
    static let homeCard = LeafPattern(size: CGSize(width: 230, height: 212), leaves: [
        Leaf(x: 180, y: 34, rotation: 28),
        Leaf(x: 118, y: 18, rotation: -24, scale: 0.7),
        Leaf(x: 214, y: 108, rotation: 64, scale: 0.8),
        Leaf(x: 150, y: 90, rotation: -8, scale: 0.55),
    ], color: Palette.surface.opacity(0.12))

    /// The band of the product card (306 × 112, from the top left corner), Basilic at 10 %.
    static let cardBand = LeafPattern(size: CGSize(width: 306, height: 112), leaves: [
        Leaf(x: 36, y: 30, rotation: -30),
        Leaf(x: 270, y: 78, rotation: 40, scale: 0.8),
        Leaf(x: 250, y: 22, rotation: -60, scale: 0.55),
    ], color: Palette.basil.opacity(0.10))

    /// The dark aisle card (350 × 200, from the top right corner), white at 8 %.
    static let gateCard = LeafPattern(size: CGSize(width: 350, height: 200), leaves: [
        Leaf(x: 300, y: 40, rotation: 30),
        Leaf(x: 240, y: 120, rotation: -20, scale: 0.75),
        Leaf(x: 330, y: 150, rotation: 70, scale: 0.6),
        Leaf(x: 200, y: 30, rotation: -50, scale: 0.5),
    ], color: Palette.surface.opacity(0.08))

    /// The welcome screen (250 × 230, 40 below the top right corner), Basilic at 9 %.
    static let welcome = LeafPattern(size: CGSize(width: 250, height: 230), leaves: [
        Leaf(x: 40, y: 40, rotation: -30),
        Leaf(x: 222, y: 196, rotation: 50, scale: 0.8),
        Leaf(x: 120, y: 210, rotation: -70, scale: 0.6),
    ], color: Palette.basil.opacity(0.09))
}

/// The basil leaf of the charter in a 32 × 48 box centered on the origin: `M0-22C13-13 15 9 0 22C-15 9-13-13 0-22Z`.
enum LeafPath {
    nonisolated static let outline: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: -22))
        path.addCurve(to: CGPoint(x: 0, y: 22), control1: CGPoint(x: 13, y: -13), control2: CGPoint(x: 15, y: 9))
        path.addCurve(to: CGPoint(x: 0, y: -22), control1: CGPoint(x: -15, y: 9), control2: CGPoint(x: -13, y: -13))
        path.closeSubpath()
        return path
    }()

    /// The midrib, `M0-18V20`.
    nonisolated static let rib: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: -18))
        path.addLine(to: CGPoint(x: 0, y: 20))
        return path
    }()
}

/// The outlines of the pattern's leaves, stroked before they are placed so that the stroke scales with each leaf
/// (as in the SVG).
private struct LeafPatternShape: Shape {
    let leaves: [LeafPattern.Leaf]

    func path(in rect: CGRect) -> Path {
        let style = StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)
        var stroked = LeafPath.outline.strokedPath(style)
        stroked.addPath(LeafPath.rib.strokedPath(style))
        var path = Path()
        for leaf in leaves {
            let transform = CGAffineTransform(translationX: rect.minX + leaf.x, y: rect.minY + leaf.y)
                .rotated(by: leaf.rotation * .pi / 180)
                .scaledBy(x: leaf.scale, y: leaf.scale)
            path.addPath(stroked, transform: transform)
        }
        return path
    }
}

// MARK: - Celebration

/// Fête (motion 9): about 18 leaves fall once across the whole view while turning, then nothing is left
/// (1200 ms). Nothing at all with « Réduire les animations ». Same leaves as the mockups `Done` and
/// `Onboarding-Recap`.
struct LeafConfetti: View {
    @State private var start: Date?
    @State private var finished = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if !reduceMotion && !finished {
                TimelineView(.animation) { timeline in
                    Canvas { context, size in
                        let elapsed = timeline.date.timeIntervalSince(start ?? timeline.date)
                        for leaf in Self.leaves {
                            leaf.draw(in: &context, size: size, elapsed: elapsed)
                        }
                    }
                }
                .task {
                    start = .now
                    try? await Task.sleep(for: .seconds(Motion.Duration.celebration + 0.05))
                    finished = true
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private struct FallingLeaf {
        /// Width of the leaf (its height is 1.5 times more).
        let width: CGFloat
        /// Start, in a 390 wide screen.
        let x: CGFloat
        /// Drift along the fall.
        let dx: CGFloat
        /// Turn along the fall, in degrees.
        let turn: Double
        let delay: Double
        let color: Color

        /// The fall accelerates like the mockup's `cubic-bezier(.3, 0, .8, 1)`.
        private static let curve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.3, y: 0), endControlPoint: UnitPoint(x: 0.8, y: 1))

        func draw(in context: inout GraphicsContext, size: CGSize, elapsed: Double) {
            let duration = Motion.Duration.celebration - delay
            let progress = min(max((elapsed - delay) / duration, 0), 1)
            guard progress > 0, progress < 1 else { return }
            let eased = Self.curve.value(at: progress)
            // In over the first 8 %, out over the last 25 %.
            let opacity = min(progress / 0.08, 1) * min((1 - progress) / 0.25, 1)
            var leaf = context
            leaf.opacity = opacity
            let fall: CGFloat = size.height * 0.9 + 40
            leaf.translateBy(x: x * size.width / 390 + dx * CGFloat(eased), y: -40 + CGFloat(eased) * fall)
            leaf.rotate(by: .degrees(turn * eased))
            leaf.scaleBy(x: width / 32, y: width / 32)
            leaf.fill(LeafPath.outline, with: .color(color))
            leaf.stroke(LeafPath.rib, with: .color(Palette.surface.opacity(0.5)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
    }

    private static let leaves: [FallingLeaf] = {
        // Basilic, two lighter greens, Basilic clair and one Tomate leaf in six, as in the mockups.
        let palette: [Color] = [Palette.basil, Color(hex: 0x2F8A5B), Color(hex: 0x9BD8B3), Palette.basilLight, Palette.tomato, Palette.basil]
        return (0..<18).map { (i: Int) -> FallingLeaf in
            let width: Int = 12 + (i * 7) % 12
            let x: Int = (i * 53 + 17) % 370
            let dx: Int = (i * 37) % 80 - 40
            let turn: Int = 300 + (i * 61) % 360
            // The mockup's delays (0 to 0.9 s, looping), brought within the first 0.35 s of the single fall.
            let loopDelay: Double = (Double(i) * 0.07).truncatingRemainder(dividingBy: 0.9)
            return FallingLeaf(
                width: CGFloat(width),
                x: CGFloat(x),
                dx: CGFloat(dx),
                turn: Double(turn),
                delay: loopDelay * 0.35 / 0.9,
                color: palette[i % palette.count]
            )
        }
    }()
}
