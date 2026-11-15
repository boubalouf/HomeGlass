package com.homeglass.app.ui.tiles

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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

            // La disposition suit la portée réelle de la case : large = pochette à gauche,
            // haute = pochette et textes centrés, standard = empilés.
            val wide = model.spanX >= 2
            val tall = model.spanY >= 2
            val artworkSize = if (tall) {
                (minOf(maxWidth.value, maxHeight.value) * 0.30f).coerceIn(28f, 96f).dp
            } else {
                dimens.tileIcon
            }
            val nameMaxLines =
                if (tall || maxHeight - artworkSize >= nameLine * 2 + statusSpace + 2.dp) 2 else 1

            // La disposition « grande » reste affichée même en pause : c'est son bouton
            // lecture/pause qui change d'icône selon l'état courant.
            val grande = model.spanX >= 2 && model.spanY >= 2 && model.isAvailable

            if (grande) {
                // Grande case : pochette en grand à gauche, commandes à droite.
                val cover = minOf(maxWidth, maxHeight)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaBadge(artwork = artwork, tint = iconTint, size = cover)
                    Spacer(Modifier.width(20.dp))
                    MediaControlBar(
                        entityId = model.entityId.orEmpty(),
                        isPlaying = model.isActive,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else when {
                tall -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                ) {
                    MediaBadge(artwork = artwork, tint = iconTint, size = artworkSize)
                    Spacer(Modifier.height(10.dp))
                    TileTexts(
                        name = model.name,
                        status = status,
                        nameColor = nameColor,
                        statusColor = statusColor,
                        nameMaxLines = 2,
                    )
                }

                wide -> Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaBadge(artwork = artwork, tint = iconTint, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        TileTexts(
                            name = model.name,
                            status = status,
                            nameColor = nameColor,
                            statusColor = statusColor,
                            nameMaxLines = 2,
                        )
                    }
                }

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    MediaBadge(artwork = artwork, tint = iconTint, size = artworkSize)
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
}

/** Barre de commandes de la grande case média : précédent, lecture/pause, suivant. */
@Composable
private fun MediaControlBar(
    entityId: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val controls = LocalMediaControls.current
    val haptics = com.homeglass.app.core.haptics.rememberGlassHaptics()

    // Retour immédiat : l'icône bascule dès le toucher, puis se recale sur l'état réel
    // quand Home Assistant confirme (la commande peut mettre un instant à revenir).
    var optimistic by remember(entityId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isPlaying) { optimistic = null }
    val playing = optimistic ?: isPlaying

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaControlButton(
            icon = Icons.Rounded.SkipPrevious,
            description = stringResource(R.string.control_previous),
            size = 48.dp,
        ) { controls?.previous(entityId) }

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptics.tap()
                        optimistic = !playing
                        controls?.playPause(entityId)
                    },
                )
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.control_pause else R.string.control_play,
                ),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }

        MediaControlButton(
            icon = Icons.Rounded.SkipNext,
            description = stringResource(R.string.control_next),
            size = 48.dp,
        ) { controls?.next(entityId) }
    }
}

@Composable
private fun MediaControlButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    onClick: () -> Unit,
) {
    val colors = GlassTheme.colors
    val haptics = com.homeglass.app.core.haptics.rememberGlassHaptics()

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.chipBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptics.tap()
                    onClick()
                },
            )
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = colors.screenTextPrimary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Pochette de piste, ou icône d'enceinte en repli, à la taille demandée. */
@Composable
private fun MediaBadge(artwork: ImageBitmap?, tint: Color, size: Dp) {
    if (artwork != null) {
        Image(
            bitmap = artwork,
            contentDescription = null, // décoratif : la description est portée par la tuile
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Icon(
            imageVector = TileVisuals.icon(TileCategory.Media),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
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
