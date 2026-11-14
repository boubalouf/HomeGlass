package com.homeglass.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.homeglass.app.R
import com.homeglass.app.domain.ha.EntityPresentation
import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.TileValueKind
import com.homeglass.app.domain.ha.mediaInfo
import com.homeglass.app.domain.ha.toPresentation
import com.homeglass.app.domain.layout.Layout
import com.homeglass.app.ui.tiles.TileUiModel

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
 * Applique l'état réel d'une entité à une tuile du contrat : texte, allumée/éteinte,
 * indisponible, niveau (luminosité, position).
 */
@Composable
fun TileUiModel.withEntityState(state: EntityState?): TileUiModel {
    if (state == null) return this
    val presentation = remember(state) { state.toPresentation() }
    return copy(
        stateText = tileStateText(presentation),
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
