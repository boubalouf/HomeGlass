package com.homeglass.app.ui.home

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.homeglass.app.R
import com.homeglass.app.domain.ha.EntityPresentation
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.TileValueKind
import com.homeglass.app.domain.ha.mediaInfo
import com.homeglass.app.domain.ha.toPresentation
import com.homeglass.app.domain.layout.Layout
import com.homeglass.app.ui.tiles.TileUiModel
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date

/** Texte d'état localisé d'une tuile, à partir de la lecture pure de l'entité. */
@Composable
fun tileStateText(presentation: EntityPresentation): String {
    if (!presentation.available) return stringResource(R.string.state_unavailable)

    return when (presentation.kind) {
        TileValueKind.None -> ""
        TileValueKind.On -> stringResource(R.string.entity_state_on)
        TileValueKind.Off -> stringResource(R.string.entity_state_off)
        TileValueKind.Percent -> stringResource(R.string.value_percent, presentation.percent ?: 0)
        TileValueKind.Measurement -> {
            val value = presentation.text.orEmpty()
            val unit = presentation.unit
            if (unit.isNullOrBlank()) value else stringResource(R.string.value_measurement, value, unit)
        }

        TileValueKind.Open -> presentation.percent?.let { percent ->
            stringResource(R.string.state_open_percent, percent)
        } ?: stringResource(R.string.state_open)

        TileValueKind.Closed -> stringResource(R.string.state_closed)
        TileValueKind.Locked -> stringResource(R.string.state_locked)
        TileValueKind.Unlocked -> stringResource(R.string.state_unlocked)
        TileValueKind.Playing -> presentation.detail?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.state_playing)

        TileValueKind.Paused -> stringResource(R.string.state_paused)
        TileValueKind.Text -> presentation.text.orEmpty()
    }
}

/**
 * Texte d'une tuile hors ligne : « Hors ligne depuis 14:32 », avec la date si l'entité a
 * décroché un autre jour. Home Assistant date l'état `unavailable` dans `last_changed`.
 */
@Composable
private fun unavailableText(state: EntityState): String {
    if (state.state == EntityState.UNKNOWN) return stringResource(R.string.state_unknown)

    val instant = remember(state.lastChanged, state.lastUpdated) {
        // Home Assistant écrit un décalage (`+00:00`) ; `Instant.parse` ne l'accepte pas
        // toujours selon la version d'Android, `OffsetDateTime` le gère.
        listOfNotNull(state.lastChanged, state.lastUpdated).firstNotNullOfOrNull { raw ->
            runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
                ?: runCatching { Instant.parse(raw) }.getOrNull()
        }
    } ?: return stringResource(R.string.state_unavailable)

    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val localDate = remember(instant, zone) { instant.atZone(zone).toLocalDate() }
    val today = remember(localDate) { localDate == LocalDate.now(zone) }
    // Une tuile 1 × 1 ne peut afficher qu'une ligne courte : l'heure si l'entité a décroché
    // aujourd'hui, sinon le jour (avec l'année seulement si elle diffère).
    val label = if (today) {
        remember(instant, context) {
            DateFormat.getTimeFormat(context).format(Date.from(instant))
        }
    } else {
        remember(instant, context, localDate) {
            val pattern = if (localDate.year == LocalDate.now(zone).year) "dd/MM" else "dd/MM/yy"
            DateFormat.format(pattern, Date.from(instant)).toString()
        }
    }

    return stringResource(R.string.state_offline_since, label)
}

/**
 * Applique l'état réel d'une entité à une tuile du contrat : texte, allumée/éteinte,
 * indisponible, niveau (luminosité, position).
 */
@Composable
fun TileUiModel.withEntityState(state: EntityState?): TileUiModel {
    if (state == null) return this
    val presentation = remember(state) { state.toPresentation() }
    return copy(
        stateText = if (state.isAvailable) tileStateText(presentation) else unavailableText(state),
        isActive = presentation.active,
        isAvailable = presentation.available,
        level = presentation.level ?: level,
        media = state.mediaInfo() ?: media,
    )
}

@Composable
fun HomeSectionUi.withStates(states: Map<String, EntityState>): HomeSectionUi = when (kind) {
    HomeSectionKind.Scenes -> this
    HomeSectionKind.Tiles -> copy(tiles = tiles.map { it.withEntityState(it.entityId?.let(states::get)) })
}

/** Écran Maison complet : sections du layout + états temps réel. */
@Composable
fun Layout.toHomeUiWithStates(states: Map<String, EntityState>): HomeUiModel =
    toHomeUi().copy(sections = toHomeUi().sections.map { it.withStates(states) })

/** Pièces issues du layout, avec leurs tuiles à jour. */
@Composable
fun Layout.toRoomsUi(states: Map<String, EntityState>): List<RoomUi> = rooms.map { room ->
    RoomUi(
        id = room.id,
        name = room.name.ifBlank { room.id },
        sections = room.sections
            .map { section -> section.toUi().withStates(states) }
            .filter { it.tiles.isNotEmpty() || it.scenes.isNotEmpty() },
    )
}
