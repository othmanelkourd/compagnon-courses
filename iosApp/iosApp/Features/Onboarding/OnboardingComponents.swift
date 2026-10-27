import Shared
import SwiftUI

/// The top bar of the onboarding steps (mockups `Onboarding-*`): [←] round button (or [✕] for a section opened from
/// « Mon foyer »), « Étape n sur 4 » over 4 segments, « Passer » on the right when the step can be skipped.
struct OnboardingTopBar: View {
    let stepNumber: Int?
    let stepCount: Int
    let canSkip: Bool
    let closeInsteadOfBack: Bool
    var backLabel = Text("Retour")
    var enabled = true
    let onBack: () -> Void
    let onSkip: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            IconButton(icon: closeInsteadOfBack ? .close : .chevronLeft, label: closeInsteadOfBack ? Text("Fermer") : backLabel, action: onBack)
            if let stepNumber {
                VStack(spacing: 6) {
                    Text("Étape \(stepNumber) sur \(stepCount)")
                        .textStyle(.counter)
                        .foregroundStyle(Palette.ink)
                        .monospacedDigit()
                    ProgressSegments(total: stepCount, filled: stepNumber, spacing: 4)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("Étape \(stepNumber) sur \(stepCount)"))
            } else {
                Spacer(minLength: 0)
            }
            Group {
                if canSkip {
                    Button("Passer", action: onSkip)
                        .buttonStyle(.text)
                        .padding(.trailing, -12)
                        .disabled(!enabled)
                } else {
                    Color.clear.frame(width: Target.minimum, height: Target.minimum)
                }
            }
            .frame(minWidth: 64, alignment: .trailing)
            .accessibilityHidden(!canSkip)
        }
    }
}

/// Overline, title and subtitle of a step (« VOTRE FOYER », « Qui vit à la maison ? », …).
struct OnboardingHeader: View {
    var overline: Text?
    let title: Text
    let subtitle: Text
    var titleStyle: TextStyle = .screenTitle
    var subtitleStyle: TextStyle = .bodyCompact
    var spacing: CGFloat = 10

    var body: some View {
        VStack(alignment: .leading, spacing: spacing) {
            if let overline {
                Overline(text: overline, color: Palette.basil)
            }
            title
                .textStyle(titleStyle)
                .foregroundStyle(Palette.ink)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            subtitle
                .textStyle(subtitleStyle)
                .foregroundStyle(Palette.inkSoft)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// « Vos réponses restent sur votre téléphone. » with a padlock (Aide, Encre douce).
struct PrivacyNote: View {
    let text: Text
    var centered = true

    var body: some View {
        HStack(spacing: 6) {
            IconView(.lock, size: 16)
            text
                .textStyle(.help)
                .fixedSize(horizontal: false, vertical: true)
        }
        .foregroundStyle(Palette.inkSoft)
        .multilineTextAlignment(centered ? .center : .leading)
        .frame(maxWidth: .infinity, alignment: centered ? .center : .leading)
        .accessibilityElement(children: .combine)
    }
}

/// The section title of a group inside a step (« DES ANIMAUX ? », « FRUITS ET LÉGUMES »): 13 / 700 capitals,
/// tracking 0.6, Encre douce.
struct GroupTitle: View {
    let text: Text

    var body: some View {
        text
            .font(TextStyle.overline.font)
            .tracking(0.6)
            .textCase(.uppercase)
            .foregroundStyle(Palette.inkSoft)
            .accessibilityAddTraits(.isHeader)
    }
}

/// The Tomate banner of a storage failure, with « Réessayer ».
struct OnboardingErrorBanner: View {
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Text("Votre foyer n'a pas pu être enregistré sur l'appareil")
                .textStyle(.secondaryStrong)
                .foregroundStyle(Palette.tomatoText)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button("Réessayer", action: onRetry)
                .buttonStyle(TomatoTextButtonStyle())
        }
        .padding(.leading, 14)
        .padding(.trailing, 2)
        .padding(.vertical, 4)
        .background(Palette.tomatoLight, in: .rect(cornerRadius: Radius.field))
        .accessibilityElement(children: .contain)
    }
}

private struct TomatoTextButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.custom(FontName.figtreeBold, size: 15, relativeTo: .subheadline))
            .foregroundStyle(Palette.tomatoText)
            .padding(.horizontal, 12)
            .frame(minHeight: Target.minimum)
            .opacity(configuration.isPressed ? 0.6 : 1)
            .contentShape(.rect)
    }
}

/// The primary button of a step: its title, or « Enregistrement… » while the household is being saved.
struct OnboardingPrimaryButton<Label: View>: View {
    let saving: Bool
    let action: () -> Void
    @ViewBuilder let label: Label

    var body: some View {
        Button(action: action) {
            if saving {
                Text("Enregistrement…")
            } else {
                label
            }
        }
        .buttonStyle(.primary)
        .disabled(saving)
    }
}

/// A scrolling step with its buttons fixed at the bottom: on Fond, or over a fade when the list scrolls under them
/// (essentials, recurring products).
struct OnboardingStepLayout<Content: View, Bottom: View>: View {
    var fade = false
    var horizontalPadding: CGFloat = Spacing.screen
    @ViewBuilder let content: Content
    @ViewBuilder let bottom: Bottom

