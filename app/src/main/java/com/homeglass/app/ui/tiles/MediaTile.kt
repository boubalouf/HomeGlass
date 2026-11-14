package com.homeglass.app.ui.tiles

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Tuile d'enceinte : pochette de la piste en cours à la place de l'icône, nom de l'appareil
 * puis titre et artiste. Un appui met la lecture en pause (sans couper l'alimentation), un
 * appui long ouvre le panneau de contrôle.
 *
 * La géométrie reste celle du design validé : même hauteur, même padding, mêmes typographies ;
 * la pochette occupe simplement l'emplacement de l'icône.
 */
@Composable
fun MediaTile(
    model: TileUiModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val motion = GlassTheme.motion

    val artwork = rememberMediaArtwork(
        model.media?.artworkUrl(LocalMediaArtworkBaseUrl.current),
    )
    val tint = TileVisuals.color(model, colors)
    val iconTint by animateColorAsState(
        targetValue = if (model.isActive && model.isAvailable) tint else colors.iconOnInactive,
        animationSpec = motion.state(),
        label = "mediaTileTint",
    )
    // Couleur de texte : actif vs inactif
    // Inactive: logique par fond détecté (blanc sur sombre, noir sur clair)
    // Couleur de texte selon la luminance du fond de la tuile.
    // Attention : Color.red/green/blue renvoient des Float 0..1 (pas 0..255).
    val tileBg = if (model.isActive) colors.tileActiveBackground else colors.tileInactiveBackground
    val luminance = 0.2126f * tileBg.red + 0.7152f * tileBg.green + 0.0722f * tileBg.blue
    val textColor = if (luminance < 0.5f) Color.White else Color.Black


    val nameColor by animateColorAsState(
        targetValue = textColor,
        animationSpec = motion.state(),
        label = "mediaTileNameColor",
    )
    val statusColor by animateColorAsState(
        targetValue = textColor,
        animationSpec = motion.state(),
        label = "mediaTileStatusColor",
    )

    val status = mediaStatusText(model)
    val description = stringResource(R.string.a11y_tile_description, model.name, status)
    val unavailableLabel = stringResource(R.string.state_unavailable)
    val typography = GlassTheme.typography

    TileShell(
        active = model.isActive && model.isAvailable,
        available = model.isAvailable,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            if (!model.isAvailable) stateDescription = unavailableLabel
        },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(dimens.tilePadding)) {
            val density = LocalDensity.current
            val nameLine = with(density) { typography.tileName.lineHeight.toDp() }
            val statusLine = with(density) { typography.tileStatus.lineHeight.toDp() }
            val statusSpace = if (status.isEmpty()) 0.dp else statusLine
            // Même règle que les autres tuiles : l'état ne doit jamais être coupé.
            val nameMaxLines =
                if (maxHeight - dimens.tileIcon >= nameLine * 2 + statusSpace + 2.dp) 2 else 1

            Column(modifier = Modifier.fillMaxSize()) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork,
                        contentDescription = null, // décoratif : la description est portée par la tuile
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(dimens.tileIcon)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Icon(
                        imageVector = TileVisuals.icon(TileCategory.Media),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(dimens.tileIcon),
                    )
                }
                Spacer(Modifier.weight(1f))
                TileTexts(
                    name = model.name,
                    status = status,
                    nameColor = nameColor,
                    statusColor = statusColor,
                    nameMaxLines = nameMaxLines,
                )
            }
        }
    }
}

/**
 * Ligne d'état d'une enceinte : « Titre · Artiste » quand Home Assistant publie la piste,
 * sinon l'état traduit (« En lecture », « En pause », « Indisponible »).
 */
@Composable
private fun mediaStatusText(model: TileUiModel): String {
    if (!model.isAvailable) return stringResource(R.string.state_unavailable)

    val title = model.media?.title?.takeIf { it.isNotBlank() } ?: model.stateText
    val artist = model.media?.artist?.takeIf { it.isNotBlank() }
    return when {
        title.isBlank() -> model.stateText
        artist == null -> title
        else -> stringResource(R.string.media_title_artist, title, artist)
    }
}
