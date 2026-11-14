package com.homeglass.app.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Tuile de volet / store : le remplissage depuis le bas reflète la position,
 * la ligne d'état donne le pourcentage ("Fermé", "40 %", "Ouvert").
 */
@Composable
fun CoverTile(
    model: TileUiModel,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = TileVisuals.color(model, GlassTheme.colors)
    val position = (model.level ?: 0f).coerceIn(0f, 1f)

    DeviceTile(
        model = model,
        onClick = onToggle,
        onLongClick = onLongClick,
        modifier = modifier,
        tint = tint,
        backgroundOverlay = {
            if (position > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .fillMaxHeight(position)
                        .background(tint.copy(alpha = 0.16f)),
                )
            }
        },
    )
}
