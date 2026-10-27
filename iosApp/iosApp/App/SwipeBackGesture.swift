import UIKit

/// The screens hide the navigation bar (they draw the charter's own top bars), which turns off UIKit's edge swipe.
/// The charter keeps « glisser depuis le bord = quitter » on iOS, so the pop gesture is re-enabled whenever a screen
/// is pushed.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1 && !SwipeBack.isBlocked
    }
}

/// Turns the system edge swipe off while a pushed screen handles it itself: inside a step of « Mon foyer », the
/// swipe goes back one step instead of leaving the screen.
@MainActor
enum SwipeBack {
    static var isBlocked = false
}
