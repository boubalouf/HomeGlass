package com.homeglass.app.ui.tiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import com.homeglass.app.ui.components.GlassBackground
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.theme.HomeGlassTheme
import com.homeglass.app.ui.theme.ThemeConfig
import com.homeglass.app.ui.theme.ThemeMode

private val tileWidth = 173.dp
private val tileHeight = 96.dp

@Composable
private fun PreviewCanvas(
    dark: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    HomeGlassTheme(
        config = ThemeConfig(mode = if (dark) ThemeMode.Dark else ThemeMode.Light),
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassBackground()
            content()
        }
    }
}

@Composable
private fun TileCell(model: TileUiModel, modifier: Modifier = Modifier) {
    EntityTile(
        model = model,
        onToggle = {},
        onLongClick = {},
        onLevelChange = { _, _ -> },
        modifier = modifier.width(tileWidth).height(tileHeight),
    )
}

// region Tuiles — états allumée / éteinte / indisponible

@Preview(name = "Lumière · allumée / éteinte", group = "Tuiles", widthDp = 390, heightDp = 128, showBackground = true)
@Composable
private fun LightTileOnOffPreview() {
    PreviewCanvas {
        val tiles = sampleTiles()
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TileCell(tiles[0])
            TileCell(tiles[1])
        }
    }
}

@Preview(
    name = "Lumière · allumée / éteinte (sombre)",
    group = "Tuiles",
    widthDp = 390,
    heightDp = 128,
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
private fun LightTileOnOffDarkPreview() {
    PreviewCanvas(dark = true) {
        val tiles = sampleTiles()
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TileCell(tiles[0])
            TileCell(tiles[1])
        }
    }
}

@Preview(name = "Tuile · indisponible", group = "Tuiles", widthDp = 390, heightDp = 128, showBackground = true)
@Composable
private fun TileUnavailablePreview() {
    PreviewCanvas {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            TileCell(sampleTiles().last())
        }
    }
}

@Preview(
    name = "Tuile · police système 130 %",
    group = "Accessibilité",
    widthDp = 390,
    heightDp = 140,
    fontScale = 1.3f,
    showBackground = true,
)
@Composable
private fun TileLargeFontScalePreview() {
    PreviewCanvas {
        Row(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TileCell(sampleTiles()[0])
            TileCell(sampleTiles()[2])
        }
    }
}

// endregion

// region Tuiles par domaine

@Preview(name = "Tuiles · domaines", group = "Tuiles", widthDp = 390, heightDp = 260, showBackground = true)
@Composable
private fun DomainTilesPreview() {
    PreviewCanvas {
        val tiles = sampleTiles()
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TileCell(tiles.first { it.category == TileCategory.Cover })
                TileCell(tiles.first { it.category == TileCategory.Lock })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TileCell(tiles.first { it.category == TileCategory.Media })
                TileCell(tiles.first { it.category == TileCategory.Sensor })
            }
        }
    }
}

// endregion

// region Grille

@Preview(name = "Grille de tuiles", group = "Grille", widthDp = 390, heightDp = 560, showBackground = true)
@Composable
private fun TileGridPreview() {
    PreviewCanvas {
        TileGrid(
            tiles = sampleTiles(),
            onTileClick = {},
            onTileLongClick = {},
            onTileLevelChange = { _, _ -> },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            animateAppearance = false,
        )
    }
}

@Preview(
    name = "Grille de tuiles (sombre, tablette)",
    group = "Grille",
    widthDp = 700,
    heightDp = 520,
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
private fun TileGridDarkTabletPreview() {
    PreviewCanvas(dark = true) {
        TileGrid(
            tiles = sampleTiles(),
            onTileClick = {},
            onTileLongClick = {},
            onTileLevelChange = { _, _ -> },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            animateAppearance = false,
        )
    }
}

// endregion

// region Typographie

@Preview(name = "Typographie", group = "Fondations", widthDp = 390, heightDp = 300, showBackground = true)
@Composable
private fun TypographyPreview() {
    PreviewCanvas {
        val colors = GlassTheme.colors
        val typography = GlassTheme.typography
        Column(Modifier.fillMaxSize().padding(16.dp)) {
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
    }
}

// endregion
