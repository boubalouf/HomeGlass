package com.homeglass.app.ui.tiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Blinds
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.homeglass.app.ui.theme.GlassColors

/** Icône et couleur par défaut de chaque catégorie du design system. */
object TileVisuals {

    fun icon(category: TileCategory): ImageVector = when (category) {
        TileCategory.Light -> Icons.Rounded.Lightbulb
        TileCategory.Climate -> Icons.Rounded.Thermostat
        TileCategory.Switch -> Icons.Rounded.PowerSettingsNew
        TileCategory.Cover -> Icons.Rounded.Blinds
        TileCategory.Lock -> Icons.Rounded.Lock
        TileCategory.Alarm -> Icons.Rounded.Shield
        TileCategory.Media -> Icons.Rounded.Speaker
        TileCategory.Sensor -> Icons.AutoMirrored.Rounded.ShowChart
        TileCategory.Camera -> Icons.Rounded.CameraAlt
        TileCategory.Vacuum -> Icons.Rounded.CleaningServices
        TileCategory.Scene -> Icons.Rounded.AutoAwesome
        TileCategory.Generic -> Icons.Rounded.DevicesOther
    }

    /**
     * Couleur de la tuile active : surcharge de la configuration si présente, sinon
     * couleur de catégorie. Les éléments de sécurité sont verts quand ils sont sécurisés.
     */
    fun color(model: TileUiModel, colors: GlassColors): Color = model.tint ?: when (model.category) {
        TileCategory.Light -> colors.categoryLight
        TileCategory.Climate -> colors.categoryHeat
        TileCategory.Switch -> colors.categorySwitch
        TileCategory.Cover -> colors.categoryCover
        TileCategory.Lock -> if (model.isActive) colors.categorySecure else colors.categoryWarning
        TileCategory.Alarm -> if (model.isActive) colors.categorySecure else colors.categoryDanger
        TileCategory.Media -> colors.categoryMedia
        TileCategory.Sensor -> colors.categorySensor
        TileCategory.Camera -> colors.categoryCool
        TileCategory.Vacuum -> colors.categorySwitch
        TileCategory.Scene -> colors.accent
        TileCategory.Generic -> colors.categoryGeneric
    }
}
