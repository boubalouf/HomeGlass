package com.homeglass.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Valeurs dimensionnelles par défaut, reprises du design system. */
object GlassDefaults {
    val TileCornerRadius: Dp = 22.dp
    val SheetTopCornerRadius: Dp = 28.dp
    val TileHeight: Dp = 96.dp
    val ScreenPadding: Dp = 16.dp
    val GridSpacing: Dp = 12.dp
    val TilePadding: Dp = 14.dp
    val TileIcon: Dp = 28.dp
    val NavBarHeight: Dp = 64.dp
    val NavBarBottomMargin: Dp = 12.dp
    val MinTouchTarget: Dp = 48.dp
}

@Immutable
data class GlassDimens(
    val screenPadding: Dp = GlassDefaults.ScreenPadding,
    val gridSpacing: Dp = GlassDefaults.GridSpacing,
    val tileHeight: Dp = GlassDefaults.TileHeight,
    val tilePadding: Dp = GlassDefaults.TilePadding,
    val tileIcon: Dp = GlassDefaults.TileIcon,
    val navBarHeight: Dp = GlassDefaults.NavBarHeight,
    val navBarBottomMargin: Dp = GlassDefaults.NavBarBottomMargin,
    val minTouchTarget: Dp = GlassDefaults.MinTouchTarget,
) {
    /** Applique la densité du thème (0,85 compact → 1,30 aéré). */
    fun scaled(scale: Float): GlassDimens {
        val clamped = scale.coerceIn(0.85f, 1.30f)
        if (clamped == 1f) return this
        return copy(
            screenPadding = screenPadding * clamped,
            gridSpacing = gridSpacing * clamped,
            tileHeight = tileHeight * clamped,
            tilePadding = tilePadding * clamped,
        )
    }
}

val LocalGlassDimens = staticCompositionLocalOf { GlassDimens() }
