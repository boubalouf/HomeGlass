package com.homeglass.app.ui.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.homeglass.app.R
import com.homeglass.app.ui.navigation.NavTabUi
import com.homeglass.app.ui.tiles.TileAction
import com.homeglass.app.ui.tiles.TileUiModel

/** Section de l'écran Maison, déjà résolue en modèles d'affichage. */
@Immutable
data class HomeSectionUi(
    val id: String,
    val title: String,
    val kind: HomeSectionKind,
    val tiles: List<TileUiModel> = emptyList(),
    val scenes: List<SceneCardUi> = emptyList(),
    /** Vrai pour la grille libre : positions et portées explicites, sans reflow automatique. */
    val freeform: Boolean = false,
)

enum class HomeSectionKind { Tiles, Scenes }

/** Carte de scène à dégradé. */
@Immutable
data class SceneCardUi(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val action: TileAction,
)

/** Chip de synthèse (lumières, climat, sécurité, médias, énergie). */
enum class SummaryChipUi(@StringRes val labelRes: Int, val icon: ImageVector) {
    Lights(R.string.chip_lights, Icons.Rounded.Lightbulb),
    Climate(R.string.chip_climate, Icons.Rounded.Thermostat),
    Security(R.string.chip_security, Icons.Rounded.Shield),
    Media(R.string.chip_media, Icons.Rounded.Speaker),
    Energy(R.string.chip_energy, Icons.Rounded.Bolt),
}

/** Pièce (aire Home Assistant) avec ses sections déjà mappées. */
@Immutable
data class RoomUi(
    val id: String,
    val name: String,
    val sections: List<HomeSectionUi> = emptyList(),
)

/** Modèle complet de l'écran Maison. */
@Immutable
data class HomeUiModel(
    val title: String,
    val revision: Int,
    val chips: List<SummaryChipUi>,
    val sections: List<HomeSectionUi>,
    val tabs: List<NavTabUi>,
)
