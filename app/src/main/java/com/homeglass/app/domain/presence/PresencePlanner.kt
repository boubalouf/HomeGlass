package com.homeglass.app.domain.presence

/** Détecteurs à activer pour un mode de réveil donné. */
data class PresencePlan(val useProximity: Boolean, val useCamera: Boolean) {
    val isEmpty: Boolean get() = !useProximity && !useCamera
}

object PresencePlanner {

    /**
     * Le mode automatique combine les deux sources quand elles existent : le capteur réveille
     * instantanément quand on s'approche du bord, la caméra détecte une personne à distance.
     * Les modes explicites n'activent que la source demandée.
     */
    fun plan(source: PresenceSource, hasProximity: Boolean, hasCamera: Boolean): PresencePlan =
        when (source) {
            PresenceSource.Proximity -> PresencePlan(useProximity = hasProximity, useCamera = false)
            PresenceSource.Camera -> PresencePlan(useProximity = false, useCamera = hasCamera)
            PresenceSource.Auto -> PresencePlan(useProximity = hasProximity, useCamera = hasCamera)
        }
}
