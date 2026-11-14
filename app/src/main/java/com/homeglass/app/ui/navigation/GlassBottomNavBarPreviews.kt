package com.homeglass.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.homeglass.app.ui.components.GlassBackground
import com.homeglass.app.ui.theme.HomeGlassTheme
import com.homeglass.app.ui.theme.ThemeConfig
import com.homeglass.app.ui.theme.ThemeMode

@Composable
private fun NavBarPreviewCanvas(dark: Boolean) {
    HomeGlassTheme(
        config = ThemeConfig(mode = if (dark) ThemeMode.Dark else ThemeMode.Light),
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassBackground()
            GlassBottomNavBar(
                selected = GlassTab.Home,
                onSelect = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Preview(name = "Barre de navigation", group = "Navigation", widthDp = 390, heightDp = 140, showBackground = true)
@Composable
private fun NavBarLightPreview() {
    NavBarPreviewCanvas(dark = false)
}

@Preview(
    name = "Barre de navigation (sombre)",
    group = "Navigation",
    widthDp = 390,
    heightDp = 140,
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
private fun NavBarDarkPreview() {
    NavBarPreviewCanvas(dark = true)
}
