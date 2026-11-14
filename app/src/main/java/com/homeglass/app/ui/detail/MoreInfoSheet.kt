package com.homeglass.app.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.MediaInfo
import com.homeglass.app.domain.ha.mediaInfo
import com.homeglass.app.ui.components.glassHazePanel
import com.homeglass.app.ui.home.GlassPillButton
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.tiles.LocalMediaArtworkBaseUrl
import com.homeglass.app.ui.tiles.rememberMediaArtwork
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Domaines que le panneau sait piloter directement (allumage / extinction). */
private val CONTROLLED_DOMAINS = setOf("light", "switch", "fan", "input_boolean")

/** Modes de couleur qui autorisent le choix d'une teinte RGB. */
private val COLOR_MODES = setOf("hs", "rgb", "rgbw", "rgbww", "xy")

private val WARM_COLOR = Color(0xFFFFB05A)
private val COLD_COLOR = Color(0xFFBFD9FF)

/** Bits de `supported_features` publiés par Home Assistant pour un lecteur média. */
private const val FEATURE_PREVIOUS_TRACK = 16L
private const val FEATURE_NEXT_TRACK = 32L

/**
 * Panneau de contrôle d'un appareil.
 *
 * Pour une lumière, il réunit l'allumage, la luminosité, la température de couleur et les
 * teintes prédéfinies ; pour une prise ou un ventilateur, l'allumage suffit. Les commandes
 * sont envoyées au fil du geste (luminosité) puis à la fin du geste pour la température,
 * pour ne pas saturer Home Assistant.
 */
@Composable
fun MoreInfoSheet(
    state: EntityState,
    title: String,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onBrightness: (percent: Int, finished: Boolean) -> Unit,
    onColor: (red: Int, green: Int, blue: Int) -> Unit,
    onColorTemperature: (kelvin: Int) -> Unit,
    onMediaPlayPause: () -> Unit,
    onMediaNext: () -> Unit,
    onMediaPrevious: () -> Unit,
    onVolume: (level: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val typography = GlassTheme.typography
    val dimens = GlassTheme.dimens

    val isLight = state.domain == "light"
    val controllable = state.domain in CONTROLLED_DOMAINS && state.isAvailable
    val active = state.state == "on" || state.state == "open"

    val modes = state.stringListAttribute("supported_color_modes")
    val dimmable = isLight &&
        (state.attributes.containsKey("brightness") || modes.any { it !in setOf("onoff", "unknown") })
    val supportsColor = isLight &&
        (state.attributes.containsKey("rgb_color") || modes.any { it in COLOR_MODES })
    val supportsTemperature = isLight &&
        (
            state.attributes.containsKey("color_temp_kelvin") ||
                modes.any { it == "color_temp" || it == "color_temp_kelvin" }
            )

    val brightnessPercent = state.numberAttribute("brightness")
        ?.let { (it * 100.0 / 255.0).roundToInt().coerceIn(0, 100) }
        ?: 100

    var level by remember(state.entityId) { mutableFloatStateOf(brightnessPercent / 100f) }
    var draggingLevel by remember(state.entityId) { mutableStateOf(false) }

    LaunchedEffect(brightnessPercent, draggingLevel) {
        if (!draggingLevel) level = brightnessPercent / 100f
    }

    val kelvin = state.numberAttribute("color_temp_kelvin")?.toInt() ?: 3500
    val minKelvin = state.numberAttribute("min_color_temp_kelvin")?.toInt() ?: 2000
    val maxKelvin = state.numberAttribute("max_color_temp_kelvin")?.toInt() ?: 6500
    var temperature by remember(state.entityId) { mutableFloatStateOf(kelvin.toFloat()) }

    LaunchedEffect(kelvin) { temperature = kelvin.toFloat() }

    val currentColor = state.rgbColor()

    val isMedia = state.domain == "media_player"
    val playing = state.state == "playing"

    // Capacités déclarées par l'appareil : inutile de proposer une piste suivante qu'il ne
    // sait pas jouer (Home Assistant refuse alors le service). 0 = inconnu, on laisse actif.
    val features = state.numberAttribute("supported_features")?.toLong() ?: 0L
    val supportsPrevious = features == 0L || features and FEATURE_PREVIOUS_TRACK != 0L
    val supportsNext = features == 0L || features and FEATURE_NEXT_TRACK != 0L

    val volumePercent = state.numberAttribute("volume_level")
        ?.let { (it * 100.0).roundToInt().coerceIn(0, 100) }
    var volume by remember(state.entityId) {
        mutableFloatStateOf(volumePercent?.div(100f) ?: 0f)
    }
    var draggingVolume by remember(state.entityId) { mutableStateOf(false) }

    LaunchedEffect(volumePercent, draggingVolume) {
        if (!draggingVolume && volumePercent != null) volume = volumePercent / 100f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = dimens.navBarHeight + dimens.navBarBottomMargin)
                .glassHazePanel(shape = shapes.sheet, tint = colors.sheetBackground)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 44.dp)
                    .height(5.dp)
                    .clip(shapes.pill)
                    .background(colors.sheetHandle),
            )
            Spacer(Modifier.height(16.dp))

            Text(title, style = typography.sectionTitle, color = colors.screenTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    controllable -> stringResource(
                        if (active) R.string.entity_state_on else R.string.entity_state_off,
                    )

                    // L'état brut de Home Assistant ("playing") n'est jamais montré tel quel.
                    isMedia -> stringResource(
                        when (state.state) {
                            "playing" -> R.string.state_playing
                            "paused" -> R.string.state_paused
                            "idle" -> R.string.state_idle
                            "off" -> R.string.entity_state_off
                            else -> R.string.entity_state_on
                        },
                    )

                    else -> state.state
                },
                style = typography.caption,
                color = colors.screenTextSecondary,
            )

            if (controllable) {
                Spacer(Modifier.height(16.dp))
                GlassPillButton(
                    text = stringResource(
                        if (active) R.string.control_turn_off else R.string.control_turn_on,
                    ),
                    onClick = onToggle,
                )
            }

            if (isMedia) {
                Spacer(Modifier.height(20.dp))
                MediaNowPlaying(media = state.mediaInfo(), fallbackTitle = title)

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 20.dp,
                        alignment = Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MediaButton(
                        icon = Icons.Rounded.SkipPrevious,
                        description = stringResource(R.string.control_previous),
                        onClick = onMediaPrevious,
                        enabled = supportsPrevious,
                    )
                    MediaButton(
                        icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        description = stringResource(
                            if (playing) R.string.control_pause else R.string.control_play,
                        ),
                        onClick = onMediaPlayPause,
                        primary = true,
                    )
                    MediaButton(
                        icon = Icons.Rounded.SkipNext,
                        description = stringResource(R.string.control_next),
                        onClick = onMediaNext,
                        enabled = supportsNext,
                    )
                }

                if (volumePercent != null) {
                    Spacer(Modifier.height(18.dp))
                    ControlHeader(
                        label = stringResource(R.string.control_volume),
                        value = stringResource(R.string.value_percent, (volume * 100).roundToInt()),
                    )
                    Slider(
                        value = volume,
                        onValueChange = { value ->
                            draggingVolume = true
                            volume = value
                        },
                        onValueChangeFinished = {
                            draggingVolume = false
                            onVolume(volume)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = colors.screenTextPrimary,
                            activeTrackColor = colors.accent,
                            inactiveTrackColor = colors.chipBackground,
                        ),
                    )
                }
            }

            if (dimmable) {
                Spacer(Modifier.height(20.dp))
                ControlHeader(
                    label = stringResource(R.string.control_brightness),
                    value = stringResource(R.string.value_percent, (level * 100).roundToInt()),
                )
                Slider(
                    value = level,
                    onValueChange = { value ->
                        draggingLevel = true
                        level = value
                        onBrightness((value * 100).roundToInt().coerceIn(0, 100), false)
                    },
                    onValueChangeFinished = {
                        draggingLevel = false
                        onBrightness((level * 100).roundToInt().coerceIn(0, 100), true)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = colors.screenTextPrimary,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.chipBackground,
                    ),
                )
            }

            if (supportsTemperature) {
                Spacer(Modifier.height(12.dp))
                ControlHeader(
                    label = stringResource(R.string.control_color_temperature),
                    value = stringResource(R.string.value_kelvin, "${temperature.roundToInt()}"),
                )
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(shapes.pill)
                            .background(Brush.horizontalGradient(listOf(WARM_COLOR, COLD_COLOR))),
                    )
                    Slider(
                        value = temperature.coerceIn(minKelvin.toFloat(), maxKelvin.toFloat()),
                        valueRange = minKelvin.toFloat()..maxKelvin.toFloat(),
                        onValueChange = { value -> temperature = value },
                        onValueChangeFinished = { onColorTemperature(temperature.roundToInt()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                        ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.control_warm),
                        style = typography.caption,
                        color = colors.screenTextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.control_cold),
                        style = typography.caption,
                        color = colors.screenTextSecondary,
                    )
                }
            }

            if (supportsColor) {
                Spacer(Modifier.height(20.dp))
                ControlHeader(label = stringResource(R.string.control_color), value = null)
                Spacer(Modifier.height(14.dp))
                LightColorWheel(
                    selected = currentColor,
                    onColor = onColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = state.entityId,
                style = typography.caption,
                color = colors.screenTextSecondary,
            )
        }
    }
}

