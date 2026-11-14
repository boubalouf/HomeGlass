package com.homeglass.app.ui.tiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Tuile de lumière. Si la lumière est dimmable ([TileUiModel.level] non nul),
 * un glissement horizontal règle la luminosité et remplit la tuile de gauche à droite.
 * L'appui court bascule la lumière, l'appui long ouvre la feuille de détail.
 */
@Composable
fun LightTile(
    model: TileUiModel,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    onLevelChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = TileVisuals.color(model, GlassTheme.colors)
    val motion = GlassTheme.motion

    val currentLevel = model.level ?: 0f
    val dimmable = model.level != null && model.isAvailable

    var dragging by remember(model.id) { mutableStateOf(false) }
    var dragLevel by remember(model.id) { mutableFloatStateOf(currentLevel) }
    var tileWidthPx by remember { mutableFloatStateOf(1f) }

    // Le niveau arrive de Home Assistant : on le reprend sauf pendant un glissement local.
    LaunchedEffect(currentLevel) {
        if (!dragging) dragLevel = currentLevel
    }
    val shownLevel by animateFloatAsState(
        targetValue = if (dragging) dragLevel else currentLevel,
        animationSpec = motion.state(),
        label = "lightLevel",
    )

    DeviceTile(
        model = model,
        onClick = onToggle,
        onLongClick = onLongClick,
        modifier = modifier
            .onSizeChanged { tileWidthPx = it.width.coerceAtLeast(1).toFloat() }
            .then(
                if (dimmable) {
                    Modifier.pointerInput(model.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragCancel = { dragging = false },
                            onDragEnd = {
                                dragging = false
                                onLevelChange(dragLevel)
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            dragLevel = (dragLevel + dragAmount / tileWidthPx).coerceIn(0f, 1f)
                        }
                    }
                } else {
                    Modifier
                },
            ),
        tint = tint,
        backgroundOverlay = {
            if (shownLevel > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(shownLevel)
                        .background(tint.copy(alpha = 0.18f)),
                )
            }
        },
    )
}
