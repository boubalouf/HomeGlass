package com.homeglass.app.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Fond plein écran : dégradé doux du thème, ou fond d'écran fourni par l'add-on avec
 * assombrissement et flou réglables.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    wallpaper: Painter? = null,
    wallpaperScrim: Float = 0f,
    wallpaperBlur: Dp = 0.dp,
) {
    val colors = GlassTheme.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors.backgroundGradient)),
    ) {
        if (wallpaper != null) {
            // Modifier.blur nécessite Android 12+ ; en dessous, l'image reste nette (dégradé visible par transparence).
            val blurModifier = if (wallpaperBlur > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(wallpaperBlur)
            } else {
                Modifier
            }
            Image(
                painter = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(blurModifier),
            )
            if (wallpaperScrim > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            colors.wallpaperScrim.copy(alpha = wallpaperScrim.coerceIn(0f, 1f)),
                        ),
                )
            }
        }
    }
}
