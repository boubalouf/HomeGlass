package com.homeglass.app.core.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Retours haptiques du design system : léger pour une bascule, plus marqué pour
 * une confirmation (verrouillage, scène lancée, ouverture de feuille).
 */
class GlassHaptics internal constructor(private val view: View) {

    /** Appui court sur une tuile (bascule on/off). */
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Confirmation marquée d'une action. */
    fun confirm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /** Ouverture d'une feuille de détail. */
    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberGlassHaptics(): GlassHaptics {
    val view = LocalView.current
    return remember(view) { GlassHaptics(view) }
}
