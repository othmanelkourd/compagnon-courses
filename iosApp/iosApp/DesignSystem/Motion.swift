import SwiftUI

/// The motion tokens of the charter (mockup `Motion`), the same on Android (`ui/theme/Motion.kt`): four durations,
/// three curves and a soft spring. With « Réduire les animations » (`accessibilityReduceMotion`), slides, scales,
/// the card's flight and the celebration become plain fades, everywhere: the modifiers below read it themselves.
/// Nothing loops.
enum Motion {
    enum Duration {
        /// Rapide: press, check, chip.
        static let quick: Double = 0.15
        /// Standard: steps, rows, pickers, rolling numbers.
        static let standard: Double = 0.25
        /// Ample: entrances, the card pile, images, progress.
        static let ample: Double = 0.4
        /// Fête: the fall of leaves, once.
        static let celebration: Double = 1.2
    }

    /// Between two blocks of a cascade.
    static let stagger: Double = 0.04

    /// Standard curve `cubic-bezier(0.2, 0, 0, 1)`: steps, selections, progress.
    static func standard(_ duration: Double = Duration.standard) -> Animation {
        .timingCurve(0.2, 0, 0, 1, duration: duration)
    }

    /// Entrance curve `cubic-bezier(0, 0, 0, 1)`: what appears.
    static func entrance(_ duration: Double = Duration.ample) -> Animation {
        .timingCurve(0, 0, 0, 1, duration: duration)
    }

    /// Exit curve `cubic-bezier(0.3, 0, 1, 1)`: what leaves (the card, the leaves).
    static func exit(_ duration: Double = Duration.standard) -> Animation {
        .timingCurve(0.3, 0, 1, 1, duration: duration)
    }

    /// Ressort doux (damping 0.8): the pop of images and checks.
    static let softSpring = Animation.spring(response: Duration.ample, dampingFraction: 0.8)

    /// What a movement becomes with « Réduire les animations »: a plain fade.
    static let fade = Animation.easeInOut(duration: Duration.standard)
}

/// The direction of the last step change. SwiftUI keeps the removal transition a step was last rendered with, so
/// with a plain `Bool` the leaving step would slide the previous way; it reads this reference when it leaves.
final class StepDirection {
    var forward = true
}

/// One side of the step transition: arriving from 24 points on the side we move toward, or leaving 24 points to
/// the other side, while fading.
private struct StepSlide: ViewModifier {
    let direction: StepDirection
    let leaving: Bool
    let active: Bool

    func body(content: Content) -> some View {
        let toward: CGFloat = direction.forward ? 24 : -24
        content
            .offset(x: active ? (leaving ? -toward : toward) : 0)
            .opacity(active ? 0 : 1)
    }
}

extension AnyTransition {
    /// Passage d'étape (motion 1): the next step slides 24 points in from the trailing edge while fading in, the
    /// previous one slides 24 points out to the leading edge (the other way when going back). A plain fade with
    /// « Réduire les animations ». The owner of `direction` sets it before the step changes.
    static func step(_ direction: StepDirection, reduceMotion: Bool) -> AnyTransition {
        if reduceMotion {
            return AnyTransition.opacity.animation(Motion.fade)
        }
        func slide(leaving: Bool) -> AnyTransition {
            .modifier(
                active: StepSlide(direction: direction, leaving: leaving, active: true),
                identity: StepSlide(direction: direction, leaving: leaving, active: false)
            )
        }
        return AnyTransition.asymmetric(insertion: slide(leaving: false), removal: slide(leaving: true))
            .animation(Motion.standard())
    }
}

extension View {
    /// Entrée en cascade (motion 2): rises 12 points and fades in, once per appearance, `index` × 40 ms after the
    /// first block (plus `delay`). A fade only with « Réduire les animations ».
    func cascade(_ index: Int, delay: Double = 0) -> some View {
        modifier(CascadeModifier(delay: delay + Double(index) * Motion.stagger))
    }

    /// Chiffres qui roulent (motion 5): the digits of the text roll up when `value` grows, down when it shrinks.
    func rollingNumber(_ value: Int) -> some View {
        modifier(RollingNumberModifier(value: value))
    }

    /// Appui (motion 8): the large cards and buttons shrink to 0.98 while pressed.
    func pressScale(_ pressed: Bool) -> some View {
        modifier(PressScaleModifier(pressed: pressed))
    }

    /// Images 3D (motion 10): enters with a slight bounce, 0.9 → 1 on the soft spring, `delay` after it appears (a
    /// fade with « Réduire les animations »). Off when `enabled` is false.
    func popIn(delay: Double = 0, from scale: CGFloat = 0.9, enabled: Bool = true) -> some View {
        modifier(PopInModifier(delay: delay, scale: scale, enabled: enabled))
    }

    /// Puce qui « pop » (motion 4): dips to 0.96 then springs back each time `trigger` changes.
    func chipPop<Trigger: Equatable>(trigger: Trigger) -> some View {
        modifier(ChipPopModifier(trigger: trigger))
    }
}

private struct CascadeModifier: ViewModifier {
    let delay: Double

    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(shown ? 1 : 0)
            .offset(y: shown || reduceMotion ? 0 : 12)
            .onAppear {
                guard !shown else { return }
                withAnimation((reduceMotion ? Motion.fade : Motion.entrance()).delay(delay)) {
                    shown = true
                }
            }
    }
}

private struct PopInModifier: ViewModifier {
    let delay: Double
    let scale: CGFloat
    let enabled: Bool

    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        let visible = shown || !enabled
        content
            .scaleEffect(visible || reduceMotion ? 1 : scale)
            .opacity(visible ? 1 : 0)
            .onAppear {
                guard enabled, !shown else { return }
                withAnimation((reduceMotion ? Motion.fade : Motion.softSpring).delay(delay)) {
                    shown = true
                }
            }
    }
}

private struct RollingNumberModifier: ViewModifier {
    let value: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .contentTransition(reduceMotion ? .opacity : .numericText(value: Double(value)))
            .animation(reduceMotion ? Motion.fade : Motion.standard(), value: value)
    }
}

private struct PressScaleModifier: ViewModifier {
    let pressed: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .scaleEffect(pressed && !reduceMotion ? 0.98 : 1)
            .animation(Motion.standard(Motion.Duration.quick), value: pressed)
    }
}

private struct ChipPopModifier<Trigger: Equatable>: ViewModifier {
    let trigger: Trigger

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        if reduceMotion {
            content
        } else {
            content.keyframeAnimator(initialValue: CGFloat(1), trigger: trigger) { view, scale in
                view.scaleEffect(scale)
            } keyframes: { _ in
                CubicKeyframe(0.96, duration: Motion.Duration.quick / 2)
                SpringKeyframe(1, duration: Motion.Duration.standard, spring: Spring(response: 0.25, dampingRatio: 0.6))
            }
        }
    }
}

/// Appui on the large cards of the home screen (Basilic card, « Ma liste », « Mon foyer »).
struct PressScaleStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .pressScale(configuration.isPressed)
    }
}
