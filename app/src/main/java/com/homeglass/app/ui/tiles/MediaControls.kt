package com.homeglass.app.ui.tiles

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Actions de lecture fournies une fois à la racine de l'application.
 *
 * Les tuiles média (notamment la grande case en lecture) affichent des boutons sans avoir à
 * faire descendre le contrôleur dans toute la hiérarchie des grilles.
 */
data class MediaControlHandlers(
    val playPause: (String) -> Unit,
    val next: (String) -> Unit,
    val previous: (String) -> Unit,
)

val LocalMediaControls = staticCompositionLocalOf<MediaControlHandlers?> { null }
