package com.homeglass.app.ui.home

import com.homeglass.app.domain.dashboard.CustomDashboard
import com.homeglass.app.ui.tiles.TileUiModel

/**
 * Applique le tableau de bord personnel à l'accueil.
 *
 * Les cases enregistrées remplacent les sections d'appareils générées et forment une seule
 * grille libre (placements et tailles explicites) ; les scènes restent affichées dessous.
 * Les cases dont l'entité a disparu du layout courant sont ignorées : après une modification
 * côté Home Assistant, l'écran continue de s'afficher au lieu de se vider. Un tableau vide
 * rend la main au tableau généré automatiquement.
 */
fun HomeUiModel.withCustomLayout(
    dashboard: CustomDashboard,
    extraTiles: List<TileUiModel> = emptyList(),
): HomeUiModel {
    val cleaned = dashboard.normalized()
    if (cleaned.tiles.isEmpty()) return this

    val byEntity = (sections.filter { it.kind == HomeSectionKind.Tiles }.flatMap { it.tiles } + extraTiles)
        .mapNotNull { tile -> tile.entityId?.let { id -> id to tile } }
        .toMap()

    val tiles = cleaned.tiles.mapNotNull { placed ->
        val widget = placed.widget
        if (widget != null) {
            widget.toTileUiModel(placed)
        } else {
            byEntity[placed.entityId]?.withPlacement(placed)?.let { tile ->
                // Un nom personnalisé remplace celui publié par Home Assistant.
                placed.label?.let { tile.copy(name = it) } ?: tile
            }
        }
    }
    if (tiles.isEmpty()) return this

    val custom = HomeSectionUi(
        id = "custom_grid",
        title = "",
        kind = HomeSectionKind.Tiles,
        tiles = tiles,
        freeform = true,
    )
    val scenes = sections.filter { it.kind == HomeSectionKind.Scenes }
    return copy(sections = listOf(custom) + scenes)
}

/** Toutes les tuiles d'entités de l'accueil, dans leur ordre d'affichage. */
fun HomeUiModel.allEntityTiles(): List<TileUiModel> =
    sections.filter { it.kind == HomeSectionKind.Tiles }
        .flatMap { it.tiles }
        .filterNot { it.isWidget }
