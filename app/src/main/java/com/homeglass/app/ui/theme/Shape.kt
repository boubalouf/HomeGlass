package com.homeglass.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/** Rayons du design system. Le rayon des tuiles est surchargeable par la configuration. */
@Immutable
data class GlassShapes(
    val tile: Shape,
    val sheet: Shape,
    val pill: Shape,
    val chip: Shape,
    val navBar: Shape,
)

fun glassShapes(tileCornerRadius: Dp): GlassShapes = GlassShapes(
    tile = RoundedCornerShape(tileCornerRadius),
    sheet = RoundedCornerShape(
        topStart = GlassDefaults.SheetTopCornerRadius,
        topEnd = GlassDefaults.SheetTopCornerRadius,
    ),
    pill = RoundedCornerShape(percent = 50),
    chip = RoundedCornerShape(percent = 50),
    navBar = RoundedCornerShape(percent = 50),
)

val LocalGlassShapes = staticCompositionLocalOf { glassShapes(GlassDefaults.TileCornerRadius) }
