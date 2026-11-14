package com.homeglass.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.homeglass.app.R
import androidx.compose.ui.res.stringResource
import com.homeglass.app.ui.theme.GlassTheme
import com.homeglass.app.ui.theme.HomeGlassTheme
import com.homeglass.app.ui.theme.ThemeConfig
import com.homeglass.app.ui.theme.ThemeMode

@Composable
private fun GlassSurfacePreviewCanvas(dark: Boolean) {
    HomeGlassTheme(
        config = ThemeConfig(mode = if (dark) ThemeMode.Dark else ThemeMode.Light),
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassBackground()
            GlassSurface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                contentPadding = PaddingValues(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.showcase_typography_section),
                    style = GlassTheme.typography.sectionTitle,
                    color = if (dark) GlassTheme.colors.textOnInactivePrimary else GlassTheme.colors.screenTextPrimary,
                )
            }
        }
    }
}

@Preview(name = "Verre dépoli", group = "Fondations", widthDp = 390, heightDp = 220, showBackground = true)
@Composable
private fun GlassSurfaceLightPreview() {
    GlassSurfacePreviewCanvas(dark = false)
}

@Preview(
    name = "Verre dépoli (sombre)",
    group = "Fondations",
    widthDp = 390,
    heightDp = 220,
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
private fun GlassSurfaceDarkPreview() {
    GlassSurfacePreviewCanvas(dark = true)
}
