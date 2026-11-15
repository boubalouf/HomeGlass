package com.homeglass.app.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.homeglass.app.domain.layout.ActionType
import com.homeglass.app.domain.layout.BackgroundType
import com.homeglass.app.domain.layout.Density
import com.homeglass.app.domain.layout.Display
import com.homeglass.app.domain.layout.Layout
import com.homeglass.app.domain.layout.LayoutAction
import com.homeglass.app.domain.layout.LayoutParser
import com.homeglass.app.domain.layout.LayoutTheme
import com.homeglass.app.domain.layout.SceneItem
import com.homeglass.app.domain.layout.Section
import com.homeglass.app.domain.layout.SectionItem
import com.homeglass.app.domain.layout.TileItem
import com.homeglass.app.domain.layout.TileSize
import com.homeglass.app.ui.navigation.NavTabUi
import com.homeglass.app.ui.theme.GlassPalette
import com.homeglass.app.ui.theme.ThemeConfig
import com.homeglass.app.ui.theme.ThemeMode
import com.homeglass.app.ui.theme.WallpaperConfig
import com.homeglass.app.ui.tiles.TileAction
import com.homeglass.app.ui.tiles.TileCategory
import com.homeglass.app.ui.tiles.TileDisplay
import com.homeglass.app.ui.tiles.TileIcons
import com.homeglass.app.ui.tiles.TileSize as UiTileSize
import com.homeglass.app.ui.tiles.TileUiModel
import com.homeglass.app.domain.layout.ThemeMode as LayoutThemeMode

/** Couleur de repli des dégradés de scène incomplets (accent du design system + rose Apple). */
private val FallbackGradient = listOf(GlassPalette.AccentOrange, Color(0xFFFF375F))

/** Convertit un document de mise en page en modèle d'écran Maison. */
fun Layout.toHomeUi(): HomeUiModel = HomeUiModel(
    title = home.title.ifBlank { "Maison" },
    revision = revision,
    // Les chips de synthèse (lumières, climat, sécurité, médias) sont retirées de l'accueil :
    // seules les catégories encore utiles (énergie) resteraient visibles.
    chips = home.summaryChips.mapNotNull { chip -> summaryChips[chip] }
        .filterNot { chip -> chip in removedSummaryChips },
    sections = home.sections.map(Section::toUi).filter { it.tiles.isNotEmpty() || it.scenes.isNotEmpty() },
    tabs = tabs.filter { it.enabled }.map { tab ->
        NavTabUi(
            id = tab.id,
            label = tab.label.ifBlank { tab.type.name },
            icon = TileIcons.forTab(tab.icon, tab.type),
            type = tab.type,
        )
    },
)

/** Une section `scenes` produit des cartes à dégradé, une section `tiles` une grille. */
fun Section.toUi(): HomeSectionUi {
    val items = LayoutParser.decodeItems(this)
    return if (kind == Section.KIND_SCENES) {
        HomeSectionUi(
            id = id,
            title = title,
            kind = HomeSectionKind.Scenes,
            scenes = items.filterIsInstance<SectionItem.Scene>().map { it.scene.toUi() },
        )
    } else {
        HomeSectionUi(
            id = id,
            title = title,
            kind = HomeSectionKind.Tiles,
            tiles = items.filterIsInstance<SectionItem.Tile>()
                .filterNot { it.tile.hidden }
                .map { it.tile.toUi() },
        )
    }
}

/** Tuile du contrat vers modèle de tuile existant. */
fun TileItem.toUi(): TileUiModel {
    val uiSize = size.toUi()
    return TileUiModel(
        id = id.ifBlank { entityId },
        name = name.ifBlank { entityId },
        stateText = "",
        category = categoryFor(entityId),
        size = uiSize,
        tint = parseHexColor(accent),
        display = display.toUi(),
        tap = tap.toUiAction(TileAction.None),
        longPress = longPress.toUiAction(TileAction.MoreInfo),
        entityId = entityId.ifBlank { null },
        // La grille libre reprend la taille déclarée par le contrat : 1×1, 2×1 ou 2×2.
        spanX = if (uiSize == UiTileSize.Small) 1 else 2,
        spanY = if (uiSize == UiTileSize.Large) 2 else 1,
    )
}

/** Carte de scène du contrat vers modèle d'affichage. */
fun SceneItem.toUi(): SceneCardUi = SceneCardUi(
    id = id.ifBlank { name },
    name = name.ifBlank { "Scène" },
    icon = TileIcons.forName(icon, TileCategory.Scene),
    gradient = gradientColors(gradient),
    action = actions.firstOrNull().toUiAction(TileAction.None),
)

