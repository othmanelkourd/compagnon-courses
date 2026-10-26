import Shared
import SwiftUI

/// The T1 screen « Probablement nécessaire » (pre-checked) and the products behind a gate (unchecked), mockup
/// `Suggested`: check, adjust the quantities, then validate everything in one tap.
struct ItemChecklistView: View {
    let title: Text
    let subtitle: Text
    let items: [SuggestedItemUi]
    let checkedCount: Int
    let canUndo: Bool
    let send: (any QuestionEvent) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 8) {
                    title
                        .textStyle(.screenTitle)
                        .foregroundStyle(Palette.ink)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)
                    subtitle
                        .textStyle(.bodyCompact)
                        .foregroundStyle(Palette.inkSoft)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.horizontal, Spacing.screen)
                .padding(.top, 24)
                .padding(.bottom, 16)

                ListBlock(data: items, id: \.productId) { item in
                    SuggestionRow(item: item, send: send)
                }
                .padding(.horizontal, Spacing.block)
            }
            .padding(.bottom, 16)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: 12) {
                Button {
                    send(QuestionEventConfirmItems.shared)
                } label: {
                    if checkedCount == 0 {
                        Text("Aucun article coché")
                    } else {
                        Text("Ajouter \(checkedCount) articles")
                    }
                }
                .buttonStyle(.primary)
                .disabled(checkedCount == 0)

                QuestionFooter(canUndo: canUndo, onUndo: { send(QuestionEventUndo.shared) }) {
                    Button("Rien de tout ça", action: confirmNothing)
                        .buttonStyle(.text)
                        .accessibilityHint("N'ajoute aucun de ces articles")
                }
            }
            .padding(.horizontal, Spacing.screen)
            .padding(.top, 20)
            .padding(.bottom, 4)
            .background { BottomFade() }
        }
    }

    /// Unchecks every line, then validates: nothing is added.
    private func confirmNothing() {
        for item in items where item.checked {
            send(QuestionEventToggleItem(productId: item.productId))
        }
        send(QuestionEventConfirmItems.shared)
    }
}

/// Ligne suggestion: round checkbox, name (Encre discrète when unchecked), reason, compact stepper.
private struct SuggestionRow: View {
    let item: SuggestedItemUi
    let send: (any QuestionEvent) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        // At accessibility text sizes the stepper goes under the name.
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(spacing: 6))
        layout {
            Button {
                send(QuestionEventToggleItem(productId: item.productId))
            } label: {
                HStack(spacing: 6) {
                    Checkbox(checked: item.checked, shape: .round)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: item.name)
                            .textStyle(.listRow)
                            .foregroundStyle(item.checked ? Palette.ink : Palette.inkMuted)
                        if let reason = item.reason {
                            ReasonText.text(for: reason, compact: true)
                                .textStyle(.caption)
                                .foregroundStyle(Palette.inkSoft)
                        }
                    }
                    .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .frame(minHeight: Target.checkbox)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: item.name))
            .accessibilityValue(item.checked ? Text("Coché, \(item.quantity.selected.label)") : Text("Non coché"))
            .accessibilityHint(item.reason.map { ReasonText.text(for: $0, compact: true) } ?? Text(verbatim: ""))
            .accessibilityAddTraits(item.checked ? [.isButton, .isSelected] : .isButton)

            CompactStepper(stepper: item.quantity, dimmed: !item.checked) { increment in
                send(QuestionEventStepItem(productId: item.productId, increment: increment))
            }
            // Its natural width first (the quantity is capped): the name wraps before a quantity does.
            .layoutPriority(1)
        }
        .padding(.vertical, 8)
        .padding(.leading, 4)
        .padding(.trailing, 10)
    }
}
