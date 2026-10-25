import SwiftUI

enum SwipeDirection: Equatable {
    /// Right: « Oui ».
    case yes
    /// Left: « Non ».
    case no
}

/// A request to send the card away, from a button rather than a drag: the card flies off, then `perform` runs.
struct SwipeCommand: Equatable {
    let id = UUID()
    let direction: SwipeDirection
    let perform: () -> Void

    static func == (lhs: SwipeCommand, rhs: SwipeCommand) -> Bool {
        lhs.id == rhs.id
    }
}

/// A card that answers by swiping: right = « Oui », left = « Non ». The buttons of the screen send the same
/// answers through `command`, so the swipe is never the only way (and VoiceOver users never need it).
///
/// Give each question its own identity (`.id(productId)`): the card flies off, the state moves on, and the next
/// card is a new view that starts centered.
struct SwipeableCard<Content: View>: View {
    @Binding var command: SwipeCommand?
    /// Whether the drag is recognized (off when the content scrolls, at accessibility text sizes).
    var dragEnabled = true
    let onSwipe: (SwipeDirection) -> Void
    @ViewBuilder let content: Content

    @State private var offset: CGFloat = 0
    @State private var leaving = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let threshold: CGFloat = 110

    var body: some View {
        content
            .overlay(alignment: .top) { stamp }
            .offset(x: offset)
            .rotationEffect(.degrees(reduceMotion ? 0 : Double(offset / 24)), anchor: .bottom)
            .gesture(drag, including: dragEnabled && !leaving ? .all : .subviews)
            .onChange(of: command) { _, newValue in
                guard let newValue else { return }
                command = nil
                fling(newValue.direction, then: newValue.perform)
            }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 16)
            .onChanged { value in
                // Mostly vertical drags are not answers.
                guard abs(value.translation.width) > abs(value.translation.height) * 0.8 || offset != 0 else { return }
                offset = value.translation.width
            }
            .onEnded { value in
                let predicted = value.predictedEndTranslation.width
                if offset > threshold || (offset > 40 && predicted > threshold * 2) {
                    fling(.yes) { onSwipe(.yes) }
                } else if offset < -threshold || (offset < -40 && predicted < -threshold * 2) {
                    fling(.no) { onSwipe(.no) }
                } else {
                    withAnimation(.spring(duration: 0.3, bounce: 0.35)) { offset = 0 }
                }
            }
    }

    private func fling(_ direction: SwipeDirection, then perform: @escaping () -> Void) {
        guard !leaving else { return }
        leaving = true
        if reduceMotion {
            perform()
            scheduleReturn()
            return
        }
        let target: CGFloat = direction == .yes ? 700 : -700
        withAnimation(.easeIn(duration: 0.2)) {
            offset = target
        } completion: {
            perform()
            scheduleReturn()
        }
    }

    /// Safety net: if the state did not move on (the answer was ignored), bring the card back.
    private func scheduleReturn() {
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.2))
            leaving = false
            withAnimation(.spring(duration: 0.35)) { offset = 0 }
        }
    }

    /// « OUI » / « NON », fading in with the drag.
    @ViewBuilder
    private var stamp: some View {
        let progress = min(abs(offset) / threshold, 1)
        if offset != 0 && !leaving {
            let isYes = offset > 0
            Text(isYes ? "OUI" : "NON")
                .font(.largeTitle.weight(.heavy))
                .foregroundStyle(isYes ? Color.green : Color.red)
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .overlay {
                    RoundedRectangle(cornerRadius: 10).strokeBorder(isYes ? Color.green : Color.red, lineWidth: 4)
                }
                .rotationEffect(.degrees(isYes ? -12 : 12))
                .frame(maxWidth: .infinity, alignment: isYes ? .leading : .trailing)
                .padding(24)
                .opacity(progress)
                .accessibilityHidden(true)
        }
    }
}
