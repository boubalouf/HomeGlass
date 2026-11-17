package com.homeglass.app.ui.tiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Aiguillage vers la tuile adaptée au domaine de l'entité.
 *
 * Les domaines sans interaction spécifique (switch, input_boolean, climate, lock, fan,
 * vacuum, camera, alarm_control_panel, humidifier, weather…) utilisent la tuile standard,
 * dont l'icône et la couleur viennent de [TileVisuals]. Les domaines nécessitant une
 * interaction propre (lumière dimmable, volet, capteur, scène, enceinte) ont leur composant
 * dédié.
 */
@Composable
fun EntityTile(
    model: TileUiModel,
    onToggle: (TileUiModel) -> Unit,
    onLongClick: (TileUiModel) -> Unit,
    onLevelChange: (TileUiModel, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (model.category) {
        TileCategory.Light -> LightTile(
            model = model,
            onToggle = { onToggle(model) },
            onLongClick = { onLongClick(model) },
            onLevelChange = { onLevelChange(model, it) },
            modifier = modifier,
        )

        TileCategory.Sensor -> SensorTile(
            model = model,
            onLongClick = { onLongClick(model) },
            modifier = modifier,
        )

        TileCategory.Cover -> CoverTile(
            model = model,
            onToggle = { onToggle(model) },
            onLongClick = { onLongClick(model) },
            modifier = modifier,
        )

        TileCategory.Scene -> SceneTile(
            model = model,
            onRun = { onToggle(model) },
            onLongClick = { onLongClick(model) },
            modifier = modifier,
        )

        TileCategory.Media -> MediaTile(
            model = model,
            onClick = { onToggle(model) },
            onLongClick = { onLongClick(model) },
            modifier = modifier,
        )

        TileCategory.Clock -> ClockTile(
            model = model,
            onLongClick = { onLongClick(model) },
            modifier = modifier,
        )

        TileCategory.Photo -> PhotoTile(
            model = model,
            onLongClick = { onLongClick(model) },
            modifier = modifier,
        )

        TileCategory.Calendar -> CalendarTile(
            model = model,
            onLongClick = { onLongClick(model) },
            modifier = modifier,
        )

        TileCategory.Spotify -> SpotifyTile(model = model, modifier = modifier)

        // Switch, climate, lock, alarm, camera, vacuum, generic : disposition standard.
        else -> DeviceTile(
            model = model,
            onClick = { onToggle(model) },
            onLongClick = { onLongClick(model) },
            modifier = modifier,
        )
    }
}
