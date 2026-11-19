import Foundation
import Shared

/// The texts of the household (`design/TEXTES.md`, « Bienvenue et Mon foyer »): the choices of the steps and the
/// summaries of the recap, « Mon foyer » and the home card. Values are joined with « · »: « 2 adultes · 1 bébé ·
/// 1 enfant · 1 chat », « Végétarien · sans lactose », « Courses chaque semaine · 4 récurrents », « 4 personnes ·
/// Végétarien · 14 essentiels ». Same rules as Android's `HouseholdTexts`.
enum HouseholdTexts {
    static let separator = " · "

    // MARK: - Choices

    static func dietLabel(_ diet: DietChoice) -> String {
        switch diet {
        case .omnivore: String(localized: "Je mange de tout")
        case .pescatarian: String(localized: "Pescétarien")
        case .vegetarian: String(localized: "Végétarien")
        case .vegan: String(localized: "Végan")
        }
    }

    static func dietHint(_ diet: DietChoice) -> String {
        switch diet {
        case .omnivore: String(localized: "Aucune restriction")
        case .pescatarian: String(localized: "Du poisson, pas de viande")
        case .vegetarian: String(localized: "Ni viande, ni poisson")
        case .vegan: String(localized: "Aucun produit d'origine animale")
        }
    }

    static func avoidLabel(_ avoid: AvoidChoice) -> String {
        switch avoid {
        case .gluten: String(localized: "Gluten")
        case .lactose: String(localized: "Lactose")
        case .nuts: String(localized: "Fruits à coque")
        case .eggs: String(localized: "Œufs")
        case .alcohol: String(localized: "Alcool")
        }
    }

    static func avoidHint(_ avoid: AvoidChoice) -> String {
        switch avoid {
        case .gluten: String(localized: "Pâtes, pain, farine de blé…")
        case .lactose: String(localized: "Lait, fromages, yaourts…")
        case .nuts: String(localized: "Amandes, noix, noisettes…")
        case .eggs: String(localized: "Œufs et produits qui en contiennent")
        case .alcohol: String(localized: "Bières, vins et spiritueux")
        }
    }

    /// « Déjà exclu par le régime végan ».
    static func avoidLocked(by diet: DietChoice) -> String {
        let name = dietLabel(diet).lowercased(with: .current)
        return String(localized: "Déjà exclu par le régime \(name)")
    }

    static func petLabel(_ pet: PetChoice) -> String {
        switch pet {
        case .dog: String(localized: "Chien")
        case .cat: String(localized: "Chat")
        case .other: String(localized: "Autre")
        }
    }

    static func memberLabel(_ kind: MemberKind) -> String {
        switch kind {
        case .adults: String(localized: "Adultes")
        case .babies: String(localized: "Bébés")
        case .children: String(localized: "Enfants")
        case .teens: String(localized: "Ados")
        }
    }

    static func memberHint(_ kind: MemberKind) -> String {
        switch kind {
        case .adults: String(localized: "18 ans et plus")
        case .babies: String(localized: "Moins de 3 ans")
        case .children: String(localized: "De 3 à 12 ans")
        case .teens: String(localized: "De 13 à 17 ans")
        }
    }

    static func cadenceLabel(_ cadence: CadenceChoice) -> String {
        switch cadence {
        case .week: String(localized: "Semaine")
        case .twoWeeks: String(localized: "2 semaines")
        case .month: String(localized: "Mois")
        }
    }

    /// The label of a rhythm tile (« Autre durée » has no tile: it is the stepper).
    static func rhythmLabel(_ choice: RhythmChoice) -> String {
        switch choice {
        case .week: String(localized: "Chaque semaine")
        case .twoWeeks: String(localized: "Toutes les 2 semaines")
        case .month: String(localized: "Chaque mois")
        case .other: ""
        }
    }

    /// « 7 jours ».
    static func days(_ count: Int) -> String {
        String(localized: "\(count) jours")
    }

    // MARK: - Summaries

