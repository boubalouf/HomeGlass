package com.homeglass.app.core.system

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Vrai si l'utilisateur a demandé la suppression des animations
 * (Options développeur / Accessibilité : échelle d'animation à 0).
 *
 * L'observateur permet de réagir au changement sans recréer l'activité.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduceMotion by remember { mutableStateOf(isAnimationDisabled(context)) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = isAnimationDisabled(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        // Resynchronise au cas où le réglage aurait changé pendant l'enregistrement.
        reduceMotion = isAnimationDisabled(context)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return reduceMotion
}

private fun isAnimationDisabled(context: Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
} catch (_: Exception) {
    // Certains environnements (Preview, appareils verrouillés) refusent la lecture.
    false
}
