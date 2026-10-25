package com.compagnon.courses.android.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The few Material icons the app needs beyond `material-icons-core` (the extended set is a multi-megabyte
 * dependency). Paths copied from the Material Symbols « Undo » and « Remove » icons (Apache 2.0).
 */
internal object AppIcons {

    val Undo: ImageVector by lazy {
        materialIcon(name = "Filled.Undo", autoMirror = true) {
            materialPath {
                moveTo(12.5f, 8.0f)
                curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
                lineTo(2.0f, 7.0f)
                verticalLineToRelative(9.0f)
                horizontalLineToRelative(9.0f)
                lineToRelative(-3.62f, -3.62f)
                curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
                curveToRelative(3.54f, 0.0f, 6.55f, 2.31f, 7.6f, 5.5f)
                lineToRelative(2.37f, -0.78f)
                curveTo(21.08f, 11.03f, 17.15f, 8.0f, 12.5f, 8.0f)
                close()
            }
        }
    }

    val Remove: ImageVector by lazy {
        materialIcon(name = "Filled.Remove") {
            materialPath {
                moveTo(19.0f, 13.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
    }
}
