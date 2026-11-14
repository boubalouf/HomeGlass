package com.homeglass.app.ui.home

import com.homeglass.app.domain.dashboard.CustomDashboard
import com.homeglass.app.ui.tiles.TileUiModel

/**
 * Applique les zones personnalisées à l'accueil : chaque zone devient une section titrée,
 * dans l'ordre et avec les appareils choisis.
 *
 * Les tuiles absentes de la sélection sont retirées (elles restent dans les pièces), et les
 * identifiants inconnus du layout courant sont ignorés : après une modification côté Home
 * Assistant, l'écran continue de s'afficher au lieu de se vider.
 *
 * Un tableau vide rend la main au tableau généré automatiquement.
 */
fun HomeUiModel.withCustomSections(
    dashboard: CustomDashboard,
    extraTiles: List<TileUiModel> = emptyList(),
): HomeUiModel {
    if (!dashboard.isConfigured) return this

    val tiles = (sections.filter { it.kind == HomeSectionKind.Tiles }.flatMap { it.tiles } + extraTiles)
        .mapNotNull { tile -> tile.entityId?.let { id -> id to tile } }
        .toMap()

    val custom = dashboard.normalized().sections.mapNotNull { section ->
        val chosen = section.entityIds.mapNotNull(tiles::get)
        if (chosen.isEmpty()) {
            null
        } else {
            HomeSectionUi(
                id = "custom_${section.id}",
                title = section.title,
                kind = HomeSectionKind.Tiles,
                tiles = chosen,
            )
        }
    }
    if (custom.isEmpty()) return this

    val scenes = sections.filter { it.kind == HomeSectionKind.Scenes }
    return copy(sections = custom + scenes)
}
