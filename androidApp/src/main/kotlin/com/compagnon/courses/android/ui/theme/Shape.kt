package com.compagnon.courses.android.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The radii of the charter (`design/CHARTE.md` § 3). */
object CompagnonShapes {
    /** Product card and aisle card. */
    val Card = RoundedCornerShape(28.dp)

    /** Options sheet, top corners only. */
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Home cards. */
    val BlockLarge = RoundedCornerShape(24.dp)

    /** List blocks, suggestion block, boxed stepper, large meta choices, option groups. */
    val Block = RoundedCornerShape(20.dp)

    /** Buttons. */
    val Button = RoundedCornerShape(18.dp)

    /** Fields, snackbar, quantity choices. */
    val Field = RoundedCornerShape(16.dp)

    /** Free stepper buttons, unit selector. */
    val Control = RoundedCornerShape(14.dp)

    /** Compact stepper, option tile, hint. */
    val Small = RoundedCornerShape(12.dp)

    /** Square checkbox. */
    val Checkbox = RoundedCornerShape(8.dp)

    /** Chips. */
    val Pill = CircleShape
}

/** The Material slots mapped onto [CompagnonShapes]. */
internal val CompagnonMaterialShapes = Shapes(
    extraSmall = CompagnonShapes.Checkbox,
    small = CompagnonShapes.Small,
    medium = CompagnonShapes.Field,
    large = CompagnonShapes.Block,
    extraLarge = CompagnonShapes.Card,
)

/** The spacing of the charter (4 dp grid). */
object CompagnonSpacing {
    /** Side margin of a screen. */
    val Screen = 20.dp

    /** Side margin of list blocks. */
    val Block = 16.dp

    /** Below the status bar (the mockup's 56 is the status bar plus about 8). */
    val Top = 8.dp

    /** Below the content, above the navigation bar. */
    val Bottom = 12.dp
}
