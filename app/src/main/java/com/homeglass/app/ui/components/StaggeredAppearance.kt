package com.homeglass.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.homeglass.app.ui.theme.GlassTheme
import kotlinx.coroutines.delay

/**
 * Apparition échelonnée (30 ms par élément) : léger décalage vertical, fondu et zoom
 * de 0,94 vers 1. Retourne [Modifier] directement pour être appliqué à un élément
 * mesuré par un [androidx.compose.ui.layout.Layout] ou un LazyList.
 */
@Composable
fun staggeredAppearance(index: Int): Modifier {
    val motion = GlassTheme.motion
    if (motion.reduceMotion) return Modifier

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(motion.staggerStepMillis * index)
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motion.appear(),
        label = "staggeredAppearance",
    )
    val offsetPx = with(LocalDensity.current) { 10.dp.toPx() }

    return Modifier.graphicsLayer {
        alpha = progress
        val scale = 0.94f + 0.06f * progress
        scaleX = scale
        scaleY = scale
        translationY = (1f - progress) * offsetPx
    }
}
