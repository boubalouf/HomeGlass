package com.homeglass.app.ui.tiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.homeglass.app.R

/**
 * Jeu de données de démonstration.
 *
 * Les noms d'entités sont fictifs (ils viendront de Home Assistant à l'exécution) ;
 * en revanche tous les libellés d'état passent par les ressources, comme dans l'app réelle.
 */
@Composable
internal fun sampleTiles(): List<TileUiModel> {
    val percent72 = stringResource(R.string.value_percent, 72)
    val percent40 = stringResource(R.string.value_percent, 40)
    val temperature = stringResource(R.string.value_temperature, "21,5")
    val on = stringResource(R.string.entity_state_on)
    val off = stringResource(R.string.entity_state_off)
    val locked = stringResource(R.string.state_locked)
    val playing = stringResource(R.string.state_playing)
    val ready = stringResource(R.string.state_ready)
    val armed = stringResource(R.string.state_armed)

    return listOf(
        TileUiModel(
            id = "light.salon",
            name = "Lampe du salon",
            stateText = percent72,
            category = TileCategory.Light,
            isActive = true,
            level = 0.72f,
        ),
        TileUiModel(
            id = "light.bureau",
            name = "Lampe du bureau",
            stateText = off,
            category = TileCategory.Light,
            isActive = false,
            level = 0f,
        ),
        TileUiModel(
            id = "climate.salon",
            name = "Thermostat",
            stateText = temperature,
            category = TileCategory.Climate,
            isActive = true,
            size = TileSize.Wide,
        ),
        TileUiModel(
            id = "cover.chambre",
            name = "Volet chambre",
            stateText = percent40,
            category = TileCategory.Cover,
            isActive = true,
            level = 0.4f,
        ),
        TileUiModel(
            id = "lock.entree",
            name = "Serrure entrée",
            stateText = locked,
            category = TileCategory.Lock,
            isActive = true,
        ),
        TileUiModel(
            id = "media.cuisine",
            name = "Enceinte cuisine",
            stateText = playing,
            category = TileCategory.Media,
            isActive = true,
        ),
        TileUiModel(
            id = "sensor.temperature",
            name = "Température salon",
            stateText = temperature,
            category = TileCategory.Sensor,
        ),
        TileUiModel(
            id = "switch.cafetiere",
            name = "Cafetière",
            stateText = on,
            category = TileCategory.Switch,
            isActive = true,
        ),
        TileUiModel(
            id = "alarm.maison",
            name = "Alarme",
            stateText = armed,
            category = TileCategory.Alarm,
            isActive = true,
        ),
        TileUiModel(
            id = "scene.nuit",
            name = "Mode nuit",
            stateText = ready,
            category = TileCategory.Scene,
        ),
        TileUiModel(
            id = "vacuum.salon",
            name = "Aspirateur",
            stateText = off,
            category = TileCategory.Vacuum,
            isActive = false,
            size = TileSize.Large,
        ),
        TileUiModel(
            id = "light.couloir",
            name = "Lampe du couloir",
            stateText = stringResource(R.string.state_unavailable),
            category = TileCategory.Light,
            isActive = false,
            isAvailable = false,
        ),
    )
}
