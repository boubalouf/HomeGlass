package com.homeglass.app.ui.tiles

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.homeglass.app.ui.components.staggeredAppearance
import com.homeglass.app.ui.theme.GlassTheme

/** Nombre de colonnes : 2 sur téléphone, 3 en paysage/tablette, 4 sur grand écran. */
private fun columnsFor(width: androidx.compose.ui.unit.Dp): Int = when {
    width >= 840.dp -> 4
    width >= 600.dp -> 3
    else -> 2
}

private data class TilePlacement(val col: Int, val row: Int, val colSpan: Int, val rowSpan: Int)

/**
 * Grille de tuiles à lignes homogènes, avec support des tuiles larges (2 colonnes)
 * et grandes (2 × 2).
 *
 * Les hauteurs de rangée valent exactement `tileHeight` (96 dp par défaut) : une tuile
 * large reste donc alignée avec ses voisines. Un [Layout] maison est utilisé plutôt
 * qu'un LazyVerticalGrid pour garantir que toutes les tuiles d'une rangée partagent la
 * même hauteur quelle que soit la longueur de leur nom.
 */
@Composable
fun TileGrid(
    tiles: List<TileUiModel>,
    onTileClick: (TileUiModel) -> Unit,
    onTileLongClick: (TileUiModel) -> Unit,
    onTileLevelChange: (TileUiModel, Float) -> Unit,
    modifier: Modifier = Modifier,
    animateAppearance: Boolean = true,
) {
    val dimens = GlassTheme.dimens

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = columnsFor(maxWidth)
        val density = LocalDensity.current
        val spacingPx = with(density) { dimens.gridSpacing.roundToPx() }
        val rowHeightPx = with(density) { dimens.tileHeight.roundToPx() }
        val placements = placeTiles(tiles, columns)

        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                tiles.forEachIndexed { index, tile ->
                    EntityTile(
                        model = tile,
                        onToggle = onTileClick,
                        onLongClick = onTileLongClick,
                        onLevelChange = onTileLevelChange,
                        modifier = if (animateAppearance) staggeredAppearance(index) else Modifier,
                    )
                }
            },
        ) { measurables, constraints ->
            val gridWidth = constraints.maxWidth
            val columnWidth = ((gridWidth - spacingPx * (columns - 1)) / columns).coerceAtLeast(0)
            val rows = placements.maxOfOrNull { it.row + it.rowSpan } ?: 0
            val gridHeight = if (rows == 0) 0 else rows * rowHeightPx + (rows - 1) * spacingPx

            layout(gridWidth, gridHeight) {
                measurables.forEachIndexed { index, measurable ->
                    val placement = placements[index]
                    val width = columnWidth * placement.colSpan + spacingPx * (placement.colSpan - 1)
                    val height = rowHeightPx * placement.rowSpan + spacingPx * (placement.rowSpan - 1)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    placeable.place(
                        x = placement.col * (columnWidth + spacingPx),
                        y = placement.row * (rowHeightPx + spacingPx),
                    )
                }
            }
        }
    }
}

/** Placement glouton en lecture ligne par ligne, avec recherche du premier emplacement libre. */
private fun placeTiles(tiles: List<TileUiModel>, columns: Int): List<TilePlacement> {
    val occupied = mutableListOf<MutableList<Boolean>>()

    fun ensureRow(row: Int) {
        while (occupied.size <= row) occupied.add(MutableList(columns) { false })
    }

    fun isFree(row: Int, col: Int, rowSpan: Int, colSpan: Int): Boolean {
        for (r in row until row + rowSpan) {
            for (c in col until col + colSpan) {
                if (c >= columns) return false
                if (r < occupied.size && occupied[r][c]) return false
            }
        }
        return true
    }

    val placements = ArrayList<TilePlacement>(tiles.size)
    for (tile in tiles) {
        val colSpan = when (tile.size) {
            TileSize.Small -> 1
            TileSize.Wide, TileSize.Large -> 2
        }.coerceAtMost(columns)
        val rowSpan = if (tile.size == TileSize.Large) 2 else 1

        var row = 0
        var placed: TilePlacement? = null
        while (placed == null) {
            for (col in 0..columns - colSpan) {
                if (isFree(row, col, rowSpan, colSpan)) {
                    for (r in row until row + rowSpan) {
                        ensureRow(r)
                        for (c in col until col + colSpan) occupied[r][c] = true
                    }
                    placed = TilePlacement(col = col, row = row, colSpan = colSpan, rowSpan = rowSpan)
                    break
                }
            }
            if (placed == null) row++
        }
        placements += placed
    }
    return placements
}
