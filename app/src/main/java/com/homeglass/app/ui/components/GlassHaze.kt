package com.homeglass.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

/**
 * Couche de flou partagée de l'application.
 *
 * L'écran déclare **une** [HazeState] pour tout son contenu : le fond et la liste marqués
 * [glassHazeSource] sont capturés, et chaque panneau en verre ([glassHazePanel]) en floute
 * le contenu. La même couche servira à la feuille de détail : un seul état, plusieurs
 * panneaux, un seul rendu du fond.
 *
 * Le flou réel s'appuie sur `RenderEffect`, disponible à partir d'Android 12. En dessous,
 * [glassHazePanel] applique le repli translucide déjà validé (teinte du thème) et aucune
 * couche de capture n'est créée.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Rayon de flou des panneaux en verre. */
private val PanelBlurRadius: Dp = 24.dp

/** Le flou n'est possible qu'à partir d'Android 12 (`RenderEffect`). */
private val blurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Fournit l'état Haze à tout le sous-arbre. */
@Composable
fun GlassHazeHost(content: @Composable () -> Unit) {
    val state = remember { HazeState() }
    CompositionLocalProvider(LocalHazeState provides state, content = content)
}

/** Marque le contenu (fond, liste) comme source du flou des panneaux. */
@Composable
fun Modifier.glassHazeSource(): Modifier {
    val state = LocalHazeState.current ?: return this
    if (!blurSupported) return this
    return this.haze(state)
}

/**
 * Panneau en verre : flou du contenu source sur Android 12+, sinon teinte translucide.
 * La forme est appliquée dans les deux cas pour que le contour reste identique.
 */
@Composable
fun Modifier.glassHazePanel(
    shape: Shape,
    tint: Color,
    blurRadius: Dp = PanelBlurRadius,
): Modifier {
    val state = LocalHazeState.current
    if (state == null || !blurSupported) {
        return this.clip(shape).background(tint)
    }
    return this
        .clip(shape)
        .hazeChild(state) {
            backgroundColor = tint
            this.blurRadius = blurRadius
        }
}
