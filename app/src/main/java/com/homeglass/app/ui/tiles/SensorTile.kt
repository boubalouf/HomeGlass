package com.homeglass.app.ui.tiles

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Tuile de capteur : la valeur est mise en avant en grand, le nom passe en légende.
 * Couleur neutre par défaut ; la surcharge de teinte de la configuration reste possible.
 */
@Composable
fun SensorTile(
    model: TileUiModel,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography

    // Même règle que les tuiles standard : couleur de texte selon la luminance du fond.
    val tileBg = if (model.isActive && model.isAvailable) {
        colors.tileActiveBackground
    } else {
        colors.tileInactiveBackground
    }
    val luminance = 0.2126f * tileBg.red + 0.7152f * tileBg.green + 0.0722f * tileBg.blue
    val valueColor = if (luminance < 0.5f) colors.textOnInactivePrimary else Color(0xFF1C1C1E)
    val nameColor = if (luminance < 0.5f) colors.textOnInactiveSecondary else Color(0xFF54545A)

    DeviceTile(
        model = model,
        onClick = onLongClick,
        onLongClick = onLongClick,
        modifier = modifier,
        bottomContent = {
            Column {
                Text(
                    text = model.stateText,
                    style = typography.sensorValue,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.name,
                    style = typography.tileStatus,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        // La valeur est déjà une icône parlante : sans pictogramme, le nom sous la valeur
        // dispose de toute la hauteur de la tuile et n'est plus rogné.
        showIcon = false,
    )
}