    /// « 2 adultes · 1 bébé · 1 enfant · 1 chat ».
    static func members(_ summary: HouseholdSummaryUi) -> String {
        var parts = [String(localized: "\(Int(summary.adults)) adultes")]
        if summary.babies > 0 { parts.append(String(localized: "\(Int(summary.babies)) bébés")) }
        if summary.children > 0 { parts.append(String(localized: "\(Int(summary.children)) enfants")) }
        if summary.teens > 0 { parts.append(String(localized: "\(Int(summary.teens)) ados")) }
        for pet in summary.pets {
            switch pet {
            case .dog: parts.append(String(localized: "1 chien"))
            case .cat: parts.append(String(localized: "1 chat"))
            case .other: parts.append(String(localized: "1 autre animal"))
            }
        }
        return parts.joined(separator: separator)
    }

    /// « Végétarien · sans lactose »: the diet, then the products avoided; « Je mange de tout » alone when nothing is.
    static func food(_ summary: HouseholdSummaryUi) -> String {
        var parts = [dietLabel(summary.diet)]
        for avoid in summary.avoid {
            switch avoid {
            case .gluten: parts.append(String(localized: "sans gluten"))
            case .lactose: parts.append(String(localized: "sans lactose"))
            case .nuts: parts.append(String(localized: "sans fruits à coque"))
            case .eggs: parts.append(String(localized: "sans œufs"))
            case .alcohol: parts.append(String(localized: "sans alcool"))
            }
        }
        return parts.joined(separator: separator)
    }

    /// « 14 produits ».
    static func essentials(_ summary: HouseholdSummaryUi) -> String {
        String(localized: "\(Int(summary.essentials)) produits")
    }

    /// « Courses chaque semaine · 4 récurrents ».
    static func rhythm(_ summary: HouseholdSummaryUi) -> String {
        let days = Int(summary.horizonDays)
        let shopping = switch days {
        case 7: String(localized: "Courses chaque semaine")
        case 14: String(localized: "Courses toutes les 2 semaines")
        case 30: String(localized: "Courses chaque mois")
        default: String(localized: "Courses tous les \(days) jours")
        }
        guard summary.recurring > 0 else { return shopping }
        return shopping + separator + String(localized: "\(Int(summary.recurring)) récurrents")
    }

    /// The home card: « 4 personnes · Végétarien · 14 essentiels ».
    static func card(_ summary: HouseholdSummaryUi) -> String {
        [
            String(localized: "\(Int(summary.persons)) personnes"),
            dietLabel(summary.diet),
            String(localized: "\(Int(summary.essentials)) essentiels")
        ].joined(separator: separator)
    }

    static func section(_ section: HouseholdSection, of summary: HouseholdSummaryUi) -> String {
        switch section {
        case .household: members(summary)
        case .food: food(summary)
        case .essentials: essentials(summary)
        case .rhythm: rhythm(summary)
        }
    }

    /// « Foyer », « Alimentation », « Essentiels », « Rythme ».
    static func sectionRow(_ section: HouseholdSection) -> String {
        switch section {
        case .household: String(localized: "Foyer")
        case .food: String(localized: "Alimentation")
        case .essentials: String(localized: "Essentiels")
        case .rhythm: String(localized: "Rythme")
        }
    }

    /// « Votre foyer », « Votre façon de manger », « Vos essentiels », « Votre rythme ».
    static func sectionTitle(_ section: HouseholdSection) -> String {
        switch section {
        case .household: String(localized: "Votre foyer")
        case .food: String(localized: "Votre façon de manger")
        case .essentials: String(localized: "Vos essentiels")
        case .rhythm: String(localized: "Votre rythme")
        }
    }

    static func sectionHint(_ section: HouseholdSection) -> String {
        switch section {
        case .household: String(localized: "Adultes, enfants, animaux")
        case .food: String(localized: "Régime et produits à éviter")
        case .essentials: String(localized: "Ce qui ne doit jamais manquer")
        case .rhythm: String(localized: "Produits récurrents et fréquence des courses")
        }
    }
}

extension HouseholdSection {
    /// The four sections, in the order of the questionnaire.
    static let ordered: [HouseholdSection] = [.household, .food, .essentials, .rhythm]
}
