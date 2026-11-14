package com.homeglass.app.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.ui.theme.GlassTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Teinte/saturation d'un point du disque, et la couleur RGB correspondante. */
internal data class WheelPick(
    val hue: Float,
    val saturation: Float,
    val red: Int,
    val green: Int,
    val blue: Int,
)

/**
 * Roue de couleurs : le disque entier, du blanc au centre vers toutes les teintes saturées
 * sur le bord. L'angle donne la teinte, la distance au centre la saturation.
 *
 * L'utilisateur touche ou fait glisser son doigt ; la teinte n'est envoyée qu'au relâchement
 * (et au tap), pour ne pas inonder Home Assistant pendant le geste.
 */
@Composable
fun LightColorWheel(
    selected: Color?,
    onColor: (red: Int, green: Int, blue: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val description = stringResource(R.string.control_color_wheel)
    var pick by remember { mutableStateOf<WheelPick?>(null) }

    Box(
        // Taille bornée : le disque reste un cercle compact au centre de la fiche, même
        // quand le parent propose toute la largeur (tablette).
        modifier = modifier
            .widthIn(max = 220.dp)
            .aspectRatio(1f)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val picked = wheelPick(
                            offsetX = position.x - size.width / 2f,
                            offsetY = position.y - size.height / 2f,
                            radius = size.width / 2f,
                        )
                        pick = picked
                        onColor(picked.red, picked.green, picked.blue)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { position ->
                            pick = wheelPick(
                                offsetX = position.x - size.width / 2f,
                                offsetY = position.y - size.height / 2f,
                                radius = size.width / 2f,
                            )
                        },
                        onDragEnd = { pick?.let { onColor(it.red, it.green, it.blue) } },
                    ) { change, _ ->
                        change.consume()
                        pick = wheelPick(
                            offsetX = change.position.x - size.width / 2f,
                            offsetY = change.position.y - size.height / 2f,
                            radius = size.width / 2f,
                        )
                    }
                },
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                brush = Brush.sweepGradient(hueGradient(), center = center),
                radius = radius,
                center = center,
            )
            // Voile blanc vers le centre : la distance au bord devient la saturation.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
            drawCircle(
                color = colors.chipBackground,
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            val shown = pick ?: selected?.let { color ->
                val (dx, dy) = wheelPosition(
                    red = (color.red * 255f).roundToInt(),
                    green = (color.green * 255f).roundToInt(),
                    blue = (color.blue * 255f).roundToInt(),
                )
                val saturation = hypot(dx, dy).coerceIn(0f, 1f)
                val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f)
                WheelPick(hue, saturation, 0, 0, 0)
            }

            if (shown != null) {
                val angle = Math.toRadians(shown.hue.toDouble())
                val reach = (radius - 12.dp.toPx()) * shown.saturation.coerceIn(0f, 1f)
                val marker = Offset(
                    x = center.x + (cos(angle) * reach).toFloat(),
                    y = center.y + (sin(angle) * reach).toFloat(),
                )
                drawCircle(color = Color.White, radius = 9.dp.toPx(), center = marker)
                drawCircle(
                    color = Color.Black.copy(alpha = 0.28f),
                    radius = 9.dp.toPx(),
                    center = marker,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
    }
}

/** Dégradé du disque : 36 pas de 10°, assez fin pour paraître continu. */
private fun hueGradient(): List<Color> = List(37) { step ->
    val (red, green, blue) = hsvToRgb(hue = step * 10f, saturation = 1f, value = 1f)
    Color(red, green, blue)
}

/** Teinte, saturation et couleur RGB d'un point exprimé par rapport au centre du disque. */
internal fun wheelPick(offsetX: Float, offsetY: Float, radius: Float): WheelPick {
    val safeRadius = radius.coerceAtLeast(1f)
    val dx = offsetX / safeRadius
    val dy = offsetY / safeRadius
    val saturation = hypot(dx, dy).coerceIn(0f, 1f)
    val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat() + 360f) % 360f)
    val (red, green, blue) = hsvToRgb(hue, saturation, 1f)
    return WheelPick(hue, saturation, red, green, blue)
}

/** Conversion HSV → RGB (composantes 0..255), sans dépendance Android donc testable en JVM. */
internal fun hsvToRgb(hue: Float, saturation: Float, value: Float): Triple<Int, Int, Int> {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)

    val chroma = v * s
    val second = chroma * (1f - abs((normalizedHue / 60f) % 2f - 1f))
    val base = v - chroma

    val (r, g, b) = when {
        normalizedHue < 60f -> Triple(chroma, second, 0f)
        normalizedHue < 120f -> Triple(second, chroma, 0f)
        normalizedHue < 180f -> Triple(0f, chroma, second)
        normalizedHue < 240f -> Triple(0f, second, chroma)
        normalizedHue < 300f -> Triple(second, 0f, chroma)
        else -> Triple(chroma, 0f, second)
    }

    fun channel(component: Float): Int = ((component + base) * 255f).roundToInt().coerceIn(0, 255)
    return Triple(channel(r), channel(g), channel(b))
}

/**
 * Position normalisée (rayon 1 = bord) d'une couleur RGB sur le disque, pour placer le repère
 * sur la couleur réellement rapportée par Home Assistant.
 */
internal fun wheelPosition(red: Int, green: Int, blue: Int): Pair<Float, Float> {
    val r = red.coerceIn(0, 255) / 255f
    val g = green.coerceIn(0, 255) / 255f
    val b = blue.coerceIn(0, 255) / 255f

    val maximum = maxOf(r, g, b)
    val minimum = minOf(r, g, b)
    val delta = maximum - minimum
    val saturation = if (maximum <= 0f) 0f else delta / maximum

    val hue = when {
        delta < 0.0001f -> 0f
        maximum == r -> 60f * (((g - b) / delta) % 6f)
        maximum == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val angle = Math.toRadians(normalizedHue.toDouble())

    return Pair(
        (saturation * cos(angle)).toFloat(),
        (saturation * sin(angle)).toFloat(),
    )
}
