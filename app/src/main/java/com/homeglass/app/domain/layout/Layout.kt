package com.homeglass.app.domain.layout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Contrat de mise en page HomeGlass, schéma v1 — miroir exact du JSON produit par l'add-on.
 *
 * Règles appliquées ici :
 * - tous les champs ont une valeur par défaut explicite, donc un JSON partiel reste lisible ;
 * - les champs que le serveur envoie à `null` (`accent`, `assetId`, `tap`, `data`, `target`…)
 *   sont nullables : le parseur les accepte sans erreur ;
 * - les champs inconnus sont ignorés (voir [LayoutParser]) ;
 * - les valeurs d'énumération inconnues retombent sur la valeur par défaut grâce à
 *   `coerceInputValues` du parseur, jamais sur une exception.
 *
 * Ce modèle est volontairement séparé des modèles d'affichage ([com.homeglass.app.ui.home]) :
 * le JSON de l'add-on peut évoluer sans toucher aux composants.
 */
@Serializable
data class Layout(
    val schemaVersion: Int = LayoutParser.SUPPORTED_SCHEMA_VERSION,
    val revision: Int = 1,
    val publishedAt: String = "",
    val theme: LayoutTheme = LayoutTheme(),
    val tabs: List<LayoutTab> = emptyList(),
    val home: LayoutHome = LayoutHome(),
    val rooms: List<LayoutRoom> = emptyList(),
)

@Serializable
enum class ThemeMode {
    @SerialName("auto") Auto,
    @SerialName("light") Light,
    @SerialName("dark") Dark,
}

@Serializable
enum class Density {
    @SerialName("compact") Compact,
    @SerialName("standard") Standard,
    @SerialName("comfortable") Comfortable,
}

@Serializable
enum class BackgroundType {
    @SerialName("gradient") Gradient,
    @SerialName("solid") Solid,
    @SerialName("image") Image,
}

@Serializable
enum class TabType {
    @SerialName("home") Home,
    @SerialName("rooms") Rooms,
    @SerialName("settings") Settings,
}

@Serializable
enum class TileSize {
    @SerialName("small") Small,
    @SerialName("wide") Wide,
    @SerialName("large") Large,
}

@Serializable
enum class Display {
    @SerialName("auto") Auto,
    @SerialName("button") Button,
    @SerialName("slider") Slider,
    @SerialName("value") Value,
    @SerialName("graph") Graph,
}

@Serializable
enum class ActionType {
    @SerialName("toggle") Toggle,
    @SerialName("more_info") MoreInfo,
    @SerialName("call_service") CallService,
    @SerialName("navigate") Navigate,
    @SerialName("none") None,
}

/** Type de contenu d'une section. Conservé en texte : un « kind » inconnu ne casse rien. */
@Serializable
data class Section(
    val id: String = "",
    val kind: String = KIND_TILES,
    val title: String = "",
    /**
     * Items bruts : leur forme dépend de `kind` (`tiles` ou `scenes`). Ils sont décodés
     * en [TileItem] ou [SceneItem] par [LayoutParser.decodeItems], un item invalide étant
     * ignoré sans invalider le reste du document.
     */
    val items: List<JsonElement> = emptyList(),
) {
    companion object {
        const val KIND_TILES = "tiles"
        const val KIND_SCENES = "scenes"
    }
}

@Serializable
data class LayoutTheme(
    val mode: ThemeMode = ThemeMode.Auto,
    val accent: String = "#FF9500",
    val tileRadiusDp: Int = 22,
    val density: Density = Density.Standard,
    val background: LayoutBackground = LayoutBackground(),
)

@Serializable
data class LayoutBackground(
    val type: BackgroundType = BackgroundType.Gradient,
    /** Identifiant d'asset servi par `GET /api/assets/{id}`. */
    val assetId: String? = null,
    val blurDp: Float = 0f,
    val dim: Float = 0f,
)

@Serializable
data class LayoutTab(
    val id: String = "",
    val type: TabType = TabType.Home,
    val label: String = "",
    val icon: String = "apps",
    val enabled: Boolean = true,
)

/**
 * Accueil.
 *
 * `summaryChips` est une liste de clés ("lights", "climate"…) conservée en texte : une
 * catégorie ajoutée par une version plus récente de l'add-on est ignorée à l'affichage, elle
 * ne fait pas échouer tout le document (un élément de liste n'a pas de valeur par défaut, il
 * ne bénéficie donc pas de `coerceInputValues`).
 */
@Serializable
data class LayoutHome(
    val title: String = "Maison",
    val summaryChips: List<String> = DEFAULT_SUMMARY_CHIPS,
    val sections: List<Section> = emptyList(),
) {
    companion object {
        val DEFAULT_SUMMARY_CHIPS = listOf("lights", "climate", "security", "media")
    }
}

@Serializable
data class LayoutRoom(
    val id: String = "",
    val name: String = "",
    val icon: String = "meeting_room",
    val areaId: String? = null,
    val sections: List<Section> = emptyList(),
)
