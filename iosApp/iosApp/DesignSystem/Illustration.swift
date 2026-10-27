import SwiftUI
import UIKit

/// A Fluent Emoji 3D illustration (Microsoft, MIT, `Resources/Licenses`) from the asset catalog
/// (`Illustrations/<key>`, 256 px). Decorative: hidden from VoiceOver, the text next to it says what it is.
///
/// It enters with a slight bounce, 0.9 → 1 on the soft spring (motion 10), `delay` after it appears; a fade with
/// « Réduire les animations ». `pops: false` for the thumbnails of rows, which come with their row.
struct Illustration: View {
    let key: String
    let size: CGFloat
    var pops = true
    var delay: Double = 0

    init(_ key: String, size: CGFloat, pops: Bool = true, delay: Double = 0) {
        self.key = key
        self.size = size
        self.pops = pops
        self.delay = delay
    }

    var body: some View {
        Image(Self.assetName(key))
            .resizable()
            .interpolation(.high)
            .scaledToFit()
            .frame(width: size, height: size)
            .popIn(delay: delay, enabled: pops)
            .accessibilityHidden(true)
    }

    /// The asset of `key`, or the fallback bags if the catalog has none (every shared key has one).
    private static func assetName(_ key: String) -> String {
        let name = "Illustrations/\(key)"
        return UIImage(named: name) == nil ? "Illustrations/\(ScreenIllustration.fallback)" : name
    }
}

/// Pastille: an illustration on a Basilic clair disc (Surface douce and the image at 45 % when `dimmed`: an unchecked
/// suggestion, a checked list item). `shadow` adds the soft shadow under the object of the large discs.
struct IllustrationPlate: View {
    let key: String
    /// Diameter of the disc.
    let size: CGFloat
    /// Size of the image inside it.
    let imageSize: CGFloat
    var dimmed = false
    var shadow = false
    var pops = false
    var delay: Double = 0

    var body: some View {
        ZStack {
            Circle()
                .fill(dimmed ? Palette.surfaceSoft : Palette.basilLight)
            if shadow {
                ObjectShadow(width: imageSize * 0.72)
                    .offset(y: imageSize * 0.42)
            }
            Illustration(key, size: imageSize, pops: pops, delay: delay)
                .opacity(dimmed ? 0.45 : 1)
        }
        .frame(width: size, height: size)
        .animation(Motion.standard(Motion.Duration.quick), value: dimmed)
        .accessibilityHidden(true)
    }
}

/// The blurred shadow under a large 3D object (mockups `Card`, `List-Empty`, `Done`): an ellipse of Encre at 10 %.
struct ObjectShadow: View {
    let width: CGFloat
    var height: CGFloat = 10

    var body: some View {
        Ellipse()
            .fill(Palette.ink.opacity(0.10))
            .frame(width: width, height: height)
            .blur(radius: 3)
            .accessibilityHidden(true)
    }
}

/// The round Basilic badge with a check that draws itself, on the corner of an illustration: a selected pet, a
/// suggestion to buy (with `checked: false`, an empty white ring), the end of a session or of the onboarding.
struct CheckBadge: View {
    var checked = true
    /// Outer diameter, border included.
    let size: CGFloat
    /// The ring around it, in the color of what it sits on.
    var border: CGFloat = 2
    var borderColor: Color = Palette.surface
    /// Size of the check and its stroke, in points.
    let checkSize: CGFloat
    let checkWidth: CGFloat
    /// Pops in (and draws its check) after `delay` (the hero badges); otherwise it only draws its check.
    var pops = false
    var delay: Double = 0

    var body: some View {
        ZStack {
            if checked {
                Circle()
                    .fill(Palette.basil)
                    .overlay { Circle().strokeBorder(borderColor, lineWidth: border) }
                DrawnCheck(size: checkSize, lineWidth: checkWidth, delay: pops ? delay + 0.06 : 0)
            } else {
                Circle()
                    .fill(Palette.surface)
                    .overlay { Circle().strokeBorder(Palette.checkboxBorder, lineWidth: border) }
            }
        }
        .frame(width: size, height: size)
        .animation(Motion.standard(Motion.Duration.quick), value: checked)
        .popIn(delay: delay, from: 0.6, enabled: pops)
        .accessibilityHidden(true)
    }
}

/// The check of the charter (`M20 6 9 17l-5-5` in a 24 grid), drawn from its long arm like the mockup's path.
struct CheckShape: Shape {
    func path(in rect: CGRect) -> Path {
        let scale = min(rect.width, rect.height) / 24
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * scale, y: rect.minY + y * scale)
        }
        var path = Path()
        path.move(to: point(20, 6))
        path.addLine(to: point(9, 17))
        path.addLine(to: point(4, 12))
        return path
    }
}

/// Coche qui se dessine (motion 4): the check strokes itself from 0 to 1 when it appears (250 ms, after `delay`); it
/// fades in with « Réduire les animations ».
struct DrawnCheck: View {
    let size: CGFloat
    let lineWidth: CGFloat
    var color: Color = Palette.surface
    var delay: Double = 0

    @State private var progress: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        CheckShape()
            .trim(from: 0, to: reduceMotion ? 1 : progress)
            .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round))
            .frame(width: size, height: size)
            .opacity(reduceMotion ? progress : 1)
            .onAppear {
                guard progress == 0 else { return }
                withAnimation((reduceMotion ? Motion.fade : Motion.standard()).delay(delay)) {
                    progress = 1
                }
            }
            .accessibilityHidden(true)
    }
}
