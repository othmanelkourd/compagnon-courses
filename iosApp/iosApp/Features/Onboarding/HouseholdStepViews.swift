import Shared
import SwiftUI

/// Step 1 « Qui vit à la maison ? » (mockup `Onboarding-Household`): a stepper per kind of member, pets as toggles
/// with their face.
struct HouseholdStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    private static let members: [MemberKind] = [.adults, .babies, .children, .teens]
    private static let pets: [PetChoice] = [.dog, .cat, .other]

    var body: some View {
        let household = state.household
        OnboardingStepLayout {
            VStack(alignment: .leading, spacing: 0) {
                OnboardingHeader(
                    overline: Text(verbatim: HouseholdTexts.sectionTitle(.household)),
                    imageKey: ScreenIllustration.onboardingHousehold,
                    title: Text("Qui vit à la maison\u{00A0}?"),
                    subtitle: Text("Pour proposer les bonnes quantités, et les produits bébé ou animaux seulement s'il y en a.")
                )
                .padding(.top, 32)

                OnboardingBlock {
                    ForEach(Array(Self.members.enumerated()), id: \.offset) { index, kind in
                        if index > 0 {
                            RowSeparator()
                        }
                        HStack(spacing: 12) {
                            TitleHint(
                                title: Text(verbatim: HouseholdTexts.memberLabel(kind)),
                                hint: Text(verbatim: HouseholdTexts.memberHint(kind))
                            )
                            .frame(maxWidth: .infinity, alignment: .leading)
                            MemberStepper(
                                label: HouseholdTexts.memberLabel(kind),
                                count: Int(household.count(kind: kind)),
                                canDecrement: household.canDecrement(kind: kind),
                                canIncrement: household.canIncrement(kind: kind)
                            ) { increment in
                                send(OnboardingEventStepMember(kind: kind, increment: increment))
                            }
                        }
                        .padding(.leading, 16)
                        .padding(.trailing, 10)
                        .padding(.vertical, 10)
                    }
                }
                .padding(.top, 24)

                GroupTitle(text: Text("Des animaux\u{00A0}?"))
                    .padding(.horizontal, 6)
                    .padding(.top, 22)
                    .padding(.bottom, 8)

                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                    ForEach(Self.pets, id: \.self) { pet in
                        let on = household.pets.contains(pet)
                        Button {
                            send(OnboardingEventTogglePet(pet: pet))
                        } label: {
                            HStack(spacing: 8) {
                                // The pet's face, with a check badge when it is chosen.
                                Illustration(Self.imageKey(pet), size: 28, pops: false)
                                    .overlay(alignment: .bottomTrailing) {
                                        if on {
                                            CheckBadge(size: 16, borderColor: Palette.basilLight, checkSize: 9, checkWidth: 1.5)
                                                .offset(x: 5, y: 4)
                                        }
                                    }
                                Text(verbatim: HouseholdTexts.petLabel(pet))
                                    .font(.custom(FontName.figtreeBold, size: 16, relativeTo: .callout))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.8)
                            }
                            .chipPop(trigger: on)
                        }
                        .buttonStyle(ChoiceTileStyle(selected: on, height: 52))
                        .accessibilityAddTraits(on ? .isSelected : [])
                    }
                }
            }
        } bottom: {
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventNext.shared) }) {
                Text("Continuer · \(Int(household.persons)) personnes")
                    .rollingNumber(Int(household.persons))
            }
        }
    }

    private static func imageKey(_ pet: PetChoice) -> String {
        switch pet {
        case .dog: ScreenIllustration.petDog
        case .cat: ScreenIllustration.petCat
        case .other: ScreenIllustration.petOther
        }
    }
}

/// The stepper of a member row: Surface douce, radius 14, 44 buttons around the count (18 / 700).
private struct MemberStepper: View {
    let label: String
    let count: Int
    let canDecrement: Bool
    let canIncrement: Bool
    let onStep: (_ increment: Bool) -> Void

    var body: some View {
        let lowercased = label.lowercased(with: .current)
        HStack(spacing: 2) {
            button(increment: false, enabled: canDecrement, label: Text("Un de moins\u{00A0}: \(lowercased)"))
            Text(verbatim: "\(count)")
                .font(.custom(FontName.figtreeBold, size: 18, relativeTo: .body))
                .foregroundStyle(Palette.ink)
                .monospacedDigit()
                .frame(minWidth: 28)
                .rollingNumber(count)
                .accessibilityLabel(Text(verbatim: label))
                .accessibilityValue(Text(verbatim: "\(count)"))
            button(increment: true, enabled: canIncrement, label: Text("Un de plus\u{00A0}: \(lowercased)"))
        }
        .padding(2)
        .background(Palette.surfaceSoft, in: .rect(cornerRadius: Radius.stepperButton))
    }

    private func button(increment: Bool, enabled: Bool, label: Text) -> some View {
        Button {
            onStep(increment)
        } label: {
            IconView(increment ? .plus : .minus, size: 18, weight: .bold)
                .foregroundStyle(enabled ? Palette.ink : Palette.checkboxBorder)
                .frame(width: Target.minimum, height: Target.minimum)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }
}

/// Step 2 « Comment mangez-vous ? » (mockup `Onboarding-Diet`): four exclusive diets.
struct DietStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    private static let diets: [DietChoice] = [.omnivore, .pescatarian, .vegetarian, .vegan]

