package com.compagnon.courses.android.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The colour tokens of the shared charter (`design/CHARTE.md` § 1), light mode only. Screens use these names;
 * Material components get them through [CompagnonColorScheme].
 */
object CompagnonColors {
    /** Main action, selection, progress. */
    val Basil = Color(0xFF1E6B45)

    /** Main button pressed. */
    val BasilDark = Color(0xFF15533A)

    /** Selected choice, positive badges, icon tiles. */
    val BasilLight = Color(0xFFE3F0E7)

    /** Main text, snackbar background. */
    val Ink = Color(0xFF17211B)

    /** Secondary text, reasons, overlines. */
    val InkSoft = Color(0xFF4A554E)

    /** Disabled text, checked item, « Retirer » icon. */
    val InkMuted = Color(0xFF6B756E)

    /** Screen background, option groups of the sheet. */
    val Background = Color(0xFFF5F6F1)

    /** Cards, lists, sheets, fields. */
    val Surface = Color(0xFFFFFFFF)

    /** Steppers, unit selector, hint, « soft » icon button. */
    val SurfaceSoft = Color(0xFFF1F3EE)

    /** Borders of cards, fields and list blocks. */
    val Line = Color(0xFFE2E5DE)

    /** Separators between the rows of a block. */
    val LineLight = Color(0xFFEDEFEA)

    /** Secondary button border, sheet handle. */
    val LineStrong = Color(0xFFCBD1C7)

    /** Progress segments not reached yet. */
    val SegmentEmpty = Color(0xFFDCE1D8)

    /** Border of an empty checkbox. */
    val CheckboxOutline = Color(0xFFAEB6AC)

    /** The aisle question card. */
    val DarkCard = Color(0xFF1F3A2C)

    /** Rare: « à surveiller » dot. */
    val Tomato = Color(0xFFD9482B)

    /** Text and icon of « Je n'en achète plus ». */
    val TomatoText = Color(0xFFB8361C)

    /** « Je n'en achète plus » tile, « À surveiller » chip. */
    val TomatoLight = Color(0xFFFBE7E2)

    /** « Annuler » on the snackbar. */
    val SnackbarAction = Color(0xFF9BD8B3)

    /** Tile of the « pause » answers of the options sheet (« Plus tard », « Pas cette fois »). */
    val PauseTint = Color(0xFFEEF0EA)

    /** Scrim behind the options sheet (Ink at 42 %). */
    val Scrim = Color(0x6B17211B)
}

/**
 * The Material colour scheme mapped onto the charter, for the Material components the app still uses
 * (bottom sheet, dialogs, menus, text fields, progress indicators). Fixed: no dynamic colour, no dark scheme yet.
 */
internal val CompagnonColorScheme = lightColorScheme(
    primary = CompagnonColors.Basil,
    onPrimary = Color.White,
    primaryContainer = CompagnonColors.BasilLight,
    onPrimaryContainer = CompagnonColors.BasilDark,
    inversePrimary = CompagnonColors.SnackbarAction,
    secondary = CompagnonColors.InkSoft,
    onSecondary = Color.White,
    secondaryContainer = CompagnonColors.BasilLight,
    onSecondaryContainer = CompagnonColors.BasilDark,
    tertiary = CompagnonColors.DarkCard,
    onTertiary = Color.White,
    tertiaryContainer = CompagnonColors.TomatoLight,
    onTertiaryContainer = CompagnonColors.TomatoText,
    background = CompagnonColors.Background,
    onBackground = CompagnonColors.Ink,
    surface = CompagnonColors.Surface,
    onSurface = CompagnonColors.Ink,
    surfaceVariant = CompagnonColors.SurfaceSoft,
    onSurfaceVariant = CompagnonColors.InkSoft,
    surfaceTint = Color.Transparent,
    inverseSurface = CompagnonColors.Ink,
    inverseOnSurface = Color.White,
    error = CompagnonColors.TomatoText,
    onError = Color.White,
    errorContainer = CompagnonColors.TomatoLight,
    onErrorContainer = CompagnonColors.TomatoText,
    outline = CompagnonColors.LineStrong,
    outlineVariant = CompagnonColors.Line,
    scrim = Color(0xFF17211B),
    surfaceBright = CompagnonColors.Surface,
    surfaceDim = CompagnonColors.Background,
    surfaceContainerLowest = CompagnonColors.Surface,
    surfaceContainerLow = CompagnonColors.Surface,
    surfaceContainer = CompagnonColors.Surface,
    surfaceContainerHigh = CompagnonColors.Surface,
    surfaceContainerHighest = CompagnonColors.SurfaceSoft,
)
