package com.homeglass.app.domain.presence

import kotlinx.serialization.Serializable

/** Déclencheur du réveil de présence, choisi dans les réglages. */
@Serializable
enum class PresenceSource {
    /** Le meilleur disponible : capteur de proximité s'il existe, sinon caméra. */
    Auto,

    /** Capteur de proximité uniquement (instantané, sans image). */
    Proximity,

    /** Caméra frontale uniquement (détection de visage). */
    Camera,
}

/** Une ligne du journal de présence, affichée dans les réglages. */
@Serializable
data class PresenceEntry(
    /** Horodatage mur, en millisecondes. */
    val at: Long,
    val source: PresenceSource,
    /** Précision libre (« 42 cm », « Surveillance démarrée »). */
    val detail: String? = null,
)
