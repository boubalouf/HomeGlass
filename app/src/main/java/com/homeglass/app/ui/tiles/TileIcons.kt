package com.homeglass.app.ui.tiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Blinds
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.ui.graphics.vector.ImageVector
import com.homeglass.app.domain.layout.TabType

/**
 * Correspondance entre les noms d'icônes **Material Symbols en snake_case** du contrat et
 * les `ImageVector` Compose. Un nom inconnu retombe sur l'icône de la catégorie : le JSON
 * peut proposer de nouvelles icônes sans casser l'affichage.
 */
object TileIcons {

    private val byName: Map<String, ImageVector> = mapOf(
        "lightbulb" to Icons.Rounded.Lightbulb,
        "light" to Icons.Rounded.Lightbulb,
        "thermostat" to Icons.Rounded.Thermostat,
        "blinds" to Icons.Rounded.Blinds,
        "curtains" to Icons.Rounded.Blinds,
        "lock" to Icons.Rounded.Lock,
        "shield" to Icons.Rounded.Shield,
        "speaker" to Icons.Rounded.Speaker,
        "music_note" to Icons.Rounded.MusicNote,
        "play_circle" to Icons.Rounded.PlayArrow,
        "play_arrow" to Icons.Rounded.PlayArrow,
        "sensors" to Icons.Rounded.Sensors,
        "show_chart" to Icons.AutoMirrored.Rounded.ShowChart,
        "videocam" to Icons.Rounded.CameraAlt,
        "camera_alt" to Icons.Rounded.CameraAlt,
        "toggle_on" to Icons.Rounded.ToggleOn,
        "power_settings_new" to Icons.Rounded.PowerSettingsNew,
        "devices_other" to Icons.Rounded.DevicesOther,
        "cleaning_services" to Icons.Rounded.CleaningServices,
        "water_drop" to Icons.Rounded.WaterDrop,
        "water_heater" to Icons.Rounded.WaterDrop,
        "humidity_percentage" to Icons.Rounded.WaterDrop,
        "cloud" to Icons.Rounded.Cloud,
        "wb_sunny" to Icons.Rounded.WbSunny,
        "bedtime" to Icons.Rounded.Bedtime,
        "auto_awesome" to Icons.Rounded.AutoAwesome,
        "person" to Icons.Rounded.Person,
        "list" to Icons.AutoMirrored.Rounded.List,
        "bolt" to Icons.Rounded.Bolt,
        "settings" to Icons.Rounded.Settings,
        "apps" to Icons.Rounded.Apps,
        "home" to Icons.Rounded.Home,
        "meeting_room" to Icons.Rounded.MeetingRoom,
        "weekend" to Icons.Rounded.Weekend,
        "kitchen" to Icons.Rounded.Kitchen,
        "chair" to Icons.Rounded.Chair,
        "bed" to Icons.Rounded.Bed,
        "bathtub" to Icons.Rounded.Bathtub,
        "garage" to Icons.Rounded.Garage,
        "stairs" to Icons.Rounded.Stairs,
        "tv" to Icons.Rounded.Tv,
    )

    /** Icône d'une tuile : nom du contrat, sinon icône par défaut de la catégorie. */
    fun forName(name: String, category: TileCategory): ImageVector =
        byName[name] ?: TileVisuals.icon(category)

    /** Icône d'un onglet : nom du contrat, sinon icône du type d'onglet. */
    fun forTab(name: String, type: TabType): ImageVector =
        byName[name] ?: when (type) {
            TabType.Rooms -> Icons.Rounded.MeetingRoom
            TabType.Settings -> Icons.Rounded.Settings
            TabType.Home -> Icons.Rounded.Home
        }
}
