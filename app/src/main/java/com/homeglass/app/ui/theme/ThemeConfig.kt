package com.homeglass.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThemeMode { Auto, Light, Dark }

/** Fond d'écran fourni par l'add-on : image distante + assombrissement et flou réglables. */
@Immutable
data class WallpaperConfig(
    val url: String? = null,
    val scrim: Float = 0.35f,
    val blurRadius: Dp = 0.dp,
)

/**
 * Configuration du thème, telle qu'elle sera reçue de l'add-on HomeGlass Server.
 *
 * Tous les champs sont optionnels : les valeurs par défaut reproduisent le design system
 * "Apple Home" décrit dans la spécification.
 */
@Immutable
data class ThemeConfig(
    val mode: ThemeMode = ThemeMode.Auto,
    val accentColor: Color? = null,
    val tileCornerRadius: Dp? = null,
    val densityScale: Float = 1f,
    val wallpaper: WallpaperConfig? = null,
)
