package com.homeglass.app.data.ha

import com.homeglass.app.domain.ha.BaseUrlChooser

/** HomeGlass Server repéré sur le réseau. */
data class DiscoveredServer(
    val baseUrl: String,
    val version: String,
    val haUuid: String?,
)

/**
 * Adresses à tester pour trouver l'add-on.
 *
 * L'add-on tourne sur la même machine que Home Assistant et publie son API sur le port 8765
 * (`api_port` de `config.yaml`, mappé sur l'hôte). La découverte mDNS de Home Assistant donne
 * déjà l'hôte et le schéma : il suffit d'en déduire l'adresse du serveur.
 *
 * Logique pure (aucun appel réseau) pour rester testable.
 */
object AddonCandidates {

    /** Port publié par défaut par l'add-on (`config.yaml` → `ports`). */
    const val DEFAULT_PORT = 8765

    fun forInstance(instance: DiscoveredInstance): String? {
        val reference = instance.internalUrl ?: instance.baseUrl
        // `URI.getHost()` renvoie déjà les crochets autour d'une adresse IPv6 : on les retire
        // avant de les remettre, sinon l'URL obtenue serait « [[fd00::1]] ».
        val host = BaseUrlChooser.host(reference)?.removeSurrounding("[", "]") ?: return null
        val formatted = if (host.contains(':')) "[$host]" else host
        val scheme = if (reference.startsWith("https://", ignoreCase = true)) "https" else "http"
        return "$scheme://$formatted:$DEFAULT_PORT"
    }
}
