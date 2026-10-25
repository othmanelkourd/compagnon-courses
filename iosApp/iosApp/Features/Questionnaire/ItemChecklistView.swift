import Shared
import SwiftUI

/// The T1 screen « Probablement nécessaire » (pre-checked) and the products behind a gate (unchecked): check,
/// adjust the quantities, then validate everything in one tap.
struct ItemChecklistView: View {
    let title: Text
    let subtitle: Text
    let items: [SuggestedItemUi]
    let checkedCount: Int
    let send: (any QuestionEvent) -> Void

    var body: some View {
        List {
            // The title is a clear row rather than a section header, which would dim and shrink it.
            VStack(alignment: .leading, spacing: 6) {
                title
                    .font(.largeTitle.bold())
                    .accessibilityAddTraits(.isHeader)
                subtitle
                    .font(.body)
                    .foregroundStyle(.secondary)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 8, leading: 4, bottom: 0, trailing: 4))
            Section {
                ForEach(items, id: \.productId) { item in
                    ChecklistRow(item: item, send: send)
                }
            }
        }
        .listStyle(.insetGrouped)
        .safeAreaInset(edge: .bottom) {
            Button {
                send(QuestionEventConfirmItems.shared)
            } label: {
                if checkedCount == 0 {
                    Text("Rien de tout ça")
                } else {
                    Text("Ajouter \(checkedCount) articles")
                }
            }
            .buttonStyle(.big(checkedCount == 0 ? .secondary : .primary))
            .padding()
            .background(.bar)
        }
    }
}

private struct ChecklistRow: View {
    let item: SuggestedItemUi
    let send: (any QuestionEvent) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        // At accessibility text sizes the stepper goes under the name.
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(spacing: 12))
        layout {
            Button {
                send(QuestionEventToggleItem(productId: item.productId))
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: item.checked ? "checkmark.circle.fill" : "circle")
                        .font(.title2)
                        .foregroundStyle(item.checked ? Color.accentColor : Color.secondary)
                        .contentTransition(.symbolEffect(.replace))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: item.name)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.primary)
                        if let reason = item.reason {
                            ReasonText.text(for: reason)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .multilineTextAlignment(.leading)
                    Spacer(minLength: 0)
                }
                .frame(minHeight: 44)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(verbatim: item.name))
            .accessibilityValue(item.checked ? Text("Coché, \(item.quantity.selected.label)") : Text("Non coché"))
            .accessibilityHint(item.reason.map { ReasonText.text(for: $0) } ?? Text(verbatim: ""))
            .accessibilityAddTraits(item.checked ? [.isButton, .isSelected] : .isButton)

            QuantityStepperView(stepper: item.quantity, compact: true) { increment in
                send(QuestionEventStepItem(productId: item.productId, increment: increment))
            }
            .opacity(item.checked ? 1 : 0.45)
        }
    }
}
