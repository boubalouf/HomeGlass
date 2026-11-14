package com.homeglass.app.ui.showcase

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.ui.components.GlassBackground
import com.homeglass.app.ui.navigation.GlassBottomNavBar
import com.homeglass.app.ui.navigation.GlassTab
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.tiles.TileGrid
import com.homeglass.app.ui.tiles.sampleTiles

/**
 * Écran temporaire de catalogue : il sert à visualiser le design system et les tuiles
 * pendant les étapes 1 et 2. Il sera remplacé par l'écran "Maison" alimenté par la
 * mise en page de l'add-on.
 */
@Composable
fun DesignSystemShowcase(modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val dimens = GlassTheme.dimens
    val typography = GlassTheme.typography
    var selectedTab by remember { mutableStateOf(GlassTab.Home) }
    val tiles = sampleTiles()

    Box(modifier = modifier.fillMaxSize()) {
        GlassBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = dimens.screenPadding)
                .padding(top = 24.dp, bottom = 140.dp),
        ) {
            Text(
                text = stringResource(R.string.showcase_title),
                style = typography.largeTitle,
                color = colors.screenTextPrimary,
            )
            Text(
                text = stringResource(R.string.showcase_subtitle),
                style = typography.caption,
                color = colors.screenTextSecondary,
            )

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.showcase_section_tiles),
                style = typography.sectionTitle,
                color = colors.screenTextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            TileGrid(
                tiles = tiles,
                onTileClick = {},
                onTileLongClick = {},
                onTileLevelChange = { _, _ -> },
            )

            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.showcase_section_typography),
                style = typography.sectionTitle,
                color = colors.screenTextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.showcase_typography_large_title),
                style = typography.largeTitle,
                color = colors.screenTextPrimary,
            )
            Text(
                text = stringResource(R.string.showcase_typography_section),
                style = typography.sectionTitle,
                color = colors.screenTextPrimary,
            )
            Text(
                text = stringResource(R.string.showcase_label_name),
                style = typography.tileName,
                color = colors.screenTextPrimary,
            )
            Text(
                text = stringResource(R.string.showcase_label_status),
                style = typography.tileStatus,
                color = colors.screenTextSecondary,
            )
            Text(
                text = stringResource(R.string.showcase_label_caption),
                style = typography.caption,
                color = colors.screenTextSecondary,
            )
        }

        GlassBottomNavBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
