package com.homeglass.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette brute du design system "Apple Home".
 * Ces valeurs sont les valeurs par défaut : chacune peut être surchargée par le thème
 * envoyé par l'add-on HomeGlass Server (voir [ThemeConfig]).
 */
object GlassPalette {
    // Fonds plein écran
    val LightGradientStart = Color(0xFFF2F2F7)
    val LightGradientEnd = Color(0xFFE5E5EA)
    val DarkGradientStart = Color(0xFF000000)
    val DarkGradientEnd = Color(0xFF1C1C1E)

    // Accent par défaut
    val AccentOrange = Color(0xFFFF9500)

    // Couleurs de catégorie
    val CategoryLight = Color(0xFFFFB800)
    val CategoryHeat = Color(0xFFFF9500)
    val CategoryCool = Color(0xFF0A84FF)
    val CategorySwitch = Color(0xFF34C759)
    val CategorySecure = Color(0xFF34C759)
    val CategoryWarning = Color(0xFFFF9500)
    val CategoryDanger = Color(0xFFFF3B30)
    val CategoryMedia = Color(0xFFFF375F)
    val CategoryCover = Color(0xFF64D2FF)
    val CategorySensor = Color(0xFF8E8E93)
    val CategoryGeneric = Color(0xFF8E8E93)

    // Surfaces de tuile
    val ActiveTileLight = Color(0xFFFFFFFF)
    val ActiveTileDark = Color(0xFFF5F5F7)
    val InactiveTileLight = Color(0x8CFFFFFF) // blanc 55 %
    val InactiveTileDark = Color(0xB32C2C2E) // #2C2C2E à 70 %
}

/**
 * Jeu de couleurs sémantiques consommé par l'UI.
 * Les couleurs de texte respectent le contraste AA (≥ 4,5:1) sur leur propre surface.
 */
@Immutable
data class GlassColors(
    val isDark: Boolean,
    val backgroundGradient: List<Color>,
    val accent: Color,

    // Texte posé directement sur le fond d'écran
    val screenTextPrimary: Color,
    val screenTextSecondary: Color,

    // Tuiles
    val tileActiveBackground: Color,
    val tileInactiveBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textOnInactivePrimary: Color,
    val textOnInactiveSecondary: Color,
    val iconOnInactive: Color,
    val iconNeutral: Color,

    // Catégories
    val categoryLight: Color,
    val categoryHeat: Color,
    val categoryCool: Color,
    val categorySwitch: Color,
    val categorySecure: Color,
    val categoryWarning: Color,
    val categoryDanger: Color,
    val categoryMedia: Color,
    val categoryCover: Color,
    val categorySensor: Color,
    val categoryGeneric: Color,

    // Chrome (barre de navigation, chips, feuilles)
    val navBarBackground: Color,
    val navBarActiveContent: Color,
    val navBarInactiveContent: Color,
    val chipBackground: Color,
    val sheetBackground: Color,
    val sheetHandle: Color,
    val wallpaperScrim: Color,
)

fun lightGlassColors(accent: Color = GlassPalette.AccentOrange): GlassColors = GlassColors(
    isDark = false,
    backgroundGradient = listOf(GlassPalette.LightGradientStart, GlassPalette.LightGradientEnd),
    accent = accent,
    screenTextPrimary = Color(0xFF1C1C1E),
    screenTextSecondary = Color(0xFF6C6C70),
    tileActiveBackground = GlassPalette.ActiveTileLight,
    tileInactiveBackground = GlassPalette.InactiveTileLight,
    textPrimary = Color(0xFF000000),
    // #6C6C70 sur blanc : 5,2:1 — AA pour le statut 13 sp.
    textSecondary = Color(0xFF6C6C70),
    textOnInactivePrimary = Color(0xFF3C3C43),
    textOnInactiveSecondary = Color(0xFF54545A),
    iconOnInactive = Color(0xFF8A8A8E),
    iconNeutral = Color(0xFF6C6C70),
    categoryLight = GlassPalette.CategoryLight,
    categoryHeat = GlassPalette.CategoryHeat,
    categoryCool = GlassPalette.CategoryCool,
    categorySwitch = GlassPalette.CategorySwitch,
    categorySecure = GlassPalette.CategorySecure,
    categoryWarning = GlassPalette.CategoryWarning,
    categoryDanger = GlassPalette.CategoryDanger,
    categoryMedia = GlassPalette.CategoryMedia,
    categoryCover = GlassPalette.CategoryCover,
    categorySensor = GlassPalette.CategorySensor,
    categoryGeneric = GlassPalette.CategoryGeneric,
    navBarBackground = Color(0x9EFFFFFF),
    navBarActiveContent = accent,
    navBarInactiveContent = Color(0xFF6C6C70),
    chipBackground = Color(0xA6FFFFFF),
    sheetBackground = Color(0xEBF2F2F7),
    sheetHandle = Color(0x593C3C43),
    wallpaperScrim = Color.Black,
)

fun darkGlassColors(accent: Color = GlassPalette.AccentOrange): GlassColors = GlassColors(
    isDark = true,
    backgroundGradient = listOf(GlassPalette.DarkGradientStart, GlassPalette.DarkGradientEnd),
    accent = accent,
    screenTextPrimary = Color(0xFFF2F2F7),
    screenTextSecondary = Color(0xFFAEAEB2),
    // En sombre, une tuile active reste claire (comme dans Maison) : texte foncé sur fond clair.
    tileActiveBackground = GlassPalette.ActiveTileDark,
    tileInactiveBackground = GlassPalette.InactiveTileDark,
    textPrimary = Color(0xFF1C1C1E),
    textSecondary = Color(0xFF6C6C70),
    textOnInactivePrimary = Color(0xFFF2F2F7),
    textOnInactiveSecondary = Color(0xFFC7C7CC),
    iconOnInactive = Color(0xFFAEAEB2),
    iconNeutral = Color(0xFFAEAEB2),
    categoryLight = GlassPalette.CategoryLight,
    categoryHeat = GlassPalette.CategoryHeat,
    categoryCool = GlassPalette.CategoryCool,
    categorySwitch = GlassPalette.CategorySwitch,
    categorySecure = GlassPalette.CategorySecure,
    categoryWarning = GlassPalette.CategoryWarning,
    categoryDanger = GlassPalette.CategoryDanger,
    categoryMedia = GlassPalette.CategoryMedia,
    categoryCover = GlassPalette.CategoryCover,
    categorySensor = GlassPalette.CategorySensor,
    categoryGeneric = GlassPalette.CategoryGeneric,
    navBarBackground = Color(0x991C1C1E),
    navBarActiveContent = accent,
    navBarInactiveContent = Color(0xFFAEAEB2),
    chipBackground = Color(0xA62C2C2E),
    sheetBackground = Color(0xEB1C1C1E),
    sheetHandle = Color(0x8E8E93),
    wallpaperScrim = Color.Black,
)

val LocalGlassColors = staticCompositionLocalOf { lightGlassColors() }
