package com.homeglass.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Ressorts partagés du design system.
 *
 * Toutes les transitions utilisent un ressort (jamais de tween linéaire). Le changement
 * d'état d'une tuile dure ~250 ms à l'œil avec [state].
 *
 * Quand l'utilisateur désactive les animations dans les options Android
 * (ANIMATOR_DURATION_SCALE = 0), toutes les transitions deviennent instantanées.
 */
@Immutable
class GlassMotion(val reduceMotion: Boolean) {

    /** Bascule éteint/allumé : spring 250 ms, dampingRatio 0.8. */
    fun <T> state(): AnimationSpec<T> =
        if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 900f)

    /** Enfoncement d'une tuile : ressort plus vif, léger rebond. */
    fun <T> press(): AnimationSpec<T> =
        if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 1400f)

    /** Apparition échelonnée des tuiles et des feuilles. */
    fun <T> appear(): AnimationSpec<T> =
        if (reduceMotion) snap() else spring(dampingRatio = 0.85f, stiffness = 620f)

    /** Décalage entre deux tuiles d'une liste (spec : 30 ms). */
    val staggerStepMillis: Long
        get() = if (reduceMotion) 0L else 30L

    companion object {
        fun of(reduceMotion: Boolean): GlassMotion = GlassMotion(reduceMotion)
    }
}

val LocalGlassMotion = staticCompositionLocalOf { GlassMotion.of(reduceMotion = false) }
