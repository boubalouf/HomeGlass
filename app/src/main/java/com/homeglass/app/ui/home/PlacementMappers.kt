package com.homeglass.app.ui.home

import com.homeglass.app.domain.dashboard.PlacedTile
import com.homeglass.app.domain.dashboard.WidgetItem
import com.homeglass.app.domain.dashboard.WidgetKind
import com.homeglass.app.ui.tiles.TileUiModel

/** Applique la géométrie enregistrée d'une case à son modèle d'affichage. */
fun TileUiModel.withPlacement(placed: PlacedTile): TileUiModel = copy(
    spanX = placed.spanX,
    spanY = placed.spanY,
    gridCol = placed.col,
    gridRow = placed.row,
)

/**
 * Étiquette à mémoriser pour un appareil : seulement si le nom affiché diffère vraiment de
 * celui publié par Home Assistant. Un nom inchangé (ou vidé) ne crée pas d'étiquette, ce
 * qui laisse Home Assistant rester la source du nom par défaut.
 */
fun storedLabel(displayedName: String, originalName: String?): String? =
    displayedName.trim().takeIf { it.isNotEmpty() && it != originalName }

/**
 * Modèle de tuile → case enregistrable.
 *
 * Une case décorative redevient un [WidgetItem] (son identifiant et son thème sont
 * conservés), une tuile d'entité garde son `entityId`. La géométrie vient des champs de
 * placement.
 */
fun TileUiModel.toPlacedTile(): PlacedTile {
    val kind = category.toWidgetKind()
    return PlacedTile(
        id = id,
        entityId = if (kind == null) entityId else null,
        widget = kind?.let { widgetKind ->
            WidgetItem(
                id = id,
                kind = widgetKind,
                theme = widgetTheme ?: widgetKind.defaultTheme,
                text = if (widgetKind == WidgetKind.Title) name else "",
            )
        },
        col = gridCol ?: 0,
        row = gridRow ?: 0,
        spanX = spanX.coerceAtLeast(1),
        spanY = spanY.coerceAtLeast(1),
    )
}
