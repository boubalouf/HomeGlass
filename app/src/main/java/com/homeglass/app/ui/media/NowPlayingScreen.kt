package com.homeglass.app.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.MediaInfo
import com.homeglass.app.domain.ha.mediaInfo
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.tiles.LocalMediaArtworkBaseUrl
import com.homeglass.app.ui.tiles.rememberMediaArtwork
import kotlin.math.roundToInt

/** Fond du lecteur plein écran : presque noir, pour laisser la pochette dominer. */
private val PLAYER_BACKGROUND = Color(0xFF0B0B0C)

/** Rotation du disque : un tour en 18 s, la vitesse lisible d'un vinyle à l'écran. */
private const val DEGREES_PER_SECOND = 20f

/** Bits de `supported_features` publiés par Home Assistant pour un lecteur média. */
private const val FEATURE_PREVIOUS_TRACK = 16L
private const val FEATURE_NEXT_TRACK = 32L

/**
 * Lecteur plein écran d'une enceinte : la pochette tourne sur un vinyle, le titre occupe le
 * centre et les commandes sont alignées en bas de l'écran.
 *
 * En paysage (l'application y passe pendant la lecture), le disque passe à gauche et les
 * commandes à droite. Le disque s'arrête net à la pause (il repart de la même position à la
 * reprise) et ne tourne pas du tout quand l'appareil demande moins d'animations.
 */
@Composable
fun NowPlayingScreen(
    state: EntityState,
    title: String,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onVolume: (level: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = GlassTheme.colors.accent
    val reduceMotion = GlassTheme.motion.reduceMotion

    val media = state.mediaInfo()
    val artwork = rememberMediaArtwork(media?.artworkUrl(LocalMediaArtworkBaseUrl.current))
    val playing = state.state == "playing"
    val coverDescription = stringResource(R.string.player_cover)

    val features = state.numberAttribute("supported_features")?.toLong() ?: 0L
    val supportsPrevious = features == 0L || features and FEATURE_PREVIOUS_TRACK != 0L
    val supportsNext = features == 0L || features and FEATURE_NEXT_TRACK != 0L

    val volumePercent = state.numberAttribute("volume_level")
        ?.let { (it * 100.0).roundToInt().coerceIn(0, 100) }
    var volume by remember(state.entityId) { mutableFloatStateOf(volumePercent?.div(100f) ?: 0f) }
    var draggingVolume by remember(state.entityId) { mutableStateOf(false) }

    LaunchedEffect(volumePercent, draggingVolume) {
        if (!draggingVolume && volumePercent != null) volume = volumePercent / 100f
    }

    val subtitle = mediaSubtitle(media)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PLAYER_BACKGROUND),
    ) {
        val landscape = maxWidth > maxHeight
        val discSize = if (landscape) {
            minOf(maxWidth * 0.40f, maxHeight * 0.66f)
        } else {
            minOf(maxWidth * 0.62f, maxHeight * 0.50f)
        }

        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(72.dp)
                    .alpha(0.6f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xCC0B0B0C),
                        0.55f to Color(0xE60B0B0C),
                        1f to PLAYER_BACKGROUND,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = if (landscape) 40.dp else 36.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerHeader(
                title = title,
                status = playbackStateLabel(state.state),
                onDismiss = onDismiss,
            )

            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VinylDisc(
                        artwork = artwork,
                        spinning = playing && !reduceMotion,
                        size = discSize,
                        coverDescription = coverDescription,
                    )
                    Spacer(Modifier.width(48.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        TrackText(
                            media = media,
                            fallbackTitle = title,
                            subtitle = subtitle,
                            centered = false,
                        )
                        Spacer(Modifier.height(28.dp))
                        TransportRow(
                            playing = playing,
                            supportsPrevious = supportsPrevious,
                            supportsNext = supportsNext,
                            accent = accent,
                            alignStart = true,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onPrevious = onPrevious,
                        )
                        if (volumePercent != null) {
                            Spacer(Modifier.height(24.dp))
                            VolumeControl(
                                volume = volume,
                                onVolumeChange = { value ->
                                    draggingVolume = true
                                    volume = value
                                },
                                onVolumeChangeFinished = {
                                    draggingVolume = false
                                    onVolume(volume)
                                },
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
                VinylDisc(
                    artwork = artwork,
                    spinning = playing && !reduceMotion,
                    size = discSize,
                    coverDescription = coverDescription,
                )
                Spacer(Modifier.weight(1f))

                TrackText(
                    media = media,
                    fallbackTitle = title,
                    subtitle = subtitle,
                    centered = true,
                )

                Spacer(Modifier.height(24.dp))
                TransportRow(
                    playing = playing,
                    supportsPrevious = supportsPrevious,
                    supportsNext = supportsNext,
                    accent = accent,
                    alignStart = false,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                )

                if (volumePercent != null) {
                    Spacer(Modifier.height(22.dp))
                    VolumeControl(
                        volume = volume,
                        onVolumeChange = { value ->
                            draggingVolume = true
                            volume = value
                        },
                        onVolumeChangeFinished = {
                            draggingVolume = false
                            onVolume(volume)
                        },
                    )
                }
            }
        }
    }
}

/** Nom de l'enceinte et état de lecture, avec le bouton de fermeture. */
@Composable
private fun PlayerHeader(title: String, status: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = GlassTheme.typography.tileName,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = status,
                style = GlassTheme.typography.caption,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
        Spacer(Modifier.width(12.dp))
        CloseButton(onDismiss)
    }
}

