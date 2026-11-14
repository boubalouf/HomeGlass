package com.homeglass.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.homeglass.app.ui.theme.GlassTheme

/**
 * Panneau "verre dépoli" : forme arrondie + teinte translucide, sans bordure dure ni ombre.
 *
 * Remarque d'implémentation : il n'existe pas de flou d'arrière-plan natif dans Compose
 * (`Modifier.blur` floute le contenu du layer, pas ce qui est dessiné derrière). Comme le
 * fond de l'application est un dégradé lisse, la translucidité suffit à obtenir exactement
 * le rendu attendu ; pour un fond d'écran photo, le flou est appliqué à l'image elle-même
 * dans [GlassBackground] (Android 12+).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassTheme.shapes.sheet,
    tint: Color = GlassTheme.colors.sheetBackground,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .padding(contentPadding),
        content = content,
    )
}
