package com.homeglass.app.domain.presence

/**
 * Bornes et valeurs par défaut du réveil de présence.
 *
 * Le seuil de distance est exprimé en centimètres : au-delà, la tablette considère qu'il n'y
 * a personne devant. L'anti-rebond évite de rallumer l'écran en rafale quand quelqu'un bouge
 * devant le capteur ou quand la caméra voit plusieurs frames de suite.
 */
object PresenceTuning {

    const val MIN_THRESHOLD_CM = 20f
    const val MAX_THRESHOLD_CM = 200f
    const val DEFAULT_THRESHOLD_CM = 80f

    const val MIN_COOLDOWN_MS = 1_000L
    const val MAX_COOLDOWN_MS = 15_000L
    const val DEFAULT_COOLDOWN_MS = 3_000L

    /** Une valeur absente, infinie ou hors bornes retombe sur un réglage utilisable. */
    fun clampThreshold(value: Float): Float =
        if (!value.isFinite()) DEFAULT_THRESHOLD_CM else value.coerceIn(MIN_THRESHOLD_CM, MAX_THRESHOLD_CM)

    fun clampCooldown(value: Long): Long = value.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)
}
