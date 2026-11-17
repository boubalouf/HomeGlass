package com.homeglass.app.ui.tiles

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.homeglass.app.domain.ha.MediaInfo
import kotlinx.serialization.json.JsonElement

/** Emprise d'une tuile dans la grille. */
enum class TileSize {
    /** 1 colonne × 1 rangée. */
    Small,

    /** 2 colonnes × 1 rangée. */
    Wide,

    /** 2 colonnes × 2 rangées. */
    Large,
}

/** Domaine fonctionnel d'une entité, qui pilote l'icône et la couleur par défaut. */
enum class TileCategory {
    Light,
    Climate,
    Switch,
    Cover,
    Lock,
    Alarm,
    Media,
    Sensor,
    Camera,
    Vacuum,
    Scene,
    Generic,

    /** Cases locales, sans entité Home Assistant. */
    Clock,
    Photo,
    Calendar,
    Title,
    Spotify,
}

/** Mode de présentation demandé par la configuration de la tuile. */
enum class TileDisplay {
    Auto,
    Button,
    Slider,
    Value,
    Graph,
}

/** Action à exécuter sur une tuile, traduite depuis le contrat layout. */
sealed interface TileAction {
    data object None : TileAction
    data object Toggle : TileAction
    data object MoreInfo : TileAction

    data class CallService(
        val service: String,
        val data: Map<String, JsonElement>? = null,
        val target: Map<String, JsonElement>? = null,
    ) : TileAction

    data class Navigate(val roomId: String) : TileAction
}

/**
 * Modèle d'affichage d'une tuile. Il ne dépend pas du schéma JSON : il est produit par
 * [com.homeglass.app.ui.home.LayoutMappers] à partir du contrat de l'add-on.
 *
 * @param stateText libellé d'état déjà localisé ("Allumé", "72 %", "21,5 °C"). Vide tant
 *   que les états Home Assistant ne sont pas branchés (étape 4) : la ligne est alors omise.
 * @param level niveau 0..1 : luminosité pour une lumière, position pour un volet.
 * @param tint couleur d'accent surchargée pour cette tuile par la configuration.
 */
@Immutable
data class TileUiModel(
    val id: String,
    val name: String,
    val stateText: String,
    val category: TileCategory,
    val isActive: Boolean = false,
    val isAvailable: Boolean = true,
    val size: TileSize = TileSize.Small,
    val tint: Color? = null,
    val level: Float? = null,
    val display: TileDisplay = TileDisplay.Auto,
    val tap: TileAction = TileAction.None,
    val longPress: TileAction = TileAction.MoreInfo,
    /** Entité Home Assistant pilotée par la tuile (`null` pour une tuile purement locale). */
    val entityId: String? = null,
    /** Piste en cours, pour une enceinte (`null` pour les autres domaines). */
    val media: MediaInfo? = null,
    /** Thème de la case décorative (`Clock`, `Photo`, `Calendar`), `null` sinon. */
    val widgetTheme: String? = null,
    /** Portée sur la grille libre, en cellules (1 = une cellule). */
    val spanX: Int = 1,
    val spanY: Int = 1,
    /** Position explicite sur la grille libre (`null` pour un placement automatique). */
    val gridCol: Int? = null,
    val gridRow: Int? = null,
) {
    /** Vrai pour une case décorative locale, sans entité Home Assistant. */
    val isWidget: Boolean
        get() = category == TileCategory.Clock ||
            category == TileCategory.Photo ||
            category == TileCategory.Calendar ||
            category == TileCategory.Title ||
            category == TileCategory.Spotify
}
