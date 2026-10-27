import Shared
import SwiftUI

/// « Des courses à votre image » (mockup `Onboarding-Welcome`): the four sections, « Commencer », « Plus tard ».
struct WelcomeStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    var body: some View {
        OnboardingStepLayout {
            VStack(alignment: .leading, spacing: 0) {
                OcimioLogo(size: 88)
                OnboardingHeader(
                    title: Text("Des courses à votre image"),
                    subtitle: Text("Quatre questions sur votre foyer, et Ocimio vous proposera les bons produits, au bon moment."),
                    titleStyle: .screenTitleLarge,
                    subtitleStyle: .body
                )
                .padding(.top, 28)

                OnboardingBlock {
                    ForEach(Array(HouseholdSection.ordered.enumerated()), id: \.offset) { index, section in
                        if index > 0 {
                            RowSeparator()
                        }
                        HStack(spacing: 14) {
                            RowBadge {
                                Text(verbatim: "\(index + 1)")
                                    .font(.custom(FontName.figtreeBold, size: 16, relativeTo: .callout))
                            }
                            TitleHint(
                                title: Text(verbatim: HouseholdTexts.sectionTitle(section)),
                                hint: Text(verbatim: HouseholdTexts.sectionHint(section))
                            )
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                        .accessibilityElement(children: .combine)
                    }
                }
                .padding(.top, 28)
            }
            .padding(.top, 24)
        } bottom: {
            PrivacyNote(text: Text("Vos réponses restent sur votre téléphone."))
                .padding(.bottom, 14)
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventStart.shared) }) {
                Text("Commencer · ≈ 2 min")
            }
            Button("Plus tard") {
                send(OnboardingEventLater.shared)
            }
            .buttonStyle(.text)
            .disabled(state.saving)
            .padding(.top, 12)
        }
    }
}

/// « Votre foyer est prêt » (mockup `Onboarding-Recap`): the four sections with « Modifier », then a session or home.
struct RecapStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    var body: some View {
        OnboardingStepLayout {
            VStack(alignment: .leading, spacing: 0) {
                IconView(.check, size: 32, weight: .bold)
                    .foregroundStyle(Palette.basil)
                    .frame(width: 64, height: 64)
                    .background(Palette.basilLight, in: .circle)

                OnboardingHeader(
                    title: Text("Votre foyer est prêt"),
                    subtitle: Text("Les questions s'adaptent maintenant à vous. Tout reste modifiable dans «\u{00A0}Mon foyer\u{00A0}»."),
                    titleStyle: .screenTitleLarge,
                    subtitleStyle: .body
                )
                .padding(.top, 22)

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
    }
}

/// « Mon foyer » (mockup `Household-Edit`): one row per section with its summary, each opening its section;
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
                                    IconView(icon(section), size: 20, weight: .medium)
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

    private func icon(_ section: HouseholdSection) -> Icon {
        switch section {
        case .household: .house
        case .food: .food
        case .essentials: .check
        case .rhythm: .calendarWeek
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
