package com.homeglass.app.ui.media

import com.homeglass.app.domain.ha.EntityState
import com.homeglass.app.domain.ha.mediaInfo

/** Domaine des lecteurs média de Home Assistant. */
private const val MEDIA_PLAYER = "media_player"
private const val PLAYING = "playing"

/**
 * États qui ne signalent pas un nouveau morceau : le lecteur jouait déjà, ou il était en
 * pause. Reprendre une lecture ou passer au titre suivant ne rouvre donc pas l'écran.
 */
private val CONTINUING_STATES = setOf("playing", "paused", "buffering")

/** États pendant lesquels le lecteur plein écran reste affiché. */
private val VISIBLE_STATES = setOf("playing", "paused", "buffering")

/**
 * Lecteur à afficher en grand parce que la lecture vient de démarrer, sinon `null`.
 *
 * @param previous relevé précédent des états, `null` au tout premier : au lancement de
 *   l'application on prend une référence sans rien ouvrir, même si de la musique tourne.
 */
fun startedPlayback(
    states: Map<String, EntityState>,
    previous: Map<String, EntityState>?,
): String? {
    if (previous == null) return null

    return states.values
        .filter { entity -> entity.domain == MEDIA_PLAYER && entity.state == PLAYING }
        .filter { entity -> previous[entity.entityId]?.state !in CONTINUING_STATES }
        // À plusieurs départs simultanés, celui qui a un titre identifié est le plus utile.
        .sortedWith(
            compareByDescending<EntityState> { entity -> entity.mediaInfo()?.title != null }
                .thenBy { entity -> entity.entityId },
        )
        .firstOrNull()
        ?.entityId
}

/** Vrai tant que le lecteur affiché a une raison de rester à l'écran. */
fun staysOnScreen(entity: EntityState?): Boolean =
    entity != null && entity.state in VISIBLE_STATES
