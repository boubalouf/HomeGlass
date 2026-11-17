package com.homeglass.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Garde l'écran allumé tant que [enabled] est vrai (interface vinyle affichée).
 *
 * La fenêtre est libérée au démontage du composable : quitter l'appli ou fermer le lecteur
 * rend la veille automatique à Android.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Force le paysage quand [enabled] est vrai (mode vinyle), puis rend l'orientation libre en
 * sortie — l'orientation choisie par l'utilisateur avant l'ouverture est restaurée.
 */
@Composable
fun LockLandscape(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return

    DisposableEffect(activity, enabled) {
        val previous = activity.requestedOrientation
        if (enabled) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            if (enabled) {
                activity.requestedOrientation =
                    if (previous == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                        previous == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    ) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        previous
                    }
            }
        }
    }
}

/** Remonte d'un contexte Compose jusqu'à l'activité qui l'héberge. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
