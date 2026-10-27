import SwiftUI

/// Text with an optional icon before or after it, for the buttons of the charter.
struct ButtonLabel: View {
    let title: Text
    var icon: Icon?
    var iconSize: CGFloat = 20
    var trailingIcon = false

    init(_ title: Text, icon: Icon? = nil, iconSize: CGFloat = 20, trailingIcon: Bool = false) {
        self.title = title
        self.icon = icon
        self.iconSize = iconSize
        self.trailingIcon = trailingIcon
    }

    init(_ key: LocalizedStringKey, icon: Icon? = nil, iconSize: CGFloat = 20, trailingIcon: Bool = false) {
        self.init(Text(key), icon: icon, iconSize: iconSize, trailingIcon: trailingIcon)
    }

    var body: some View {
        HStack(spacing: 8) {
            if let icon, !trailingIcon {
                IconView(icon, size: iconSize, weight: .bold)
            }
            title
            if let icon, trailingIcon {
                IconView(icon, size: iconSize, weight: .bold)
            }
        }
    }
}

/// Bouton principal: Basilic, white 18 / 700, radius 18, 56 to 60 high. Pressed: Basilic foncé, at 0.98.
struct PrimaryButtonStyle: ButtonStyle {
    var height: CGFloat = 56
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .textStyle(.primaryButton)
            .foregroundStyle(Palette.surface)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(configuration.isPressed ? Palette.basilDark : Palette.basil, in: .rect(cornerRadius: Radius.button))
            .opacity(isEnabled ? 1 : 0.4)
            .contentShape(.rect(cornerRadius: Radius.button))
            .pressScale(configuration.isPressed)
    }
}

/// Bouton secondaire: Surface, 1.5 Trait fort border, Encre 18 / 700, at 0.98 when pressed. `basil`: border and text in
/// Basilic (« Courses terminées »).
struct SecondaryButtonStyle: ButtonStyle {
    var height: CGFloat = 56
    var basil = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .textStyle(.primaryButton)
            .foregroundStyle(basil ? Palette.basil : Palette.ink)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .frame(maxWidth: .infinity, minHeight: height)
            .background(configuration.isPressed ? Palette.surfaceSoft : Palette.surface, in: .rect(cornerRadius: Radius.button))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.button)
                    .strokeBorder(basil ? Palette.basil : Palette.lineStrong, lineWidth: 1.5)
            }
            .opacity(isEnabled ? 1 : 0.4)
            .contentShape(.rect(cornerRadius: Radius.button))
            .pressScale(configuration.isPressed)
    }
}

/// Bouton texte: no background, Basilic 16 / 600, 44 high. `ink`: « Annuler ». Disabled: Encre discrète.
struct TextButtonStyle: ButtonStyle {
    var ink = false
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .textStyle(.textButton)
            .foregroundStyle(isEnabled ? (ink ? Palette.ink : Palette.basil) : Palette.inkMuted)
            .padding(.horizontal, 12)
            .frame(minHeight: Target.minimum)
            .opacity(configuration.isPressed ? 0.6 : 1)
            .contentShape(.rect)
    }
}

/// Bouton icône: a 44 circle. `outline`: Surface with a Trait border; `soft`: Surface douce without border.
struct IconButtonStyle: ButtonStyle {
    enum Variant { case outline, soft }

    var variant: Variant = .outline
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(Palette.ink)
            .frame(width: Target.minimum, height: Target.minimum)
            .background(background(pressed: configuration.isPressed), in: .circle)
            .overlay {
                if variant == .outline {
                    Circle().strokeBorder(Palette.line, lineWidth: 1)
                }
            }
            .opacity(isEnabled ? 1 : 0.4)
            .contentShape(.circle)
    }

    private func background(pressed: Bool) -> Color {
        switch variant {
        case .outline: pressed ? Palette.surfaceSoft : Palette.surface
        case .soft: pressed ? Palette.line : Palette.surfaceSoft
        }
    }
}

/// A round icon button (44) with its VoiceOver label.
struct IconButton: View {
    let icon: Icon
    let label: Text
    var variant: IconButtonStyle.Variant = .outline
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            IconView(icon, size: 20)
        }
        .buttonStyle(IconButtonStyle(variant: variant))
        .accessibilityLabel(label)
    }
}

extension ButtonStyle where Self == PrimaryButtonStyle {
    static var primary: PrimaryButtonStyle { PrimaryButtonStyle() }
    static func primary(height: CGFloat) -> PrimaryButtonStyle { PrimaryButtonStyle(height: height) }
}

extension ButtonStyle where Self == SecondaryButtonStyle {
    static var secondary: SecondaryButtonStyle { SecondaryButtonStyle() }
    static func secondary(height: CGFloat = 56, basil: Bool = false) -> SecondaryButtonStyle {
        SecondaryButtonStyle(height: height, basil: basil)
    }
}

extension ButtonStyle where Self == TextButtonStyle {
    static var text: TextButtonStyle { TextButtonStyle() }
    static var inkText: TextButtonStyle { TextButtonStyle(ink: true) }
}

/// « Annuler » (back to the previous question): Encre, with the undo arrow, always at the bottom left.
struct UndoButton: View {
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ButtonLabel("Annuler", icon: .undo, iconSize: 18)
        }
        .buttonStyle(.inkText)
        .disabled(!enabled)
        .accessibilityLabel("Annuler la dernière réponse")
    }
}