/** Thème du contrat vers [ThemeConfig] (mode, accent, rayon, densité, fond). */
fun LayoutTheme.toThemeConfig(): ThemeConfig = ThemeConfig(
    mode = when (mode) {
        LayoutThemeMode.Auto -> ThemeMode.Auto
        LayoutThemeMode.Light -> ThemeMode.Light
        LayoutThemeMode.Dark -> ThemeMode.Dark
    },
    accentColor = parseHexColor(accent),
    tileCornerRadius = tileRadiusDp.coerceIn(0, 60).dp,
    densityScale = when (density) {
        Density.Compact -> 0.9f
        Density.Standard -> 1f
        Density.Comfortable -> 1.1f
    },
    wallpaper = background.let { background ->
        if (background.type != BackgroundType.Image || background.assetId.isNullOrBlank()) {
            null
        } else {
            WallpaperConfig(
                // URL relative : elle sera servie par l'add-on (GET /api/assets/{id}).
                url = "/api/assets/${background.assetId}",
                scrim = background.dim.coerceIn(0f, 1f),
                blurRadius = background.blurDp.coerceIn(0f, 100f).dp,
            )
        }
    },
)

/** `#RRGGBB` ou `#AARRGGBB` → [Color] ; `null` si la valeur est absente ou invalide. */
fun parseHexColor(hex: String?): Color? {
    val value = hex?.trim()?.removePrefix("#") ?: return null
    if (value.length != 6 && value.length != 8) return null
    val parsed = value.toLongOrNull(16) ?: return null
    return Color(if (value.length == 6) 0xFF000000L or parsed else parsed)
}

/** Dégradé de scène : exactement deux couleurs, complété si le JSON en fournit moins. */
internal fun gradientColors(hexes: List<String>): List<Color> {
    val parsed = hexes.mapNotNull(::parseHexColor)
    return when {
        parsed.size >= 2 -> parsed.take(2)
        parsed.size == 1 -> listOf(parsed.first(), FallbackGradient[1])
        else -> FallbackGradient
    }
}

/** Domaine de l'entité (`light.salon` → `light`) vers catégorie d'affichage. */
internal fun categoryFor(entityId: String): TileCategory =
    when (entityId.substringBefore('.', missingDelimiterValue = "")) {
        "light" -> TileCategory.Light
        "climate", "water_heater" -> TileCategory.Climate
        "cover" -> TileCategory.Cover
        "lock" -> TileCategory.Lock
        "alarm_control_panel" -> TileCategory.Alarm
        "media_player" -> TileCategory.Media
        "camera" -> TileCategory.Camera
        "vacuum" -> TileCategory.Vacuum
        "scene", "script" -> TileCategory.Scene
        "switch", "input_boolean", "fan" -> TileCategory.Switch
        "sensor", "binary_sensor", "number", "input_number", "select", "input_select" ->
            TileCategory.Sensor

        else -> TileCategory.Generic
    }

private fun TileSize.toUi(): UiTileSize = when (this) {
    TileSize.Small -> UiTileSize.Small
    TileSize.Wide -> UiTileSize.Wide
    TileSize.Large -> UiTileSize.Large
}

private fun Display.toUi(): TileDisplay = when (this) {
    Display.Auto -> TileDisplay.Auto
    Display.Button -> TileDisplay.Button
    Display.Slider -> TileDisplay.Slider
    Display.Value -> TileDisplay.Value
    Display.Graph -> TileDisplay.Graph
}

private fun LayoutAction?.toUiAction(fallback: TileAction): TileAction = when (this?.type) {
    ActionType.Toggle -> TileAction.Toggle
    ActionType.MoreInfo -> TileAction.MoreInfo
    ActionType.CallService -> service?.let { TileAction.CallService(it, data, target) }
        ?: fallback

    ActionType.Navigate -> roomId?.let(TileAction::Navigate) ?: fallback
    ActionType.None, null -> fallback
}

/** Chips retirées de l'accueil à la demande du design. */
private val removedSummaryChips = setOf(
    SummaryChipUi.Lights,
    SummaryChipUi.Climate,
    SummaryChipUi.Security,
    SummaryChipUi.Media,
)

/** Clés du contrat → chips d'affichage. Une clé inconnue est ignorée. */
private val summaryChips: Map<String, SummaryChipUi> = mapOf(
    "lights" to SummaryChipUi.Lights,
    "climate" to SummaryChipUi.Climate,
    "security" to SummaryChipUi.Security,
    "media" to SummaryChipUi.Media,
    "energy" to SummaryChipUi.Energy,
)
