package com.homeglass.app.domain.layout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Tuile d'appareil du contrat v1. Tous les champs sont optionnels côté JSON. */
@Serializable
data class TileItem(
    val id: String = "",
    val entityId: String = "",
    val name: String = "",
    val icon: String = "devices_other",
    /** `#RRGGBB` ou `#AARRGGBB` ; le serveur envoie `null` quand aucune teinte n'est choisie. */
    val accent: String? = null,
    val size: TileSize = TileSize.Small,
    val display: Display = Display.Auto,
    val hidden: Boolean = false,
    val tap: LayoutAction? = null,
    val longPress: LayoutAction? = null,
)

/** Carte de scène : dégradé à deux couleurs et une ou plusieurs actions. */
@Serializable
data class SceneItem(
    val id: String = "",
    val name: String = "",
    val icon: String = "auto_awesome",
    val gradient: List<String> = DEFAULT_GRADIENT,
    val actions: List<LayoutAction> = emptyList(),
) {
    companion object {
        val DEFAULT_GRADIENT = listOf("#FF9500", "#FF375F")
    }
}

/**
 * Action déclenchée par un tap, un appui long ou une carte de scène.
 *
 * `data` et `target` sont des objets libres (cible Home Assistant) : conservés en
 * [JsonElement] pour ne rien perdre du contrat, sans figer leur forme.
 */
@Serializable
data class LayoutAction(
    val type: ActionType = ActionType.None,
    @SerialName("service") val service: String? = null,
    val data: Map<String, JsonElement>? = null,
    val target: Map<String, JsonElement>? = null,
    val roomId: String? = null,
)

/** Item de section décodé, selon le `kind` de sa section. */
sealed interface SectionItem {
    data class Tile(val tile: TileItem) : SectionItem
    data class Scene(val scene: SceneItem) : SectionItem
}
