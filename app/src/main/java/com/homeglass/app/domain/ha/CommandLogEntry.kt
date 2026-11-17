package com.homeglass.app.domain.ha

import kotlinx.serialization.Serializable

/**
 * Trace locale d'une commande envoyée à Home Assistant.
 *
 * Le journal ne contient ni jeton ni adresse : seulement l'heure, le service, l'entité
 * visée et le résultat. Il sert au diagnostic (par exemple pour vérifier qu'une lumière a
 * été éteinte par l'application ou par autre chose) et reste borné côté stockage.
 */
@Serializable
data class CommandLogEntry(
    val at: Long,
    val service: String,
    val target: String? = null,
    val success: Boolean,
) {
    /** Libellé compact pour l'accessibilité et les tests. */
    val summary: String
        get() = buildString {
            append(service)
            target?.let {
                append(' ')
                append(it)
            }
        }
}
