package com.compagnon.courses.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.compagnon.courses.android.R

/** Titles: product name, screen titles, aisle and meta questions. Static instances bundled in `res/font` (OFL). */
val BricolageGrotesque = FontFamily(
    Font(R.font.bricolage_grotesque_medium, FontWeight.Medium),
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
)

/** Everything else. Static instances bundled in `res/font` (OFL). */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    family: FontFamily,
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing,
    lineHeightStyle = CenteredLineHeight,
)

/**
 * The type scale of the charter (`design/CHARTE.md` § 2), in `sp` so it follows the system font scale. Colours are
 * left to the call site.
 */
object CompagnonType {
    /** « Tomates » on the product card. */
    val ProductTitle = style(BricolageGrotesque, 44, 44, FontWeight.Bold, letterSpacing = (-0.8).sp)

    /** « Ma liste », the aisle question. */
    val ScreenTitleLarge = style(BricolageGrotesque, 34, 36, FontWeight.Bold, letterSpacing = (-0.5).sp)

    /** Meta question, « 16 articles à ajouter ». */
    val ScreenTitle = style(BricolageGrotesque, 32, 35, FontWeight.Bold, letterSpacing = (-0.4).sp)

    /** Title of the options sheet. */
    val SheetTitle = style(BricolageGrotesque, 24, 28, FontWeight.Bold)

    /** « Il vous faut des tomates ? » */
    val Question = style(Figtree, 20, 26, FontWeight.Normal)

    /** Value of a quantity choice. */
    val Value = style(Figtree, 20, 24, FontWeight.Bold)

    /** Value of a large (meta question) choice. */
    val ValueLarge = style(Figtree, 28, 32, FontWeight.Bold)

    /** « Oui · 1 kg ». */
    val ButtonPrimary = style(Figtree, 18, 22, FontWeight.Bold)

    /** Item name. */
    val ListRow = style(Figtree, 17, 22, FontWeight.SemiBold)

    /** Subtitles. */
    val Body = style(Figtree, 17, 23, FontWeight.Normal)

    /** Explanations. */
    val BodyCompact = style(Figtree, 16, 22, FontWeight.Normal)

    /** « Annuler », « Plus d'options ». */
    val ButtonText = style(Figtree, 16, 20, FontWeight.SemiBold)

    /** Quantity of a row, snackbar message. */
    val Secondary = style(Figtree, 15, 20, FontWeight.Normal)

    /** Reason, hint. */
    val Help = style(Figtree, 14, 20, FontWeight.Normal)

    /** « 5 / 12 ». */
    val Counter = style(Figtree, 14, 18, FontWeight.Bold)

    /** Line under an option. */
    val Caption = style(Figtree, 13, 17, FontWeight.Normal)

    /** Capitals, +0.8: « FRUITS ET LÉGUMES ». Upper-case the text at the call site. */
    val Overline = style(Figtree, 13, 16, FontWeight.Bold, letterSpacing = 0.8.sp)

    /** Chips (« Essentiel », « À surveiller »). */
    val Chip = style(Figtree, 12, 16, FontWeight.Bold)
}

/** The Material slots mapped onto [CompagnonType], for the Material components (dialogs, menus, fields). */
internal val CompagnonTypography = Typography(
    displayLarge = CompagnonType.ProductTitle,
    displayMedium = CompagnonType.ProductTitle,
    displaySmall = CompagnonType.ScreenTitleLarge,
    headlineLarge = CompagnonType.ScreenTitleLarge,
    headlineMedium = CompagnonType.ScreenTitle,
    headlineSmall = CompagnonType.SheetTitle,
    titleLarge = CompagnonType.Value,
    titleMedium = CompagnonType.ListRow,
    titleSmall = CompagnonType.ButtonText,
    bodyLarge = CompagnonType.Body,
    bodyMedium = CompagnonType.Secondary,
    bodySmall = CompagnonType.Help,
    labelLarge = CompagnonType.ButtonText,
    labelMedium = CompagnonType.Counter,
    labelSmall = CompagnonType.Caption,
)
