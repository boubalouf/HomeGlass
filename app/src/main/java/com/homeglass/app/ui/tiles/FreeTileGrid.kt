package com.homeglass.app.ui.tiles

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.homeglass.app.ui.components.staggeredAppearance
import com.homeglass.app.ui.theme.GlassTheme

/** Nombre de colonnes : 2 sur téléphone, 3 en paysage/tablette, 4 sur grand écran. */
internal fun freeColumnsFor(width: androidx.compose.ui.unit.Dp): Int = when {
    width >= 840.dp -> 4
    width >= 600.dp -> 3
    else -> 2
}

/** Position résolue d'une case : colonne, rangée, portée en colonnes et en rangées. */
internal data class Cell(val col: Int, val row: Int, val spanX: Int, val spanY: Int)

/**
 * Grille libre de l'accueil : chaque tuile occupe un rectangle explicite (`gridCol`,
 * `gridRow`, `spanX`, `spanY`). Les tuiles sans position (sections générées) sont posées
 * automatiquement au premier emplacement libre, sans recouvrement.
 *
 * Les portées pilotent réellement la taille du composant : le contenu de la tuile se
 * réorganise pour la place disponible, ce n'est pas un simple agrandissement visuel.
 */
@Composable
fun FreeTileGrid(
    tiles: List<TileUiModel>,
    onTileClick: (TileUiModel) -> Unit,
    onTileLongClick: (TileUiModel) -> Unit,
    onTileLevelChange: (TileUiModel, Float) -> Unit,
    modifier: Modifier = Modifier,
    animateAppearance: Boolean = true,
) {
    val dimens = GlassTheme.dimens

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = freeColumnsFor(maxWidth)
        val density = LocalDensity.current
        val spacingPx = with(density) { dimens.gridSpacing.roundToPx() }
        val rowHeightPx = with(density) { dimens.tileHeight.roundToPx() }
        val cells = remember(tiles, columns) { resolveCells(tiles, columns) }

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
            val rows = cells.maxOfOrNull { it.row + it.spanY } ?: 0
            val gridHeight = if (rows == 0) 0 else rows * rowHeightPx + (rows - 1) * spacingPx

            layout(gridWidth, gridHeight) {
                measurables.forEachIndexed { index, measurable ->
                    val cell = cells[index]
                    val width = columnWidth * cell.spanX + spacingPx * (cell.spanX - 1)
                    val height = rowHeightPx * cell.spanY + spacingPx * (cell.spanY - 1)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    placeable.place(
                        x = cell.col * (columnWidth + spacingPx),
                        y = cell.row * (rowHeightPx + spacingPx),
                    )
                }
            }
        }
    }
}

/**
 * Résout la géométrie de toutes les tuiles : d'abord les positions explicites (enregistrées
 * dans le tableau de bord), puis le placement automatique des autres au premier trou libre.
 */
internal fun resolveCells(tiles: List<TileUiModel>, columns: Int): List<Cell> {
    val occupied = mutableListOf<MutableList<Boolean>>()
    fun ensureRow(row: Int) {
        while (occupied.size <= row) occupied.add(MutableList(columns) { false })
    }

    fun free(col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        if (col < 0 || row < 0 || col + spanX > columns) return false
        for (r in row until row + spanY) {
            for (c in col until col + spanX) {
                if (r < occupied.size && occupied[r][c]) return false
            }
        }
        return true
    }

    fun mark(col: Int, row: Int, spanX: Int, spanY: Int) {
        for (r in row until row + spanY) {
            ensureRow(r)
            for (c in col until col + spanX) occupied[r][c] = true
        }
    }

    val cells = arrayOfNulls<Cell>(tiles.size)

    tiles.forEachIndexed { index, tile ->
        val col = tile.gridCol
        val row = tile.gridRow
        if (col == null || row == null) return@forEachIndexed
        val spanX = tile.spanX.coerceIn(1, columns)
        val spanY = tile.spanY.coerceAtLeast(1)
        val fixedCol = col.coerceIn(0, columns - spanX)
        val fixedRow = row.coerceAtLeast(0)
        cells[index] = Cell(fixedCol, fixedRow, spanX, spanY)
        mark(fixedCol, fixedRow, spanX, spanY)
    }

    tiles.forEachIndexed { index, tile ->
        if (cells[index] != null) return@forEachIndexed
        val spanX = tile.spanX.coerceIn(1, columns)
        val spanY = tile.spanY.coerceAtLeast(1)
        var row = 0
        var col = 0
        while (true) {
            var found = -1
            for (candidate in 0..columns - spanX) {
                if (free(candidate, row, spanX, spanY)) {
                    found = candidate
                    break
                }
            }
            if (found >= 0) {
                col = found
                break
            }
            row++
        }
        cells[index] = Cell(col, row, spanX, spanY)
        mark(col, row, spanX, spanY)
    }

    @Suppress("UNCHECKED_CAST")
    return (cells.toList() as List<Cell?>).map { it ?: Cell(0, 0, 1, 1) }
}
