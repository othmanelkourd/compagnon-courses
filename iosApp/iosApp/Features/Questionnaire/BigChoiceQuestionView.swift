import SwiftUI

/// The layout of a question answered with a few large choices and a boxed stepper (mockups `Meta` and
/// `Onboarding-Rhythm`): overline, title and subtitle, a grid of large tiles, « Autre durée : 7 jours », then the
/// buttons of the caller at the bottom. Same component as Android's `BigChoiceQuestion`.
struct BigChoiceQuestionView<Choice: Hashable, TileLabel: View, Bottom: View>: View {
    /// Sizes that differ between the meta questions and the onboarding.
    struct Metrics {
        var titleStyle: TextStyle
        var subtitleStyle: TextStyle
        var top: CGFloat
        var gridTop: CGFloat
        var tileHeight: CGFloat
        var tileRadius: CGFloat

        /// Meta questions: title 34, subtitle 17, tiles 88 high.
        static var meta: Metrics {
            Metrics(titleStyle: .screenTitleLarge, subtitleStyle: .body, top: 40, gridTop: 32, tileHeight: 88, tileRadius: Radius.tile)
        }

        /// Onboarding steps (under their top bar): title 32, subtitle 16, tiles 96 high with radius 20.
        static var onboarding: Metrics {
            Metrics(titleStyle: .screenTitle, subtitleStyle: .bodyCompact, top: 32, gridTop: 28, tileHeight: 96, tileRadius: Radius.block)
        }
    }

    let overline: Text
    let title: Text
    let subtitle: Text
    var metrics: Metrics = .meta
    let choices: [Choice]
    /// Number of columns of the grid.
    let columns: Int
    let isSelected: (Choice) -> Bool
    let accessibilityLabel: (Choice) -> Text
    let onSelect: (Choice) -> Void
    @ViewBuilder let tileLabel: (Choice) -> TileLabel
    let stepper: BoxedStepper
    @ViewBuilder let bottom: Bottom

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 10) {
                        Overline(text: overline, color: Palette.basil)
                        title
                            .textStyle(metrics.titleStyle)
                            .foregroundStyle(Palette.ink)
                            .fixedSize(horizontal: false, vertical: true)
                            .accessibilityAddTraits(.isHeader)
                        subtitle
                            .textStyle(metrics.subtitleStyle)
                            .foregroundStyle(Palette.inkSoft)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: max(columns, 1)), spacing: 10) {
                        ForEach(choices, id: \.self) { choice in
                            Button {
                                onSelect(choice)
                            } label: {
                                tileLabel(choice)
                            }
                            .buttonStyle(ChoiceTileStyle(selected: isSelected(choice), height: metrics.tileHeight, radius: metrics.tileRadius))
                            .accessibilityLabel(accessibilityLabel(choice))
                        }
                    }
                    .padding(.top, metrics.gridTop)

                    stepper
                        .padding(.top, 16)
                }
                .padding(.top, metrics.top)
                .padding(.horizontal, Spacing.screen)
                .padding(.bottom, 16)
            }
            .scrollBounceBehavior(.basedOnSize)

            bottom
                .padding(.horizontal, Spacing.screen)
                .padding(.bottom, 4)
        }
    }
}
