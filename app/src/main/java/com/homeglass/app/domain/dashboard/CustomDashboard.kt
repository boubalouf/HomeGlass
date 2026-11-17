package com.homeglass.app.domain.dashboard

import kotlinx.serialization.Serializable

/**
 * Case placée sur la grille libre de l'accueil.
 *
 * Une case référence soit une entité Home Assistant ([entityId]), soit une case décorative
 * locale ([widget]) — jamais les deux. Sa géométrie est explicite : position et portée en
 * cellules. `spanX`/`spanY` valent 1 par défaut (une cellule).
 */
@Serializable
data class PlacedTile(
    val id: String,
    val entityId: String? = null,
    val widget: WidgetItem? = null,
    val col: Int = 0,
    val row: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
    /** Nom affiché personnalisé pour une entité (`null` : nom Home Assistant). */
    val label: String? = null,
) {
    /** Géométrie assainie : portées au moins égales à 1, thème de widget valide. */
    fun sanitized(): PlacedTile = copy(
        col = col.coerceAtLeast(0),
        row = row.coerceAtLeast(0),
        spanX = spanX.coerceAtLeast(1),
        spanY = spanY.coerceAtLeast(1),
        widget = widget?.normalized(),
        label = label?.trim()?.takeIf { it.isNotEmpty() },
    )

    /** Case pleine et exploitable : entité non vide ou widget présent. */
    val isTileValid: Boolean get() = !entityId.isNullOrBlank() || widget != null
}

/**
 * Ancien format : une zone nommée et la liste plate de ses appareils.
 * Conservé uniquement pour lire et migrer les tableaux déjà enregistrés.
 */
@Serializable
data class CustomSection(
    val id: String,
    val title: String = "",
    val entityIds: List<String> = emptyList(),
)

/**
 * Tableau de bord personnel : une grille libre de cases.
 *
 * [tiles] est la source de vérité. [sections] et [widgets] sont l'ancien format, lus une
 * seule fois par [normalized] puis convertis en cases placées. Aucune référence à Android
 * ici : le modèle reste testable sur JVM.
 */
@Serializable
data class CustomDashboard(
    val tiles: List<PlacedTile> = emptyList(),
    val sections: List<CustomSection> = emptyList(),
    val widgets: List<WidgetItem> = emptyList(),
) {
    /** Le tableau contient au moins une case, y compris au format hérité. */
    val isConfigured: Boolean
        get() = tiles.isNotEmpty() || sections.any { it.entityIds.isNotEmpty() } || widgets.isNotEmpty()

    val hasContent: Boolean get() = isConfigured

    /**
     * Version assainie et migrée : cases valides uniquement, identifiants uniques, géométrie
     * corrigée. L'ancien format (zones + widgets) est déplié en cases auto-placées.
     */
    fun normalized(): CustomDashboard {
        val seen = mutableSetOf<String>()
        val cleaned = (tiles + migratedTiles())
            .mapNotNull { raw ->
                val tile = raw.sanitized()
                // Les cases texte ne sont plus proposées : elles sont retirées des tableaux
                // existants au chargement comme à l'enregistrement.
                if (!tile.isTileValid || tile.widget?.kind == WidgetKind.Title || !seen.add(tile.id)) {
                    null
                } else {
                    tile
                }
            }
            .sortedWith(compareBy({ it.row }, { it.col }))
        return CustomDashboard(cleaned)
    }

    /** Cases issues de l'ancien format, vides quand [tiles] est déjà renseigné. */
    private fun migratedTiles(): List<PlacedTile> {
        if (tiles.isNotEmpty()) return emptyList()

        val fromSections = sections.flatMap { section ->
            section.entityIds
                .filter { it.isNotBlank() }
                .map { entityId ->
                    PlacedTile(id = "tile_$entityId", entityId = entityId)
                }
        }
        val fromWidgets = widgets.map { widget ->
            PlacedTile(
                id = widget.id,
                widget = widget.normalized(),
                spanX = if (widget.kind == WidgetKind.Photo || widget.kind == WidgetKind.Spotify) 2 else 1,
            )
        }
        return DashboardLayout.autoPlace(fromSections + fromWidgets)
    }

    companion object {
        /** Nouvelle case pour une entité, au premier emplacement libre. */
        fun entityTile(entityId: String, current: List<PlacedTile>): PlacedTile {
            val spot = DashboardLayout.firstFree(current, 1, 1) ?: (0 to 0)
            return PlacedTile(
                id = "tile_${entityId}_${System.currentTimeMillis()}",
                entityId = entityId,
                col = spot.first,
                row = spot.second,
            )
        }

        /** Nouvelle case décorative, au premier emplacement libre. */
        fun widgetTile(kind: WidgetKind, current: List<PlacedTile>): PlacedTile {
            val spanX = if (kind == WidgetKind.Photo || kind == WidgetKind.Spotify) 2 else 1
            val spot = DashboardLayout.firstFree(current, spanX, 1) ?: (0 to 0)
            return PlacedTile(
                id = "widget_${kind.name}_${System.currentTimeMillis()}",
                widget = WidgetItem(
                    id = "widget_${kind.name}_${System.currentTimeMillis()}",
                    kind = kind,
                    theme = kind.defaultTheme,
                ),
                col = spot.first,
                row = spot.second,
                spanX = spanX,
            )
        }
    }
}
