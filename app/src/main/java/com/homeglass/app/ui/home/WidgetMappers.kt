package com.homeglass.app.ui.home

import com.homeglass.app.domain.dashboard.PlacedTile
import com.homeglass.app.domain.dashboard.WidgetItem
import com.homeglass.app.domain.dashboard.WidgetKind
import com.homeglass.app.ui.tiles.TileAction
import com.homeglass.app.ui.tiles.TileCategory
import com.homeglass.app.ui.tiles.TileSize
import com.homeglass.app.ui.tiles.TileUiModel

/**
 * Case décorative → modèle de tuile affichable.
 *
 * Aucune entité Home Assistant : le tap n'exécute rien. La photo occupe deux colonnes par
 * défaut pour rester lisible ; l'éditeur peut librement changer portée et position.
 */
fun WidgetItem.toTileUiModel(): TileUiModel {
    // Une photo et la case Spotify occupent deux colonnes ; un titre reste sur une cellule
    // pour pouvoir se glisser entre deux tuiles, puis être élargi si besoin.
    val defaultSpanX = if (kind == WidgetKind.Photo || kind == WidgetKind.Spotify) 2 else 1
    return TileUiModel(
        id = id,
        name = when (kind) {
            WidgetKind.Title -> text
            WidgetKind.Spotify -> "Spotify"
            else -> ""
        },
        stateText = "",
        category = kind.toCategory(),
        size = if (defaultSpanX == 2) TileSize.Wide else TileSize.Small,
        tap = TileAction.None,
        longPress = TileAction.None,
        widgetTheme = kind.coerceTheme(theme),
        spanX = defaultSpanX,
        spanY = 1,
    )
}

/** Case décorative déjà placée sur la grille libre. */
fun WidgetItem.toTileUiModel(placed: PlacedTile): TileUiModel = toTileUiModel().copy(
    spanX = placed.spanX,
    spanY = placed.spanY,
    gridCol = placed.col,
    gridRow = placed.row,
)

/** Catégorie de tuile correspondant au type de case décorative. */
fun WidgetKind.toCategory(): TileCategory = when (this) {
    WidgetKind.Clock -> TileCategory.Clock
    WidgetKind.Photo -> TileCategory.Photo
    WidgetKind.Calendar -> TileCategory.Calendar
    WidgetKind.Title -> TileCategory.Title
    WidgetKind.Spotify -> TileCategory.Spotify
}

/** Type de case décorative correspondant à une catégorie, `null` pour une entité. */
fun TileCategory.toWidgetKind(): WidgetKind? = when (this) {
    TileCategory.Clock -> WidgetKind.Clock
    TileCategory.Photo -> WidgetKind.Photo
    TileCategory.Calendar -> WidgetKind.Calendar
    TileCategory.Title -> WidgetKind.Title
    TileCategory.Spotify -> WidgetKind.Spotify
    else -> null
}
