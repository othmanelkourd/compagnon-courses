import Shared
import SwiftUI

/// « Des courses à votre image » (mockup `Onboarding-Welcome`): the logo under a cloud of 3D products, the four
/// sections with their image and number, « Commencer », « Plus tard ».
struct WelcomeStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        OnboardingStepLayout {
            VStack(alignment: .leading, spacing: 0) {
                OcimioLogo(size: 88)
                    .popIn()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(alignment: .topTrailing) {
                        decor
                    }
                OnboardingHeader(
                    title: Text("Des courses à votre image"),
                    subtitle: Text("Quatre questions sur votre foyer, et Ocimio vous proposera les bons produits, au bon moment."),
                    titleStyle: .screenTitleLarge,
                    subtitleStyle: .body
                )
                .padding(.top, 28)
                .cascade(2)

                OnboardingBlock {
                    ForEach(Array(HouseholdSection.ordered.enumerated()), id: \.offset) { index, section in
                        if index > 0 {
                            RowSeparator()
                        }
                        HStack(spacing: 14) {
                            RowBadge(size: 44, radius: 14) {
                                Illustration(section.imageKey, size: 30, pops: false)
                            }
                            .overlay(alignment: .topLeading) {
                                // The step number on the corner of its image.
                                Text(verbatim: "\(index + 1)")
                                    .font(.custom(FontName.figtreeBold, size: 12, relativeTo: .caption))
                                    .foregroundStyle(Palette.surface)
                                    .frame(width: 20, height: 20)
                                    .background(Palette.basil, in: .circle)
                                    .overlay { Circle().strokeBorder(Palette.surface, lineWidth: 2) }
                                    .offset(x: -5, y: -5)
                            }
                            TitleHint(
                                title: Text(verbatim: HouseholdTexts.sectionTitle(section)),
                                hint: Text(verbatim: HouseholdTexts.sectionHint(section))
                            )
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .accessibilityElement(children: .combine)
                    }
                }
                .padding(.top, 28)
                .cascade(3)
            }
            .padding(.top, 24)
        } bottom: {
            PrivacyNote(text: Text("Vos réponses restent sur votre téléphone."))
                .padding(.bottom, 14)
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventStart.shared) }) {
                Text("Commencer · ≈ 2 min")
            }
            .cascade(4)
            Button("Plus tard") {
                send(OnboardingEventLater.shared)
            }
            .buttonStyle(.text)
            .disabled(state.saving)
            .padding(.top, 12)
        }
    }
}

extension WelcomeStepView {
    /// The products floating around the logo and the leaf pattern (mockup `Onboarding-Welcome`), placed from the top
    /// right corner of the logo's row like the mockup places them from the screen's (its logo is 68 below the top).
    /// They pop in one after the other; the pattern alone at accessibility text sizes.
    private var decor: some View {
        ZStack(alignment: .topTrailing) {
            LeafPattern.welcome
                .offset(x: Spacing.screen, y: 40 - 68)
            if !dynamicTypeSize.isAccessibilitySize {
                // 18 lower than in the mockup: clear of the Dynamic Island, which the mockup does not have.
                product(ScreenIllustration.decorLeafy, size: 40, top: 32, right: 190, rotation: -20, delay: 0.32)
                product(ScreenIllustration.decorApple, size: 58, top: 54, right: 128, rotation: -8, delay: 0.08)
                product(ScreenIllustration.decorBaguette, size: 86, top: 38, right: 30, rotation: 18, delay: 0.12)
                product(ScreenIllustration.decorCheese, size: 46, top: 122, right: 158, rotation: -12, delay: 0.24)
                product(ScreenIllustration.decorMilk, size: 62, top: 120, right: 86, rotation: 0, delay: 0.16)
                product(ScreenIllustration.decorCarrot, size: 56, top: 112, right: 16, rotation: 22, delay: 0.24)
            }
        }
        .accessibilityHidden(true)
    }

    private func product(_ key: String, size: CGFloat, top: CGFloat, right: CGFloat, rotation: Double, delay: Double) -> some View {
        Illustration(key, size: size, delay: delay)
            .rotationEffect(.degrees(rotation))
            .offset(x: Spacing.screen - right, y: top - 68)
    }
}