/** Titre de la piste, artiste et album. */
@Composable
private fun TrackText(
    media: MediaInfo?,
    fallbackTitle: String,
    subtitle: String?,
    centered: Boolean,
) {
    val typography = GlassTheme.typography
    val alignment = if (centered) TextAlign.Center else TextAlign.Start
    val horizontal = if (centered) Alignment.CenterHorizontally else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontal,
    ) {
        Text(
            text = media?.title ?: fallbackTitle,
            style = typography.largeTitle,
            color = Color.White,
            textAlign = alignment,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = typography.tileStatus,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = alignment,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Précédent / lecture-pause / suivant. */
@Composable
private fun TransportRow(
    playing: Boolean,
    supportsPrevious: Boolean,
    supportsNext: Boolean,
    accent: Color,
    alignStart: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            28.dp,
            if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportButton(
            icon = Icons.Rounded.SkipPrevious,
            description = stringResource(R.string.control_previous),
            onClick = onPrevious,
            enabled = supportsPrevious,
            background = Color.White.copy(alpha = 0.14f),
        )
        TransportButton(
            icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            description = stringResource(
                if (playing) R.string.control_pause else R.string.control_play,
            ),
            onClick = onPlayPause,
            size = 76.dp,
            iconSize = 34.dp,
            background = accent,
        )
        TransportButton(
            icon = Icons.Rounded.SkipNext,
            description = stringResource(R.string.control_next),
            onClick = onNext,
            enabled = supportsNext,
            background = Color.White.copy(alpha = 0.14f),
        )
    }
}

/** Curseur de volume, aligné sur la largeur des commandes. */
@Composable
private fun VolumeControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
) {
    val typography = GlassTheme.typography
    val accent = GlassTheme.colors.accent

    Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.control_volume),
                style = typography.caption,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = stringResource(R.string.value_percent, (volume * 100).roundToInt()),
                style = typography.caption,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            onValueChangeFinished = onVolumeChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
        )
    }
}

/**
 * Disque de vinyle : sillons, reflet et pochette au centre (étiquette), le tout tournant
 * pendant la lecture.
 */
@Composable
private fun VinylDisc(
    artwork: ImageBitmap?,
    spinning: Boolean,
    size: Dp,
    coverDescription: String,
) {
    var degrees by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(spinning) {
        if (!spinning) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            degrees = (degrees + seconds * DEGREES_PER_SECOND) % 360f
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .shadow(elevation = 30.dp, shape = CircleShape, clip = false)
            .graphicsLayer { rotationZ = degrees }
            .semantics { contentDescription = coverDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f

            drawCircle(color = Color(0xFF101012), radius = radius)

            // Sillons : fines couronnes concentriques, plus serrées vers le bord.
            val grooves = 30
            repeat(grooves) { index ->
                val fraction = 0.97f - index * (0.50f / grooves)
                drawCircle(
                    color = Color.White.copy(alpha = 0.04f),
                    radius = radius * fraction,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // Reflet diagonal, comme la lumière sur un disque posé à plat.
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.05f),
                    ),
                    start = Offset(radius * 0.1f, radius * 0.1f),
                    end = Offset(radius * 1.7f, radius * 1.7f),
                ),
                radius = radius,
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = radius - 1.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        Box(
            modifier = Modifier
                .size(size * 0.60f)
                .clip(CircleShape)
                .background(Color(0xFF1C1C1E))
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.14f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp),
                )
            }
        }

        // Axe central du tourne-disque.
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(PLAYER_BACKGROUND),
        )
    }
}

/** Bouton rond de commande, calibré pour un fond sombre. */
@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    background: Color,
    enabled: Boolean = true,
    size: Dp = 56.dp,
    iconSize: Dp = 26.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Bouton de fermeture, posé en haut à droite. */
@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    val description = stringResource(R.string.player_close)

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
    }
}

/** « Artiste · Album », ou la seule information disponible. */
@Composable
private fun mediaSubtitle(media: MediaInfo?): String? {
    val artist = media?.artist
    val album = media?.album
    return when {
        artist != null && album != null -> stringResource(R.string.media_artist_album, artist, album)
        else -> artist ?: album
    }
}

/** État du lecteur, traduit ; le code brut de Home Assistant n'est jamais montré. */
@Composable
private fun playbackStateLabel(state: String): String = stringResource(
    when (state) {
        "playing" -> R.string.state_playing
        "paused" -> R.string.state_paused
        "idle" -> R.string.state_idle
        "off" -> R.string.entity_state_off
        "unavailable" -> R.string.state_unavailable
        else -> R.string.entity_state_on
    },
)