    var body: some View {
        ScrollView {
            content
                .padding(.horizontal, horizontalPadding)
                .padding(.bottom, 16)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 0) {
                bottom
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.top, fade ? 28 : 12)
            .padding(.bottom, 4)
            .background {
                if fade {
                    BottomFade()
                } else {
                    Palette.canvas.ignoresSafeArea()
                }
            }
        }
    }
}

/// A white block of the onboarding (Surface, Trait border, radius 24) holding rows separated by Trait léger lines.
struct OnboardingBlock<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .background(Palette.surface, in: .rect(cornerRadius: 24))
        .clipShape(.rect(cornerRadius: 24))
        .overlay { RoundedRectangle(cornerRadius: 24).strokeBorder(Palette.line, lineWidth: 1) }
    }
}

/// The Trait léger separator between two rows of a block.
struct RowSeparator: View {
    var body: some View {
        Rectangle()
            .fill(Palette.lineLight)
            .frame(height: 1)
    }
}

/// A row title (17 / 600) over its hint (13, Encre douce).
struct TitleHint: View {
    let title: Text
    let hint: Text
    var hintStyle: TextStyle = .caption

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            title
                .font(.custom(FontName.figtreeSemiBold, size: 17, relativeTo: .body))
                .foregroundStyle(Palette.ink)
            hint
                .textStyle(hintStyle)
                .foregroundStyle(Palette.inkSoft)
        }
        .multilineTextAlignment(.leading)
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// The 40 square of a row (Basilic clair, radius 12): a number on the welcome screen, an icon in « Mon foyer ».
struct RowBadge<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        content
            .foregroundStyle(Palette.basil)
            .frame(width: 40, height: 40)
            .background(Palette.basilLight, in: .rect(cornerRadius: Radius.optionTile))
    }
}

/// Wraps its subviews on as many lines as needed (the chips of the essentials).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? .infinity
        let rows = arrange(width: width, subviews: subviews)
        let height = rows.last.map { $0.y + $0.height } ?? 0
        let usedWidth = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? usedWidth, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rows = arrange(width: bounds.width, subviews: subviews)
        for row in rows {
            var x = bounds.minX
            for index in row.indices {
                let size = sizes(subviews[index], width: bounds.width)
                subviews[index].place(at: CGPoint(x: x, y: bounds.minY + row.y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
        }
    }

    private struct Row {
        var indices: [Int] = []
        var y: CGFloat = 0
        var width: CGFloat = 0
        var height: CGFloat = 0
    }

    private func sizes(_ subview: LayoutSubview, width: CGFloat) -> CGSize {
        let ideal = subview.sizeThatFits(.unspecified)
        guard ideal.width > width else { return ideal }
        return subview.sizeThatFits(ProposedViewSize(width: width, height: nil))
    }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var current = Row()
        for index in subviews.indices {
            let size = sizes(subviews[index], width: width)
            let needed = current.indices.isEmpty ? size.width : current.width + spacing + size.width
            if needed > width, !current.indices.isEmpty {
                rows.append(current)
                current = Row(y: current.y + current.height + lineSpacing)
                current.indices = [index]
                current.width = size.width
                current.height = size.height
            } else {
                current.indices.append(index)
                current.width = needed
                current.height = max(current.height, size.height)
            }
        }
        if !current.indices.isEmpty {
            rows.append(current)
        }
        return rows
    }
}

/// A product chip of the essentials (44 high, capsule, 15 / 600). Checked: Basilic clair, 2 pt Basilic border,
/// Basilic foncé text with a check.
struct ProductChip: View {
    let name: String
    let checked: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if checked {
                    IconView(.check, size: 15, weight: .heavy)
                }
                Text(verbatim: name)
                    .font(.custom(FontName.figtreeSemiBold, size: 15, relativeTo: .subheadline))
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            .foregroundStyle(checked ? Palette.basilDark : Palette.ink)
            .padding(.horizontal, 14)
            .frame(minHeight: 44)
            .background(checked ? Palette.basilLight : Palette.surface, in: .capsule)
            .overlay { Capsule().strokeBorder(checked ? Palette.basil : Palette.line, lineWidth: checked ? 2 : 1) }
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: name))
        .accessibilityAddTraits(checked ? [.isButton, .isSelected] : .isButton)
    }
}

extension View {
    /// The edge swipe of the onboarding: dragging from the leading edge sends `Back` (the previous step, or leaving
    /// a section), as the system swipe would leave a pushed screen.
    func onboardingEdgeSwipe(enabled: Bool = true, action: @escaping () -> Void) -> some View {
        overlay(alignment: .leading) {
            if enabled {
                Color.clear
                    .frame(width: 16)
                    .contentShape(.rect)
                    .gesture(
                        DragGesture(minimumDistance: 12, coordinateSpace: .global)
                            .onEnded { value in
                                if value.translation.width > 60, abs(value.translation.height) < value.translation.width {
                                    action()
                                }
                            }
                    )
                    .ignoresSafeArea()
                    .accessibilityHidden(true)
            }
        }
    }
}