/** Titre de commande avec, à droite, la valeur courante (facultative). */
@Composable
private fun ControlHeader(label: String, value: String?) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = typography.tileName, color = colors.screenTextPrimary)
        if (value != null) {
            Text(text = value, style = typography.caption, color = colors.screenTextSecondary)
        }
    }
}

/** Pochette, titre et interprète de la piste en cours d'une enceinte. */
@Composable
private fun MediaNowPlaying(media: MediaInfo?, fallbackTitle: String) {
    val colors = GlassTheme.colors
    val typography = GlassTheme.typography

    val artwork = rememberMediaArtwork(media?.artworkUrl(LocalMediaArtworkBaseUrl.current))
    val subtitle = when {
        media?.artist != null && media?.album != null ->
            stringResource(R.string.media_artist_album, media?.artist.orEmpty(), media?.album.orEmpty())

        else -> media?.artist ?: media?.album
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.chipBackground),
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
                    tint = colors.screenTextSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media?.title ?: fallbackTitle,
                style = typography.tileName,
                color = colors.screenTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = typography.caption,
                    color = colors.screenTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Bouton rond de commande de lecture ; la couleur d'accent marque l'action principale. */
@Composable
private fun MediaButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = GlassTheme.colors

    Box(
        modifier = Modifier
            .size(if (primary) 52.dp else 44.dp)
            .clip(CircleShape)
            .background(if (primary) colors.accent else colors.chipBackground)
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
            tint = if (primary) Color.White else colors.screenTextPrimary,
            modifier = Modifier.size(if (primary) 26.dp else 22.dp),
        )
    }
}

/** Couleur courante d'une lumière, quand Home Assistant la rapporte en RGB. */
private fun EntityState.rgbColor(): Color? {
    val array = attributes["rgb_color"] as? JsonArray ?: return null
    val components = array.mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }
    if (components.size < 3) return null
    return Color(components[0], components[1], components[2])
}
