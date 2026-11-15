package com.homeglass.app.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.homeglass.app.R
import com.homeglass.app.domain.dashboard.WidgetKind
import com.homeglass.app.ui.theme.GlassTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)

/**
 * Tuile horloge : l'heure avance sans entité Home Assistant.
 *
 * Trois thèmes :
 * - `classic` : grande heure posée sur la tuile standard, date complète en légende ;
 * - `minimal` : heure centrée, date courte discrète ;
 * - `bold` : heure sur un dégradé d'accent, texte blanc.
 */
@Composable
fun ClockTile(
    model: TileUiModel,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography

    val now = rememberClockTime()
    val time = remember(now) { now.format(timeFormatter) }
    val theme = model.widgetTheme ?: WidgetKind.CLOCK_CLASSIC

    val onScreenText = {
        if (theme == WidgetKind.CLOCK_BOLD) Color.White
        else if (colors.isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    }
    val secondaryText = {
        if (theme == WidgetKind.CLOCK_BOLD) Color.White.copy(alpha = 0.82f)
        else if (colors.isDark) Color(0xFFAEAEB2) else Color(0xFF6C6C70)
    }

    val background: @Composable () -> Unit = {
        when (theme) {
            WidgetKind.CLOCK_BOLD -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(colors.accent, colors.categoryMedia))),
            )

            WidgetKind.CLOCK_CLASSIC -> Box(
                Modifier
                    .fillMaxSize()
                    .background(if (colors.isDark) colors.tileInactiveBackground else colors.tileActiveBackground),
            )

            else -> Box(Modifier.fillMaxSize())
        }
    }

    val description = stringResource(R.string.a11y_clock_tile, time)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shapes.tile)
            .semantics { contentDescription = description },
    ) {
        background()

        // La taille de l'heure suit la plus petite dimension de la case : une case large ou
        // haute affiche une heure plus grande, sans jamais déborder.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val minSide = minOf(maxWidth.value, maxHeight.value)
            val timeSize = (minSide * 0.30f).coerceIn(20f, 64f).sp
            val dateSize = (minSide * 0.11f).coerceIn(10f, 15f).sp
            val horizontal =
                if (theme == WidgetKind.CLOCK_MINIMAL) Alignment.CenterHorizontally else Alignment.Start

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimens.tilePadding),
                horizontalAlignment = horizontal,
            ) {
                Text(
                    text = time,
                    style = clockStyle(theme, typography.largeTitle, timeSize),
                    color = onScreenText(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when (theme) {
                        WidgetKind.CLOCK_MINIMAL -> now.format(shortDateFormatter).replaceFirstChar(Char::uppercaseChar)
                        else -> now.format(fullDateFormatter).replaceFirstChar(Char::uppercaseChar)
                    },
                    style = typography.caption.copy(fontSize = dateSize),
                    color = secondaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun clockStyle(theme: String, base: TextStyle, size: TextUnit): TextStyle = when (theme) {
    WidgetKind.CLOCK_BOLD -> base.copy(fontWeight = FontWeight.Bold, fontSize = size)
    WidgetKind.CLOCK_MINIMAL -> base.copy(fontWeight = FontWeight.Light, fontSize = size * 0.94f)
    else -> base.copy(fontWeight = FontWeight.Bold, fontSize = size * 0.9f)
}

/** Heure courante, rafraîchie au changement de minute (pas de recomposition par seconde). */
@Composable
internal fun rememberClockTime(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000L * (60 - now.second))
        }
    }
    return now
}
