package com.homeglass.app.ui.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homeglass.app.R
import com.homeglass.app.domain.dashboard.WidgetKind
import com.homeglass.app.ui.theme.GlassTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)
private val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val weekdayLetters = listOf("L", "M", "M", "J", "V", "S", "D")

/**
 * Tuile calendrier : affiche le jour courant.
 *
 * Trois thèmes :
 * - `minimal` : grand numéro du jour, mois en légende ;
 * - `week` : bande de la semaine en cours, aujourd'hui entouré ;
 * - `focus` : jour de la semaine mis en avant sur un fond d'accent.
 */
@Composable
fun CalendarTile(
    model: TileUiModel,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography

    val today = LocalDate.now()
    val theme = model.widgetTheme ?: WidgetKind.CALENDAR_MINIMAL

    val onAccent = theme == WidgetKind.CALENDAR_FOCUS
    val primary = if (onAccent) Color.White else if (colors.isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val secondary = if (onAccent) Color.White.copy(alpha = 0.85f)
    else if (colors.isDark) Color(0xFFAEAEB2) else Color(0xFF6C6C70)

    val description = stringResource(
        R.string.a11y_calendar_tile,
        today.format(fullDateFormatter).replaceFirstChar(Char::uppercaseChar),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(GlassTheme.shapes.tile)
            .background(
                if (onAccent) {
                    Brush.linearGradient(listOf(colors.accent, colors.categoryMedia))
                } else {
                    Brush.linearGradient(
                        listOf(
                            if (colors.isDark) colors.tileInactiveBackground else colors.tileActiveBackground,
                            if (colors.isDark) colors.tileInactiveBackground else colors.tileActiveBackground,
                        ),
                    )
                },
            )
            .semantics { contentDescription = description },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Le numéro du jour suit la plus petite dimension de la case.
            val minSide = minOf(maxWidth.value, maxHeight.value)
            val daySize = (minSide * 0.34f).coerceIn(18f, 60f).sp

            when (theme) {
                WidgetKind.CALENDAR_WEEK -> WeekLayout(
                today = today,
                primary = primary,
                secondary = secondary,
                accent = colors.accent,
                modifier = Modifier.padding(dimens.tilePadding),
            )

            WidgetKind.CALENDAR_FOCUS -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimens.tilePadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = today.format(DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH))
                        .replaceFirstChar(Char::uppercaseChar),
                    style = typography.caption,
                    color = secondary,
                    maxLines = 1,
                )
                Text(
                    text = today.dayOfMonth.toString(),
                    style = typography.largeTitle.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = daySize * 0.85f,
                        lineHeight = daySize * 0.9f,
                    ),
                    color = primary,
                    maxLines = 1,
                )
                Text(
                    text = today.format(monthFormatter).replaceFirstChar(Char::uppercaseChar),
                    style = typography.caption,
                    color = secondary,
                    maxLines = 1,
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimens.tilePadding),
            ) {
                Text(
                    text = today.format(fullDateFormatter).replaceFirstChar(Char::uppercaseChar),
                    style = typography.caption,
                    color = secondary,
                    maxLines = 2,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = today.dayOfMonth.toString(),
                        style = typography.largeTitle.copy(fontWeight = FontWeight.Bold, fontSize = daySize),
                        color = primary,
                        maxLines = 1,
                    )
                    Text(
                        text = "  " + today.format(monthFormatter).replaceFirstChar(Char::uppercaseChar),
                        style = typography.tileStatus,
                        color = secondary,
                        maxLines = 1,
                    )
                }
            }
        }
        }
    }
}

/** Bande de la semaine : sept jours, le jour courant entouré d'un accent. */
@Composable
private fun WeekLayout(
    today: LocalDate,
    primary: Color,
    secondary: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val typography = GlassTheme.typography
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = today.format(monthFormatter).replaceFirstChar(Char::uppercaseChar) + " " + today.year,
            style = typography.caption,
            color = secondary,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            (0..6).forEach { offset ->
                val date = monday.plusDays(offset.toLong())
                val isToday = date == today
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = weekdayLetters[offset],
                        style = typography.caption,
                        color = if (isToday) accent else secondary,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isToday) accent else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = typography.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                            color = if (isToday) Color.White else primary,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