/// « Votre foyer est prêt » (mockup `Onboarding-Recap`): the house with a check that pops and a fall of leaves (with a
/// success haptic), the four sections with « Modifier », then a session or home.
struct RecapStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    @State private var celebrates = false

    var body: some View {
        OnboardingStepLayout {
            VStack(alignment: .leading, spacing: 0) {
                IllustrationPlate(key: ScreenIllustration.onboardingRecap, size: 104, imageSize: 72)
                    .background {
                        Circle()
                            .fill(Palette.basilLight.opacity(0.45))
                            .padding(-8)
                    }
                    .overlay(alignment: .bottomTrailing) {
                        CheckBadge(size: 36, border: 3, borderColor: Palette.canvas, checkSize: 18, checkWidth: 2.4, pops: true, delay: 0.24)
                            .offset(x: 4, y: 2)
                    }
                    .popIn()
                    .accessibilityHidden(true)

                OnboardingHeader(
                    title: Text("Votre foyer est prêt"),
                    subtitle: Text("Les questions s'adaptent maintenant à vous. Tout reste modifiable dans «\u{00A0}Mon foyer\u{00A0}»."),
                    titleStyle: .screenTitleLarge,
                    subtitleStyle: .body
                )
                .padding(.top, 22)
                .cascade(2)

                OnboardingBlock {
                    ForEach(Array(HouseholdSection.ordered.enumerated()), id: \.offset) { index, section in
                        if index > 0 {
                            RowSeparator()
                        }
                        Button {
                            send(OnboardingEventEdit(section: section))
                        } label: {
                            HStack(spacing: 14) {
                                VStack(alignment: .leading, spacing: 2) {
                                    GroupTitle(text: Text(verbatim: HouseholdTexts.sectionRow(section)))
                                    Text(verbatim: HouseholdTexts.section(section, of: state.summary))
                                        .font(.custom(FontName.figtreeSemiBold, size: 16, relativeTo: .callout))
                                        .foregroundStyle(Palette.ink)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                Text("Modifier")
                                    .font(.custom(FontName.figtreeSemiBold, size: 15, relativeTo: .subheadline))
                                    .foregroundStyle(Palette.basil)
                            }
                            .padding(.leading, 16)
                            .padding(.trailing, 14)
                            .padding(.vertical, 14)
                            .frame(minHeight: Target.minimum)
                            .contentShape(.rect)
                        }
                        .buttonStyle(RowPressStyle())
                        .disabled(state.saving)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text(verbatim: HouseholdTexts.sectionRow(section)))
                        .accessibilityValue(Text(verbatim: HouseholdTexts.section(section, of: state.summary)))
                        .accessibilityHint(Text("Modifier"))
                    }
                }
                .padding(.top, 24)
                .cascade(3)
            }
            .padding(.top, 24)
        } bottom: {
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventFinish(startSession: true)) }) {
                ButtonLabel("Faire le point maintenant", icon: .arrowRight, trailingIcon: true)
            }
            Button("Aller à l'accueil") {
                send(OnboardingEventFinish(startSession: false))
            }
            .buttonStyle(.text)
            .disabled(state.saving)
            .padding(.top, 12)
        }
        .overlay {
            if celebrates {
                LeafConfetti()
                    .ignoresSafeArea()
            }
        }
        .sensoryFeedback(.success, trigger: celebrates) { _, started in started }
        .onAppear {
            celebrates = true
        }
    }
}

/// « Mon foyer » (mockup `Household-Edit`): one row per section with its image and summary, each opening its section;
/// « Tout reprendre depuis le début ».
struct HouseholdOverviewView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    var body: some View {
        OnboardingStepLayout {
            VStack(alignment: .leading, spacing: 0) {
                OnboardingHeader(
                    title: Text("Mon foyer"),
                    subtitle: Text("Ce que vous changez ici s'applique dès le prochain «\u{00A0}faire le point\u{00A0}»."),
                    titleStyle: .screenTitleLarge,
                    subtitleStyle: .bodyCompact,
                    spacing: 8
                )
                .padding(.top, 16)

                OnboardingBlock {
                    ForEach(Array(HouseholdSection.ordered.enumerated()), id: \.offset) { index, section in
                        if index > 0 {
                            RowSeparator()
                        }
                        Button {
                            send(OnboardingEventEdit(section: section))
                        } label: {
                            HStack(spacing: 14) {
                                RowBadge {
                                    Illustration(section.imageKey, size: 28, pops: false)
                                }
                                TitleHint(
                                    title: Text(verbatim: HouseholdTexts.sectionRow(section)),
                                    hint: Text(verbatim: HouseholdTexts.section(section, of: state.summary)),
                                    hintStyle: .help
                                )
                                .frame(maxWidth: .infinity, alignment: .leading)
                                IconView(.chevronRight, size: 20)
                                    .foregroundStyle(Palette.inkMuted)
                            }
                            .padding(.leading, 16)
                            .padding(.trailing, 14)
                            .padding(.vertical, 14)
                            .contentShape(.rect)
                        }
                        .buttonStyle(RowPressStyle())
                        .accessibilityElement(children: .combine)
                    }
                }
                .padding(.top, 22)
                .cascade(1)

                PrivacyNote(text: Text("Ces informations restent sur votre téléphone."), centered: false)
                    .padding(.horizontal, 6)
                    .padding(.top, 18)
            }
        } bottom: {
            Button("Tout reprendre depuis le début") {
                send(OnboardingEventStart.shared)
            }
            .buttonStyle(.secondary)
            .disabled(state.saving)
        }
    }

}

/// A row of a block that darkens slightly while pressed.
struct RowPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Palette.surfaceSoft : Color.clear)
    }
}