    var body: some View {
        OnboardingStepLayout {
            VStack(alignment: .leading, spacing: 0) {
                OnboardingHeader(
                    overline: Text(verbatim: HouseholdTexts.sectionTitle(.food)),
                    imageKey: ScreenIllustration.onboardingFood,
                    title: Text("Comment mangez-vous\u{00A0}?"),
                    subtitle: Text("On ne vous proposera jamais un produit qui ne vous correspond pas.")
                )
                .padding(.top, 32)

                VStack(spacing: 8) {
                    ForEach(Self.diets, id: \.self) { diet in
                        DietRow(diet: diet, selected: state.diet == diet) {
                            send(OnboardingEventSelectDiet(diet: diet))
                        }
                    }
                }
                .padding(.top, 22)
                .accessibilityElement(children: .contain)
                .accessibilityLabel("Régime du foyer")
            }
        } bottom: {
            PrivacyNote(text: Text("Reste sur votre téléphone, jamais partagé."))
                .padding(.bottom, 14)
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventNext.shared) }) {
                Text("Continuer")
            }
        }
    }
}

/// A diet: radio button, name and hint, in its own tile (radius 18). Selected: Basilic clair, 2 pt Basilic border.
private struct DietRow: View {
    let diet: DietChoice
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .strokeBorder(selected ? Palette.basil : Palette.checkboxBorder, lineWidth: 2)
                    if selected {
                        Circle()
                            .fill(Palette.basil)
                            .frame(width: 10, height: 10)
                    }
                }
                .frame(width: 22, height: 22)
                TitleHint(
                    title: Text(verbatim: HouseholdTexts.dietLabel(diet)),
                    hint: Text(verbatim: HouseholdTexts.dietHint(diet))
                )
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
            .background(selected ? Palette.basilLight : Palette.surface, in: .rect(cornerRadius: Radius.button))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.button)
                    .strokeBorder(selected ? Palette.basil : Palette.line, lineWidth: selected ? 2 : 1)
            }
            .contentShape(.rect(cornerRadius: Radius.button))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(selected ? [.isButton, .isSelected] : .isButton)
    }
}

/// Step 2 « Quelque chose à éviter ? » (mockup `Onboarding-Avoid`), an image at the end of each line: the avoidances
/// already implied by the diet are checked in grey and cannot be changed.
struct AvoidStepView: View {
    let state: OnboardingUiStateReady
    let send: (any OnboardingEvent) -> Void

    var body: some View {
        let chosen = state.avoid.filter { $0.checked && !$0.locked }.count
        OnboardingStepLayout {
            VStack(alignment: .leading, spacing: 0) {
                OnboardingHeader(
                    overline: Text(verbatim: HouseholdTexts.sectionTitle(.food)),
                    imageKey: ScreenIllustration.onboardingAvoid,
                    title: Text("Quelque chose à éviter\u{00A0}?"),
                    subtitle: Text("Allergie, intolérance ou simple choix\u{00A0}: ces produits ne vous seront plus proposés.")
                )
                .padding(.top, 32)

                OnboardingBlock {
                    ForEach(Array(state.avoid.enumerated()), id: \.offset) { index, option in
                        if index > 0 {
                            RowSeparator()
                        }
                        AvoidRow(option: option, diet: state.diet) {
                            send(OnboardingEventToggleAvoid(avoid: option.choice))
                        }
                    }
                }
                .padding(.top, 22)
            }
        } bottom: {
            OnboardingPrimaryButton(saving: state.saving, action: { send(OnboardingEventNext.shared) }) {
                if chosen > 0 {
                    Text("Continuer · \(chosen) à éviter")
                        .rollingNumber(chosen)
                } else {
                    Text("Continuer")
                }
            }
            Button("Rien à éviter") {
                send(OnboardingEventNothingToAvoid.shared)
            }
            .buttonStyle(.text)
            .disabled(state.saving)
            .padding(.top, 12)
        }
    }
}

private struct AvoidRow: View {
    let option: AvoidOptionUi
    let diet: DietChoice
    let action: () -> Void

    var body: some View {
        let label = HouseholdTexts.avoidLabel(option.choice)
        let hint = option.locked ? HouseholdTexts.avoidLocked(by: diet) : HouseholdTexts.avoidHint(option.choice)
        Button(action: action) {
            HStack(spacing: 3) {
                Checkbox(checked: option.checked, shape: .square, fill: option.locked ? Palette.checkboxBorder : Palette.basil)
                TitleHint(title: Text(verbatim: label), hint: Text(verbatim: hint))
                Spacer(minLength: 8)
                Illustration(Self.imageKey(option.choice), size: 34, pops: false)
            }
            .padding(.leading, 5)
            .padding(.trailing, 16)
            .padding(.vertical, 4)
            .frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .allowsHitTesting(!option.locked)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityValue(option.checked ? Text("Coché") : Text("Non coché"))
        .accessibilityHint(Text(verbatim: hint))
        .accessibilityAddTraits(option.checked ? [.isButton, .isSelected] : .isButton)
    }

    private static func imageKey(_ avoid: AvoidChoice) -> String {
        switch avoid {
        case .gluten: ScreenIllustration.avoidGluten
        case .lactose: ScreenIllustration.avoidLactose
        case .nuts: ScreenIllustration.avoidNuts
        case .eggs: ScreenIllustration.avoidEggs
        case .alcohol: ScreenIllustration.avoidAlcohol
        }
    }
}
