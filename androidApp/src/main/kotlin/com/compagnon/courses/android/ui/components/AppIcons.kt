package com.compagnon.courses.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The line icons of the charter (`design/CHARTE.md` § 4, « Icônes »): the SVG paths of the mockup
 * (`design/maquette/`), 24 × 24, stroked about 2 wide with round caps and joins. They are drawn in black and tinted
 * by `Icon`. The Material Symbols name of each one is given for reference; no other icon family is used.
 */
internal object AppIcons {

    /** `close`. */
    val Close: ImageVector by lazy { lineIcon("Close", "M6 6l12 12M18 6 6 18") }

    /** `add`. */
    val Add: ImageVector by lazy { lineIcon("Add", "M5 12h14M12 5v14", strokeWidth = 2.4f) }

    /** `remove`. */
    val Remove: ImageVector by lazy { lineIcon("Remove", "M5 12h14", strokeWidth = 2.4f) }

    /** `more_horiz`: three filled dots. */
    val More: ImageVector by lazy {
        icon("More") {
            listOf(5f, 12f, 19f).forEach { x ->
                addPath(
                    pathData = addPathNodes("M${x - 1.8f} 12a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0z"),
                    fill = SolidColor(Color.Black),
                )
            }
        }
    }

    /** `undo`: the back-turning arrow of « Annuler ». */
    val Undo: ImageVector by lazy { lineIcon("Undo", "M9 14 4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3", autoMirror = true) }

    /** `check`. */
    val Check: ImageVector by lazy { lineIcon("Check", "M20 6 9 17l-5-5", strokeWidth = 2.6f) }

    /** `arrow_forward`. */
    val ArrowForward: ImageVector by lazy { lineIcon("ArrowForward", "M5 12h14m-6-6 6 6-6 6", autoMirror = true) }

    /** `arrow_back` (swipe hint « glisser : non »). */
    val ArrowBack: ImageVector by lazy { lineIcon("ArrowBack", "M19 12H5m6-6-6 6 6 6", autoMirror = true) }

    /** `chevron_right`. */
    val ChevronRight: ImageVector by lazy { lineIcon("ChevronRight", "m9 6 6 6-6 6", autoMirror = true) }

    /** `chevron_left` (back to the home screen). */
    val ChevronLeft: ImageVector by lazy { lineIcon("ChevronLeft", "m15 18-6-6 6-6", strokeWidth = 2.2f, autoMirror = true) }

    /** `expand_more` (« Plus d'options »). */
    val ChevronDown: ImageVector by lazy { lineIcon("ChevronDown", "m6 9 6 6 6-6", strokeWidth = 2.2f) }

    /** `info`. */
    val Info: ImageVector by lazy { lineIcon("Info", CIRCLE_9, "M12 11v5M12 8h.01") }

    /**
     * `shopping_basket` (SF Symbol `basket` on iOS): a handle over the rim, a tapered body with three slats. The
     * mockup draws a cart here; the charter asks for the basket on both platforms.
     */
    val Basket: ImageVector by lazy {
        lineIcon(
            "Basket",
            "M7 10 10 4.5h4L17 10",
            "M2.5 10h19",
            "M4.5 10l1.6 8.3a2 2 0 0 0 2 1.7h7.8a2 2 0 0 0 2-1.7L19.5 10",
            "M9.5 13.5v3M12 13.5v3M14.5 13.5v3",
            strokeWidth = 1.8f,
        )
    }

    /** `schedule`. */
    val Clock: ImageVector by lazy { lineIcon("Clock", "M12 7v5l3 2", CIRCLE_9) }

    /** `list`. */
    val ListBullets: ImageVector by lazy { lineIcon("List", "M9 6h11M9 12h11M9 18h11", "M4 6h.01M4 12h.01M4 18h.01") }

    /** `search`. */
    val Search: ImageVector by lazy {
        lineIcon("Search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0z", "m20 20-3.5-3.5")
    }

    /** `share`. */
    val Share: ImageVector by lazy {
        lineIcon("Share", "M12 15V3m0 0-4 4m4-4 4 4", "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7")
    }

    /** `calendar_today` (a week). */
    val Calendar: ImageVector by lazy { lineIcon("Calendar", "M8 3v3M16 3v3M4 9h16M5 6h14v14H5z") }

    /** `calendar_today` with a bar (a month). */
    val CalendarMonth: ImageVector by lazy { lineIcon("CalendarMonth", "M8 3v3M16 3v3M4 9h16M5 6h14v14H5zM9 14h6") }

    /** `block`. */
    val Block: ImageVector by lazy { lineIcon("Block", "M5.6 5.6l12.8 12.8", CIRCLE_9) }

    /** The « Il en reste un peu » levels (a low stock). */
    val Levels: ImageVector by lazy { lineIcon("Levels", "M4 18h16M6 14h12M9 10h6") }

    private const val CIRCLE_9 = "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z"

    private fun icon(name: String, autoMirror: Boolean = false, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror,
        ).apply(block).build()

    private fun lineIcon(
        name: String,
        vararg paths: String,
        strokeWidth: Float = 2f,
        autoMirror: Boolean = false,
    ): ImageVector = icon(name, autoMirror) {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
}
