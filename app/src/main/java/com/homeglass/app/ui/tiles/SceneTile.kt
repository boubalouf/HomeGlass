package com.homeglass.app.ui.tiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Tuile de scène : une scène est une action ponctuelle, la tuile est donc toujours
 * présentée à l'état actif (fond clair, icône accentuée). L'appui exécute la scène.
 */
@Composable
fun SceneTile(
    model: TileUiModel,
    onRun: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeviceTile(
        model = model.copy(isActive = true),
        onClick = onRun,
        onLongClick = onLongClick,
        modifier = modifier,
        tint = model.tint ?: GlassTheme.colors.accent,
    )
}
