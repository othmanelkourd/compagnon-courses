import Shared
import SwiftUI

/// The bottom row of every question: « Annuler » always at the bottom left, a contextual text button on the right.
struct QuestionFooter<Trailing: View>: View {
    let canUndo: Bool
    let onUndo: () -> Void
    @ViewBuilder let trailing: Trailing

    var body: some View {
        HStack(spacing: 8) {
            UndoButton(enabled: canUndo, action: onUndo)
            Spacer(minLength: 0)
            trailing
        }
    }
}

/// Two buttons side by side in the proportions of the charter (« Non » : « Oui » = 1 : 1.6).
struct RatioHStack: Layout {
    var ratios: [CGFloat] = [1, 1.6]
    var spacing: CGFloat = 10

    private func widths(for width: CGFloat, count: Int) -> [CGFloat] {
        let used = Array(ratios.prefix(count)) + Array(repeating: 1, count: max(0, count - ratios.count))
        let total = used.reduce(0, +)
        let available = max(0, width - spacing * CGFloat(max(count - 1, 0)))
        return used.map { available * $0 / total }
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        let columns = widths(for: width, count: subviews.count)
        let height = zip(subviews, columns)
            .map { $0.sizeThatFits(ProposedViewSize(width: $1, height: nil)).height }
            .max() ?? 0
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        for (subview, width) in zip(subviews, widths(for: bounds.width, count: subviews.count)) {
            subview.place(at: CGPoint(x: x, y: bounds.minY), proposal: ProposedViewSize(width: width, height: bounds.height))
            x += width + spacing
        }
    }
}

/// A centered spinner with a caption (loading, writing the list).
struct QuestionBusyView: View {
    let title: LocalizedStringKey

    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .controlSize(.large)
                .tint(Palette.basil)
            Text(title)
                .textStyle(.body)
                .foregroundStyle(Palette.inkSoft)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityElement(children: .combine)
    }
}

/// « Pourquoi cette question ? »: one line without a final period, built from `ReasonUi` (`design/TEXTES.md`). A
/// purchase cycle reads « Tous les 8 jours environ · dernier il y a 9 jours » on a card; a `compact` line (suggestion
/// rows) keeps only « Dernier achat il y a 9 jours », or « Acheté tous les 8 jours environ » when the last purchase is
/// unknown. Same rules as Android's `QuestionTexts.reason`.
enum ReasonText {
    static func text(for reason: ReasonUi, compact: Bool = false) -> Text {
        switch reason.kind {
        case .due:
            let days = reason.daysSinceLast?.intValue
            let interval = reason.intervalDays?.intValue
            if let days, let interval, !compact {
                return Text("Tous les \(interval) jours environ · dernier il y a \(days) jours")
            }
            if let days {
                return Text("Dernier achat il y a \(days) jours")
            }
            if let interval {
                return Text("Acheté tous les \(interval) jours environ")
            }
            return Text("Bientôt le moment d'en racheter")
        case .staple:
            return Text("Un essentiel que vous achetez souvent")
        case .seasonal:
            return Text("C'est la saison")
        case .event:
            return Text("Pour l'événement qui approche")
        case .radar:
            if let weeks = reason.weeksSinceLast?.intValue {
                return Text("Pas acheté depuis \(weeks) semaines")
            }
            return Text("Pas acheté depuis un moment")
        case .prior:
            if let size = reason.householdSize?.intValue {
                return Text("Souvent acheté par les foyers de \(size) personnes")
            }
            return Text("Souvent acheté par les foyers comme le vôtre")
        case .exploration:
            return Text("Une idée à essayer")
        case .littleLeft:
            return Text("Il en restait un peu la dernière fois")
        case .lastList:
            return Text("Sur votre dernière liste")
        case .cadence:
            // The rhythm given in « Mon foyer », for a product never bought yet.
            switch reason.intervalDays?.intValue {
            case 7: return Text("Vous en achetez chaque semaine")
            case 14: return Text("Vous en achetez toutes les 2 semaines")
            case 30: return Text("Vous en achetez chaque mois")
            case let days?: return Text("Vous en achetez tous les \(days) jours")
            case nil: return Text("Bientôt le moment d'en racheter")
            }
        }
    }
}

/// The reason of a question as an « Indice » (info icon, Aide text); VoiceOver reads « Pourquoi cette question : … ».
struct ReasonHint: View {
    let reason: ReasonUi

    var body: some View {
        Hint(text: ReasonText.text(for: reason))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Pourquoi cette question")
            .accessibilityValue(ReasonText.text(for: reason))
    }
}
