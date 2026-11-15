package com.homeglass.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.core.haptics.rememberGlassHaptics
import com.homeglass.app.ui.components.glassHazePanel
import com.homeglass.app.ui.theme.GlassTheme

/** Onglets historiques (catalogue, aperçus). La navigation réelle vient de `layout.tabs`. */
enum class GlassTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    Rooms(R.string.nav_rooms, Icons.Rounded.GridView),
    Settings(R.string.nav_settings, Icons.Rounded.Settings),
}

/**
 * Barre de navigation flottante : pilule en verre (flou réel via Haze, teinte translucide
 * en repli), onglet actif accentué (capsule de fond), 12 dp au-dessus de la barre système.
 * Zone tactile ≥ 48 dp par onglet.
 *
 * Les onglets sont générés depuis `layout.tabs` : libellé, icône et ordre viennent de
 * l'add-on, seuls les onglets `enabled` sont affichés.
 */
@Composable
fun GlassBottomNavBar(
    tabs: List<NavTabUi>,
    selectedId: String,
    onSelect: (NavTabUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val shapes = GlassTheme.shapes
    val dimens = GlassTheme.dimens
    val motion = GlassTheme.motion
    val typography = GlassTheme.typography
    val haptics = rememberGlassHaptics()

    if (tabs.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                start = dimens.screenPadding,
                end = dimens.screenPadding,
                bottom = dimens.navBarBottomMargin,
            )
            .heightIn(min = dimens.navBarHeight)
            .glassHazePanel(shape = shapes.navBar, tint = colors.navBarBackground)
            // Bord lumineux : c'est lui qui rend le verre lisible même sur un fond peu contrasté.
            .border(
                width = 1.dp,
                color = if (colors.isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.65f),
                shape = shapes.navBar,
            )
            // Reflet supérieur, comme une vitre éclairée par le haut.
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (colors.isDark) 0.12f else 0.30f),
                        Color.Transparent,
                    ),
                ),
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selectedId
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) colors.navBarActiveContent else colors.navBarInactiveContent,
                animationSpec = motion.state(),
                label = "navContentColor",
            )
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = motion.state(),
                label = "navIndicator",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = dimens.minTouchTarget)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.accent.copy(alpha = 0.16f * indicatorAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptics.tap()
                        onSelect(tab)
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Tab
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null, // libellé visible juste en dessous
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = typography.caption,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Variante pour le catalogue et les aperçus, adossée aux onglets historiques. */
@Composable
fun GlassBottomNavBar(
    selected: GlassTab,
    onSelect: (GlassTab) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<GlassTab> = GlassTab.entries,
) {
    val uiTabs = tabs.map { tab ->
        NavTabUi(id = tab.name, label = stringResource(tab.labelRes), icon = tab.icon)
    }
    GlassBottomNavBar(
        tabs = uiTabs,
        selectedId = selected.name,
        onSelect = { uiTab -> onSelect(GlassTab.valueOf(uiTab.id)) },
        modifier = modifier,
    )
}
