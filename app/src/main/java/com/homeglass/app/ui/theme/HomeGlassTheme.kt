package com.homeglass.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homeglass.app.core.system.rememberReduceMotion

/**
 * Point d'entrée du thème HomeGlass.
 *
 * Material 3 n'est utilisé que comme socle technique (fournisseur de couleurs/typo pour
 * les primitives de texte et d'icône) : aucun composant Material n'est employé tel quel.
 * L'ensemble des valeurs est exposé via [GlassTheme] et surchargeable par [ThemeConfig].
 */
@Composable
fun HomeGlassTheme(
    config: ThemeConfig = ThemeConfig(),
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = rememberReduceMotion(),
    content: @Composable () -> Unit,
) {
    val isDark = when (config.mode) {
        ThemeMode.Auto -> systemInDarkTheme
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val accent = config.accentColor ?: GlassPalette.AccentOrange
    val colors = if (isDark) darkGlassColors(accent) else lightGlassColors(accent)
    val dimens = GlassDimens().scaled(config.densityScale)
    val typography = GlassTypography()
    val shapes = glassShapes(config.tileCornerRadius ?: GlassDefaults.TileCornerRadius)
    val motion = GlassMotion.of(reduceMotion)

    MaterialTheme(
        colorScheme = glassColorScheme(colors),
        typography = glassMaterialTypography(typography),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(config.tileCornerRadius ?: GlassDefaults.TileCornerRadius),
            large = RoundedCornerShape(GlassDefaults.SheetTopCornerRadius),
        ),
    ) {
        CompositionLocalProvider(
            LocalGlassColors provides colors,
            LocalGlassDimens provides dimens,
            LocalGlassTypography provides typography,
            LocalGlassShapes provides shapes,
            LocalGlassMotion provides motion,
            content = content,
        )
    }
}

/** Accès aux jetons de design depuis n'importe quel composable. */
object GlassTheme {
    val colors: GlassColors
        @Composable @ReadOnlyComposable get() = LocalGlassColors.current

    val dimens: GlassDimens
        @Composable @ReadOnlyComposable get() = LocalGlassDimens.current

    val typography: GlassTypography
        @Composable @ReadOnlyComposable get() = LocalGlassTypography.current

    val shapes: GlassShapes
        @Composable @ReadOnlyComposable get() = LocalGlassShapes.current

    val motion: GlassMotion
        @Composable @ReadOnlyComposable get() = LocalGlassMotion.current
}

private fun glassColorScheme(colors: GlassColors) =
    if (colors.isDark) {
        darkColorScheme(
            primary = colors.accent,
            background = GlassPalette.DarkGradientStart,
            surface = GlassPalette.DarkGradientEnd,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = GlassPalette.LightGradientStart,
            surface = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
        )
    }
