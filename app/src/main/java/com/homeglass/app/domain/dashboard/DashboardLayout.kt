package com.homeglass.app.domain.dashboard

/**
 * Géométrie de la grille libre de l'accueil.
 *
 * Chaque case occupe un rectangle (`col`, `row`, `spanX`, `spanY`) sur une grille de
 * [COLUMNS] colonnes, les rangées faisant une hauteur de tuile. Les fonctions sont pures et
 * testables sans Android : l'éditeur et l'affichage s'appuient dessus.
 */
object DashboardLayout {

    /** Colonnes de référence de l'éditeur (tablette). L'affichage s'adapte, jamais le modèle. */
    const val COLUMNS = 4

    /** Deux rectangles de la grille se recouvrent-ils ? */
    fun overlaps(a: PlacedTile, b: PlacedTile): Boolean =
        a.col < b.col + b.spanX && b.col < a.col + a.spanX &&
            a.row < b.row + b.spanY && b.row < a.row + a.spanY

    /** Place les cases les unes après les autres au premier emplacement libre de la grille. */
    fun autoPlace(tiles: List<PlacedTile>, columns: Int = COLUMNS): List<PlacedTile> {
        val occupied = HashSet<Long>()
        fun key(column: Int, row: Int): Long = row.toLong() * 1_000L + column
        fun free(column: Int, row: Int, spanX: Int, spanY: Int): Boolean {
            if (column < 0 || row < 0 || column + spanX > columns) return false
            for (x in column until column + spanX) {
                for (y in row until row + spanY) {
                    if (key(x, y) in occupied) return false
                }
            }
            return true
        }

        val placed = ArrayList<PlacedTile>(tiles.size)
        for (tile in tiles) {
            val spanX = tile.spanX.coerceIn(1, columns)
            val spanY = tile.spanY.coerceAtLeast(1)
            var row = 0
            var column = 0
            while (true) {
                var found = -1
                for (x in 0..columns - spanX) {
                    if (free(x, row, spanX, spanY)) {
                        found = x
                        break
                    }
                }
                if (found >= 0) {
                    column = found
                    break
                }
                row++
            }
            for (x in column until column + spanX) {
                for (y in row until row + spanY) occupied.add(key(x, y))
            }
            placed.add(tile.copy(col = column, row = row, spanX = spanX, spanY = spanY))
        }
        return placed
    }

    /**
     * Résout les chevauchements après un déplacement ou un redimensionnement.
     *
     * La case [priorityId] garde sa position : les autres cases qui la recouvrent sont
     * déplacées vers le premier emplacement libre, ce qui garde un rendu sans superposition.
     */
    fun settle(tiles: List<PlacedTile>, priorityId: String, columns: Int = COLUMNS): List<PlacedTile> {
        val priority = tiles.firstOrNull { it.id == priorityId }
        val ordered = if (priority != null) listOf(priority) + tiles.filter { it.id != priorityId } else tiles

        val placed = ArrayList<PlacedTile>(tiles.size)
        for (tile in ordered) {
            val spanX = tile.spanX.coerceIn(1, columns)
            val clamped = tile.copy(
                spanX = spanX,
                spanY = tile.spanY.coerceAtLeast(1),
                col = tile.col.coerceIn(0, (columns - spanX).coerceAtLeast(0)),
                row = tile.row.coerceAtLeast(0),
            )
            val spot = if (placed.any { overlaps(it, clamped) }) {
                firstFree(placed, clamped.spanX, clamped.spanY, columns)
            } else {
                null
            }
            placed.add(if (spot != null) clamped.copy(col = spot.first, row = spot.second) else clamped)
        }
        return placed
    }

    /** Premier emplacement libre pour un rectangle donné, en balayant ligne par ligne. */
    fun firstFree(
        tiles: List<PlacedTile>,
        spanX: Int,
        spanY: Int,
        columns: Int = COLUMNS,
    ): Pair<Int, Int>? {
        val safeSpanX = spanX.coerceIn(1, columns)
        val safeSpanY = spanY.coerceAtLeast(1)
        var row = 0
        while (row < 500) {
            for (col in 0..columns - safeSpanX) {
                val probe = PlacedTile(
                    id = "probe",
                    col = col,
                    row = row,
                    spanX = safeSpanX,
                    spanY = safeSpanY,
                )
                if (tiles.none { overlaps(it, probe) }) return col to row
            }
            row++
        }
        return null
    }
}
