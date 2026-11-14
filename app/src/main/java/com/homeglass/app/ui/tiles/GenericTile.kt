package com.homeglass.app.ui.tiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Tuile de repli pour un domaine Home Assistant inconnu : icône générique et état
 * textuel, comportement identique aux autres tuiles.
 */
@Composable
fun GenericTile(
    model: TileUiModel,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeviceTile(
        model = model,
        onClick = onToggle,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}
